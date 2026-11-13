package io.github.ukpratik.mukhar.app.feature.words.deck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.words.DeckFixtures
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.words.DeckChoice
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-081 deck: the card, the four actions, the goal and empty screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DeckContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val decided = mutableListOf<Pair<String, DeckChoice>>()
    private val spoken = mutableListOf<String>()
    private var keptGoing = false

    private fun show(state: DeckUiState) {
        compose.mainClock.autoAdvance = true
        compose.setContent {
            MukharTheme {
                DeckContent(
                    state,
                    DeckActions(onDecide = { c, ch -> decided += c.id to ch }, onSpeak = { t, _ -> spoken += t }, onKeepGoing = { keptGoing = true }),
                )
            }
        }
    }

    @Test
    fun theCardShowsTheWordBothMeaningsTheExampleAndTheVoice() {
        show(DeckUiState(false, DeckFixtures.state(), voices = mapOf(Lang.EN to true)))
        compose.onNodeWithText("2 of 5 today").assertIsDisplayed()
        compose.onNodeWithText("lucid").assertIsDisplayed()
        compose.onNodeWithText("Clear and easy to understand.").assertIsDisplayed()
        compose.onNodeWithText("स्पष्ट").assertIsDisplayed()
        compose.onNode(hasText("She gave a lucid explanation", substring = true)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Hear lucid, Indian English voice").performClick()
        compose.onNodeWithContentDescription("Hear the example").performClick()
        assertThat(spoken).containsExactly("lucid", "She gave a lucid explanation of the plan.").inOrder()
    }

    @Test
    fun eachButtonDecidesTheTopCard() {
        var round by mutableIntStateOf(0)
        compose.setContent {
            MukharTheme {
                // A fresh deck each round, as after a real decision.
                key(round) { DeckContent(DeckUiState(false, DeckFixtures.state()), DeckActions(onDecide = { c, ch -> decided += c.id to ch })) }
            }
        }
        val buttons = listOf(TAG_DECK_DONE, TAG_DECK_STAR, TAG_DECK_LATER, TAG_DECK_DISCARD)
        buttons.forEach { tag ->
            compose.onNodeWithTag(tag).performClick()
            compose.waitForIdle()
            round++
            compose.waitForIdle()
        }
        assertThat(decided.map { it.second }).containsExactly(DeckChoice.DONE, DeckChoice.STARRED, DeckChoice.LATER, DeckChoice.DISCARDED).inOrder()
    }

    @Test
    fun aDoubleTapDecidesOnlyOnce() {
        show(DeckUiState(false, DeckFixtures.state()))
        compose.onNodeWithTag(TAG_DECK_DONE).performClick()
        compose.onNodeWithTag(TAG_DECK_DONE).performClick()
        compose.waitForIdle()
        assertThat(decided).containsExactly("en:lucid" to DeckChoice.DONE)
    }

    @Test
    fun theButtonsSayWhatTheyDoAndTheCardHasTalkBackActions() {
        show(DeckUiState(false, DeckFixtures.state()))
        compose.onNodeWithContentDescription("Got it: mark lucid as learnt. It comes back in Revise").assertExists()
        compose.onNodeWithContentDescription("Not for me: never show lucid again").assertExists()
        val card = compose.onNodeWithTag(TAG_DECK_CARD).fetchSemanticsNode()
        val labels = card.config[SemanticsActions.CustomActions].map { it.label }
        assertThat(labels).containsExactly("Got it", "Star", "Later", "Not for me").inOrder()
    }

    @Test
    fun hindiCardsShowTheTransliterationAndTheEnglishMeaning() {
        show(DeckUiState(false, DeckFixtures.state(cards = listOf(DeckFixtures.sateek))))
        compose.onNode(hasText("sateek", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Precise, exact.").assertIsDisplayed()
        compose.onNode(hasText("Her answer was precise.", substring = true)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theGoalScreenOffersKeepGoing() {
        show(DeckUiState(false, DeckFixtures.state(decided = 5, goal = 5)))
        compose.onNodeWithText("That's today's words").assertIsDisplayed()
        compose.onNodeWithTag(TAG_DECK_KEEP_GOING).performClick()
        assertThat(keptGoing).isTrue()
    }

    @Test
    fun afterKeepGoingTheCardsContinue() {
        show(DeckUiState(false, DeckFixtures.state(decided = 6, goal = 5, keepGoing = true)))
        compose.onNodeWithText("6 today · goal met").assertIsDisplayed()
        compose.onNodeWithTag(TAG_DECK_CARD).assertIsDisplayed()
    }

    @Test
    fun anEmptyDeckSaysSo() {
        show(DeckUiState(false, DeckFixtures.state(cards = emptyList(), remaining = 0)))
        compose.onNodeWithTag(TAG_DECK_EMPTY).assertIsDisplayed()
    }

    @Test
    fun theHintShowsOnlyForNewUsersAndTodayCountsShows() {
        show(DeckUiState(false, DeckFixtures.state().copy(showHint = false, practisedToday = 5)))
        compose.onAllNodesWithTag(TAG_DECK_HINT).assertCountEquals(0)
        compose.onNodeWithTag(TAG_TODAY_COUNTS, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("2 of today's 5 new words. Today counts").assertExists()
    }

    @Test
    fun theToastSitsInItsOwnSlotAboveTheButtons() {
        compose.setContent {
            MukharTheme {
                val host = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
                androidx.compose.runtime.LaunchedEffect(Unit) { host.showSnackbar("Got it · lucid", "Undo", duration = androidx.compose.material3.SnackbarDuration.Indefinite) }
                DeckContent(DeckUiState(false, DeckFixtures.state()), DeckActions(), snackbar = host)
            }
        }
        compose.waitForIdle()
        val toast = compose.onNodeWithText("Got it · lucid").fetchSemanticsNode().boundsInRoot
        for (tag in listOf(TAG_DECK_DONE, TAG_DECK_STAR, TAG_DECK_LATER, TAG_DECK_DISCARD)) {
            val button = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertThat(toast.bottom).isAtMost(button.top)
        }
        val card = compose.onNodeWithTag(TAG_DECK_CARD).fetchSemanticsNode().boundsInRoot
        assertThat(toast.top).isAtLeast(card.bottom)
    }
}
