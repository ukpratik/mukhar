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
        assertThat(first).hasSize(3) // default 3 (US-020)
        val words = repo.observeDailyWords(today).first()
        assertThat(words.map { it.id }).containsExactlyElementsIn(first).inOrder()
        assertThat(words.all { it.lang == Lang.EN && it.tracks.any { t -> t in setOf("interviews", "professional") } }).isTrue()
        assertThat(words.all { it.source == WordSource.CURATED && it.pos != null }).isTrue()
        // Same streak day, even after the setting changes: the persisted picks come back.
        stores.settings.setDailyNewWords(5)
        clock.advanceBy(Duration.ofHours(8))
        assertThat(repo.ensureDailyWords(today)).isEqualTo(first)
        // Each daily word entered the Word Gym with a word → meaning card due now.
        assertThat(dao.cardsForWord(first[0]).map { it.type }).containsExactly("word_meaning", "meaning_word")
    }

    @Test
    fun talkWordsComeFirstAndTheCountFollowsTheSetting() = runTest {
        stores.settings.setDailyNewWords(4)
        harvest("pivotal")
        harvest("undocumented")
        val picked = repo.ensureDailyWords(today)
        assertThat(picked).hasSize(4)
        assertThat(picked.take(2)).containsExactly("en:pivotal", "en:undocumented").inOrder()
        // Tomorrow they aren't picked again; new curated words are.
        val tomorrow = repo.ensureDailyWords(today.plusDays(1))
        assertThat(tomorrow).containsNoneIn(picked)
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
