package io.github.ukpratik.mukhar.data.repo

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.TestStores
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.fsrs.Rating
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.words.DeckChoice
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

/** US-081 new word deck: the four choices, undo, lists, the daily goal and Later coming back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordDeckRepositoryTest {
    private val stores = TestStores()
    private val clock = FixedClock(Instant.parse("2026-11-12T04:30:00Z"), ZoneId.of("Asia/Kolkata")) // 10:00 IST
    private val repo = testWordRepository(stores.db, clock, stores.settings)
    private val dao = stores.db.wordDao()

    @Before
    fun setUp() = runTest {
        stores.settings.setProfile(null, setOf(Lang.EN), setOf(GoalTrack.INTERVIEWS))
    }

    @After
    fun tearDown() = stores.close()

    private suspend fun deck(lang: Lang? = null) = repo.observeDeck(lang).first()

    @Test
    fun theDeckShowsCuratedWordsWithBothMeaningsAndNothingIsInTheGymYet() = runTest {
        val d = deck()
        assertThat(d.cards).hasSize(3)
        assertThat(d.goal).isEqualTo(5)
        assertThat(d.decidedToday).isEqualTo(0)
        val top = d.cards.first()
        assertThat(top.lang).isEqualTo(Lang.EN)
        assertThat(top.meaning).isNotEmpty()
        assertThat(top.gloss).isNotEmpty() // the Hindi meaning
        assertThat(top.example).isNotEmpty()
        assertThat(dao.allWordIds()).isEmpty()
        assertThat(repo.observeGymDue().first()).isEqualTo(0)
    }

    @Test
    fun doneAndStarGoIntoTheGymAndTheirLists() = runTest {
        val (a, b) = deck().cards
        repo.decide(a.id, DeckChoice.DONE)
        repo.decide(b.id, DeckChoice.STARRED)
        assertThat(dao.cardsForWord(a.id).map { it.type }).containsExactly("word_meaning", "meaning_word")
        assertThat(dao.cardsForWord(b.id)).hasSize(2)
        val d = deck()
        assertThat(d.cards.map { it.id }).containsNoneOf(a.id, b.id)
        assertThat(d.decidedToday).isEqualTo(2)
        val lists = repo.observeDeckLists(null).first()
        assertThat(lists.starred.map { it.id }).containsExactly(b.id)
        assertThat(lists.done.single().day).isEqualTo(LocalDate.of(2026, 11, 12))
        assertThat(lists.done.single().words.map { it.id }).containsExactly(a.id)
        // Unstar moves it to Done, still in the gym.
        repo.setStarred(b.id, false)
        assertThat(repo.observeDeckLists(null).first().done.single().words.map { it.id }).containsExactly(b.id, a.id)
        assertThat(dao.cardsForWord(b.id)).hasSize(2)
    }

    @Test
    fun discardNeverComesBackUntilBroughtBack() = runTest {
        val top = deck().cards.first()
        repo.decide(top.id, DeckChoice.DISCARDED)
        assertThat(dao.word(top.id)).isNull()
        clock.advanceBy(Duration.ofDays(30))
        assertThat(repo.observeDeck(null, preview = 1000).first().cards.map { it.id }).doesNotContain(top.id)
        assertThat(repo.observeDeckLists(null).first().discarded.map { it.id }).containsExactly(top.id)
        repo.bringBack(top.id)
        assertThat(repo.observeDeck(null, preview = 1000).first().cards.map { it.id }).contains(top.id)
    }

    @Test
    fun laterComesBackAfterThreeDaysAtTheFront() = runTest {
        val top = deck().cards.first()
        repo.decide(top.id, DeckChoice.LATER)
        assertThat(dao.word(top.id)).isNull()
        assertThat(deck().cards.map { it.id }).doesNotContain(top.id)
        clock.advanceBy(Duration.ofDays(3))
        val back = deck().cards.first()
        assertThat(back.id).isEqualTo(top.id)
        assertThat(back.comingBack).isTrue()
    }

    @Test
    fun undoPutsTheCardBackAndTakesTheWordOutOfTheGym() = runTest {
        val top = deck().cards.first()
        val undo = repo.decide(top.id, DeckChoice.DONE)!!
        repo.undo(undo)
        assertThat(deck().cards.first().id).isEqualTo(top.id)
        assertThat(dao.word(top.id)).isNull()
        assertThat(dao.cardsForWord(top.id)).isEmpty()
        // Undoing a Later that came back restores the Later row.
        val later = repo.decide(top.id, DeckChoice.LATER)!!
        clock.advanceBy(Duration.ofDays(3))
        val again = repo.decide(top.id, DeckChoice.STARRED)!!
        assertThat(again.previous?.choice).isEqualTo("later")
        repo.undo(again)
        assertThat(deck().cards.first().id).isEqualTo(top.id)
        assertThat(later.previous).isNull()
    }

    @Test
    fun undoKeepsAWordAlreadyGraded() = runTest {
        val top = deck().cards.first()
        val undo = repo.decide(top.id, DeckChoice.DONE)!!
        repo.grade("wm:${top.id}", Rating.GOOD, null)
        repo.undo(undo)
        assertThat(dao.word(top.id)).isNotNull()
    }

    @Test
    fun theGoalIsTheDailyCountAndKeepGoingIsRemembered() = runTest {
        stores.settings.setDailyNewWords(Lang.EN, 5)
        repeat(5) { repo.decide(deck().cards.first().id, DeckChoice.DONE) }
        val d = deck()
        assertThat(d.goalMet).isTrue()
        assertThat(d.keepGoing).isFalse()
        repo.keepGoing()
        assertThat(deck().keepGoing).isTrue()
        // A new streak day starts the count again.
        clock.advanceBy(Duration.ofDays(1))
        assertThat(deck().decidedToday).isEqualTo(0)
        assertThat(deck().keepGoing).isFalse()
    }

    @Test
    fun wordsFromTalksNeverShowInTheDeck() = runTest {
        val top = deck().cards.first()
        repo.harvest("s1", Lang.EN, "interviews", UpgradedWord(top.lemma, top.meaning, Register.CASUAL), "x")
        assertThat(repo.observeDeck(null, preview = 1000).first().cards.map { it.id }).doesNotContain(top.id)
    }

    @Test
    fun withTwoLanguagesTheDeckAlternatesAndTabsFilter() = runTest {
        stores.settings.setProfile(null, setOf(Lang.EN, Lang.HI), setOf(GoalTrack.EVERYDAY))
        val all = repo.observeDeck(null, preview = 4).first()
        assertThat(all.cards.map { it.lang }.toSet()).containsExactly(Lang.EN, Lang.HI)
        assertThat(all.goal).isEqualTo(10)
        val hi = deck(Lang.HI)
        assertThat(hi.cards.all { it.lang == Lang.HI }).isTrue()
        assertThat(hi.goal).isEqualTo(5)
        assertThat(hi.cards.first().translit).isNotNull()
    }

    @Test
    fun doneAndStarredCanBeRevisedFromTheirLists() = runTest {
        val (a, b) = deck().cards
        repo.decide(a.id, DeckChoice.DONE)
        repo.decide(b.id, DeckChoice.STARRED)
        val today = LocalDate.of(2026, 11, 12)
        assertThat(repo.queueDeck(null, starred = false, today, today).map { it.word?.id }.toSet()).containsExactly(a.id)
        assertThat(repo.queueDeck(null, starred = true, null, null).map { it.word?.id }.toSet()).containsExactly(b.id)
    }
}
