package io.github.ukpratik.mukhar.data.repo

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.TestStores
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.feedback.Mistake
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.fsrs.CardState
import io.github.ukpratik.mukhar.domain.fsrs.Rating
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.revise.CardType
import io.github.ukpratik.mukhar.domain.streak.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Daily words, the harvest cap, FSRS on Room and the revision queue (US-020 to US-022, US-040). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordRepositoryTest {
    private val stores = TestStores()
    private val clock = FixedClock(Instant.parse("2026-11-06T04:30:00Z"), ZoneId.of("Asia/Kolkata")) // 10:00 IST
    private val habits = HabitRepository(stores.db.habitDao(), stores.settings, clock)
    private val repo = testWordRepository(stores.db, clock, stores.settings, habits)
    private val dao = stores.db.wordDao()
    private val today = LocalDate.of(2026, 11, 6)

    @Before
    fun setUp() = runTest {
        stores.settings.setProfile(null, setOf(Lang.EN), setOf(GoalTrack.INTERVIEWS, GoalTrack.PROFESSIONAL))
    }

    @After
    fun tearDown() = stores.close()

    private fun up(word: String) = UpgradedWord(word, "meaning of $word", Register.POWER)

    private suspend fun harvest(word: String, session: String = "s1") =
        repo.harvest(session, Lang.EN, "professional", up(word), "We used $word today.")

    // ---- Daily words ----

    @Test
    fun dailyWordsAreCuratedForTheUsersTracksAndStableAllDay() = runTest {
        val first = repo.ensureDailyWords(today)
        assertThat(first).hasSize(5) // default 5 per language (US-076)
        val words = repo.observeDailyWords(today).first()
        assertThat(words.map { it.id }).containsExactlyElementsIn(first).inOrder()
        assertThat(words.all { it.lang == Lang.EN && it.tracks.any { t -> t in setOf("interviews", "professional") } }).isTrue()
        assertThat(words.all { it.source == WordSource.CURATED && it.pos != null }).isTrue()
        // Same streak day, same settings: the persisted picks come back.
        clock.advanceBy(Duration.ofHours(8))
        assertThat(repo.ensureDailyWords(today)).isEqualTo(first)
        // Each daily word entered the Word Gym with a word → meaning card due now.
        assertThat(dao.cardsForWord(first[0]).map { it.type }).containsExactly("word_meaning", "meaning_word")
    }

    @Test
    fun talkWordsComeFirstAndTheCountFollowsTheSetting() = runTest {
        stores.settings.setDailyNewWords(Lang.EN, 6)
        harvest("pivotal")
        harvest("undocumented")
        val picked = repo.ensureDailyWords(today)
        assertThat(picked).hasSize(6)
        assertThat(picked.take(2)).containsExactly("en:pivotal", "en:undocumented").inOrder()
        // Tomorrow they aren't picked again; new curated words are.
        val tomorrow = repo.ensureDailyWords(today.plusDays(1))
        assertThat(tomorrow).containsNoneIn(picked)
    }

    @Test
    fun eachLanguageGetsItsOwnCount() = runTest {
        stores.settings.setProfile(null, setOf(Lang.EN, Lang.HI), setOf(GoalTrack.INTERVIEWS, GoalTrack.EVERYDAY))
        stores.settings.setDailyNewWords(Lang.EN, 10)
        stores.settings.setDailyNewWords(Lang.HI, 5)
        val words = repo.observeDailyWords(today).first()
        assertThat(words.count { it.lang == Lang.EN }).isEqualTo(10)
        assertThat(words.count { it.lang == Lang.HI }).isEqualTo(5)
    }

    @Test
    fun raisingTheCountMidDayTopsUpAtOnce() = runTest {
        val first = repo.ensureDailyWords(today) // 5
        stores.settings.setDailyNewWords(Lang.EN, 8)
        val after = repo.ensureDailyWords(today)
        assertThat(after).hasSize(8)
        assertThat(after.take(5)).isEqualTo(first)
        // The observed list follows the setting without a new day (B-rc1-01).
        stores.settings.setDailyNewWords(Lang.EN, 9)
        assertThat(repo.observeDailyWords(today).first { it.size == 9 }.map { it.id }.take(8)).isEqualTo(after)
    }

    @Test
    fun loweringTheCountKeepsWordsAlreadyOpenedOrGraded() = runTest {
        stores.settings.setDailyNewWords(Lang.EN, 8)
        val picked = repo.ensureDailyWords(today)
        // Opened the last one, graded the 7th.
        repo.markOpened(picked[7])
        repo.grade("wm:${picked[6]}", Rating.GOOD, null)
        stores.settings.setDailyNewWords(Lang.EN, 5)
        val after = repo.ensureDailyWords(today)
        assertThat(after).containsExactlyElementsIn(picked.take(3) + picked.drop(6)).inOrder()
        // Removed unseen curated words leave the Word Gym (and can be picked another day).
        assertThat(dao.word(picked[5])).isNull()
        assertThat(dao.cardsForWord(picked[5])).isEmpty()
        assertThat(dao.word(picked[7])).isNotNull()
    }

    @Test
    fun whenTheListRunsOutWordsComeFromOtherTracksThenTheLanguageIsMarkedUsedUp() = runTest {
        val tiny = """{"version":1,"lang":"en","words":[""" + (1..7).joinToString(",") { i ->
            val track = if (i <= 2) "interviews" else "everyday"
            """{"id":"en:w$i","lemma":"w$i","pos":"noun","meaning":"m$i","gloss":"g","example":"e","tracks":["$track"],"register":"casual","difficulty":"easy"}"""
        } + "]}"
        val hiEmpty = """{"version":1,"lang":"hi","words":[]}"""
        val small = testWordRepository(
            stores.db, clock, stores.settings, habits,
            io.github.ukpratik.mukhar.data.content.WordCatalog({ l -> if (l == Lang.EN) tiny else hiEmpty }, kotlinx.serialization.json.Json { ignoreUnknownKeys = true }),
        )
        val day1 = small.ensureDailyWords(today)
        // Only 2 interview words: the rest come from any track.
        assertThat(day1).hasSize(5)
        assertThat(day1).containsAtLeast("en:w1", "en:w2")
        assertThat(small.exhaustedLangs.value).isEmpty()
        val day2 = small.ensureDailyWords(today.plusDays(1))
        assertThat(day2).hasSize(2) // only 2 left
        assertThat(small.exhaustedLangs.value).containsExactly(Lang.EN)
        // Next day: nothing new, no crash; the streak isn't touched by daily words at all.
        assertThat(small.ensureDailyWords(today.plusDays(2))).isEmpty()
    }

    // ---- History and extra practice (US-077) ----

    @Test
    fun historyGroupsWordsByDayPerLanguageEvenBeforeGrading() = runTest {
        val yesterday = repo.ensureDailyWords(today.minusDays(1))
        val todays = repo.ensureDailyWords(today)
        harvest("pivotal")
        val history = repo.observeHistory(Lang.EN).first()
        assertThat(history.map { it.day }).containsExactly(today, today.minusDays(1)).inOrder()
        assertThat(history[0].words.map { it.id }).containsExactlyElementsIn(todays + "en:pivotal").inOrder()
        assertThat(history[1].words.map { it.id }).containsExactlyElementsIn(yesterday).inOrder()
        assertThat(history[0].words.all { it.status == WordStatus.NEW }).isTrue()
        assertThat(repo.observeHistory(Lang.HI).first()).isEmpty()
    }

    @Test
    fun extraPracticeReviewsTheDaysWordsAndGradesAreNormalFsrsReviews() = runTest {
        val todays = repo.ensureDailyWords(today)
        val first = repo.queue(ReviewMode.GYM).first { it.type == CardType.WORD_MEANING }
        repo.grade(first.id, Rating.EASY, null) // now not due for days
        assertThat(repo.queue(ReviewMode.GYM).map { it.id }).doesNotContain(first.id)
        val extra = repo.queueExtra(Lang.EN, today, today)
        // All of today's word → meaning cards, due or not; tomorrow's meaning → word cards aren't released yet.
        assertThat(extra.map { it.id }).containsAtLeastElementsIn(todays.map { "wm:$it" })
        assertThat(extra.none { it.type == CardType.MEANING_WORD }).isTrue()
        val before = dao.card(first.id)!!
        val expected = io.github.ukpratik.mukhar.domain.fsrs.Fsrs(enableFuzzing = false)
            .review(WordRepository.run { before.toDomain() }, Rating.GOOD, clock.now(), null).first
        repo.grade(first.id, Rating.GOOD, null)
        assertThat(dao.card(first.id)!!.due).isEqualTo(expected.due.toEpochMilli())
        // An empty range is an empty session.
        assertThat(repo.queueExtra(Lang.EN, today.minusDays(9), today.minusDays(8))).isEmpty()
    }

    @Test
    fun gymDueAndQueueFilterByLanguage() = runTest {
        stores.settings.setProfile(null, setOf(Lang.EN, Lang.HI), setOf(GoalTrack.EVERYDAY))
        repo.ensureDailyWords(today)
        assertThat(repo.observeGymDue(Lang.HI).first()).isEqualTo(5)
        assertThat(repo.queue(ReviewMode.GYM, lang = Lang.HI).all { it.lang == Lang.HI }).isTrue()
        assertThat(repo.queue(ReviewMode.GYM, lang = Lang.EN).all { it.lang == Lang.EN }).isTrue()
        // Ten new words today (5 + 5) all fit in today's new-card budget.
        assertThat(repo.queue(ReviewMode.GYM).size).isEqualTo(10)
    }

    @Test
    fun wordsToTryPreferDueAndLearningWords() = runTest {
        harvest("pivotal")
        harvest("concise")
        val c = repo.queue(ReviewMode.GYM).first { it.word!!.lemma == "concise" }
        repo.grade(c.id, Rating.AGAIN, null) // learning, due again in minutes
        assertThat(repo.wordsToTry(Lang.EN, 2).first()).isEqualTo("concise")
        assertThat(repo.wordsToTry(Lang.HI)).isEmpty()
    }

    // ---- Harvest (US-021) ----

    @Test
    fun harvestIsDeduplicatedAndCappedAtFiveADay() = runTest {
        val results = listOf("pivotal", "articulate", "undocumented", "miscalculating", "ahead of schedule", "streamline", "concise")
            .map { harvest(it) }
        assertThat(results.take(5).all { it == HarvestResult.Added(today, carriedOver = false) }).isTrue()
        assertThat(results.drop(5)).containsExactly(
            HarvestResult.Added(today.plusDays(1), carriedOver = true),
            HarvestResult.Added(today.plusDays(1), carriedOver = true),
        )
        assertThat(harvest("Pivotal", "s2")).isEqualTo(HarvestResult.AlreadyInGym)

        val bank = repo.observeBank().first()
        assertThat(bank).hasSize(7)
        val waiting = bank.filter { it.status == WordStatus.SAVED_FOR_LATER }
        assertThat(waiting.map { it.lemma }).containsExactly("streamline", "concise")
        assertThat(waiting.all { it.releaseDay == today.plusDays(1) }).isTrue()

        // Today's Word Gym has only the 5 released words; the carried-over ones join tomorrow.
        assertThat(repo.queue(ReviewMode.GYM).map { it.word!!.lemma }).containsNoneOf("streamline", "concise")
        clock.advanceBy(Duration.ofDays(1))
        assertThat(repo.queue(ReviewMode.GYM).map { it.word!!.lemma }).containsAtLeast("streamline", "concise")
    }

    @Test
    fun unharvestRemovesOnlyThisTalksWord() = runTest {
        harvest("pivotal", "s1")
        repo.unharvest("s2", Lang.EN, "pivotal")
        assertThat(dao.word("en:pivotal")).isNotNull()
        repo.unharvest("s1", Lang.EN, "pivotal")
        assertThat(dao.word("en:pivotal")).isNull()
        assertThat(dao.cardsForWord("en:pivotal")).isEmpty()
    }

    // ---- FSRS with Room (US-022) ----

    @Test
    fun gradingSchedulesWithFsrsAndWritesTheReviewLog() = runTest {
        harvest("pivotal")
        val card = repo.queue(ReviewMode.GYM).single { it.type == CardType.WORD_MEANING }
        val preview = repo.preview(card.id)
        assertThat(preview.keys).containsExactlyElementsIn(Rating.entries)
        assertThat(preview[Rating.AGAIN]!!).isLessThan(preview[Rating.EASY]!!)

        val after = repo.grade(card.id, Rating.GOOD, 4_000)!!
        val row = dao.card(card.id)!!
        assertThat(row.state).isEqualTo(after.state.value)
        assertThat(row.reps).isEqualTo(1)
        assertThat(row.due).isEqualTo(after.due.toEpochMilli())
        assertThat(row.due).isEqualTo(clock.now().plus(preview[Rating.GOOD]!!).toEpochMilli())
        val log = dao.reviewLogs(card.id).single()
        assertThat(log.rating).isEqualTo(3)
        assertThat(log.stateBefore).isEqualTo(CardState.NEW.value)
        assertThat(log.durationMs).isEqualTo(4_000)

        // Easy twice moves it to review with a days-long interval; the status follows.
        repo.grade(card.id, Rating.EASY, null)
        clock.advanceBy(Duration.ofDays(30))
        repo.grade(card.id, Rating.EASY, null)
        assertThat(dao.card(card.id)!!.state).isEqualTo(CardState.REVIEW.value)
        assertThat(repo.observeWord("en:pivotal").first()!!.status).isEqualTo(WordStatus.KNOWN)
    }

    @Test
    fun removeFromGymSuspendsAndUndoRestores() = runTest {
        harvest("pivotal")
        repo.setInGym("en:pivotal", false)
        assertThat(repo.observeBank().first()).isEmpty()
        assertThat(repo.queue(ReviewMode.GYM)).isEmpty()
        repo.setInGym("en:pivotal", true)
        assertThat(repo.observeBank().first().map { it.id }).containsExactly("en:pivotal")
    }

    // ---- QueueBuilder via Room (US-040) ----

    @Test
    fun revisionMixesTypesCapsAtFifteenAndGymTakesWordsOnly() = runTest {
        listOf("pivotal", "articulate", "undocumented", "concise", "streamline").forEach { harvest(it) }
        repo.harvestMistakes("s1", Lang.EN, (1..6).map { Mistake(0, 1, "discuss about $it", "discuss $it") })
        repo.harvestMistakes("s1", Lang.EN, listOf(Mistake(0, 1, "same", "same"))) // no-op fix is skipped
        val now = clock.now().toEpochMilli()
        (1..8).forEach { i ->
            dao.insertCard(WordRepository.newCard("line:s1:$i", CardType.SAVED_LINE, null, "s1", "old $i", "better $i", Lang.EN, now, now))
        }
        val revise = repo.queue(ReviewMode.REVISE)
        assertThat(revise.size).isAtMost(15)
        assertThat(revise.map { it.type }.toSet()).containsExactly(CardType.WORD_MEANING, CardType.MISTAKE_FIX, CardType.SAVED_LINE)
        // Interleaved while more than one type is left (no 3 of a kind in a row).
        revise.take(6).map { it.type }.windowed(3).forEach { assertThat(it.toSet().size).isGreaterThan(1) }
        assertThat(revise.filter { it.type == CardType.MISTAKE_FIX }.map { it.front }).doesNotContain("same")

        val gym = repo.queue(ReviewMode.GYM)
        assertThat(gym.map { it.type }.toSet()).containsExactly(CardType.WORD_MEANING)
        assertThat(gym.all { it.word != null }).isTrue()
    }

    @Test
    fun meaningToWordCardsArriveTheNextDay() = runTest {
        harvest("pivotal")
        assertThat(repo.queue(ReviewMode.GYM).map { it.type }).containsExactly(CardType.WORD_MEANING)
        clock.advanceBy(Duration.ofDays(1))
        assertThat(repo.queue(ReviewMode.GYM).map { it.type }).contains(CardType.MEANING_WORD)
    }

    @Test
    fun focusQueueReviewsOneWordNow() = runTest {
        harvest("pivotal")
        val c = repo.queue(ReviewMode.GYM).single()
        repo.grade(c.id, Rating.EASY, null)
        assertThat(repo.queue(ReviewMode.GYM)).isEmpty()
        assertThat(repo.queue(ReviewMode.GYM, focusWordId = "en:pivotal").map { it.id }).containsExactly(c.id)
    }

    @Test
    fun aReviewOfFiveCardsCreditsTheStreakDay() = runTest {
        assertThat(repo.creditReview(4).creditedDay).isNull()
        val r = repo.creditReview(5)
        assertThat(r.creditedDay).isEqualTo(today)
        assertThat(habits.snapshot().todaySource).isEqualTo(Source.REVIEW)
    }
}
