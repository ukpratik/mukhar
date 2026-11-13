package io.github.ukpratik.mukhar.app.feature.words.revise

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.words.WordsFixtures
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D-055 Words → Revise: recall first, then Knew it / Again; done and empty screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class RevisionContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val answers = mutableListOf<Boolean>()
    private var revealed = 0
    private var learn = 0

    private fun show(state: RevisionUiState) = compose.setContent {
        MukharTheme { RevisionContent(state, RevisionActions(onReveal = { revealed++ }, onAnswer = { answers += it }, onLearn = { learn++ })) }
    }

    private val one = RevisionUiState(loading = false, cards = listOf(WordsFixtures.articulateCard))

    @Test
    fun theMeaningStaysHiddenUntilAsked() {
        show(one)
        compose.onNodeWithText("What does it mean?", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("articulate").assertIsDisplayed()
        compose.onAllNodesWithTag(TAG_REVISION_KNEW).assertCountEquals(0)
        compose.onNodeWithTag(TAG_REVISION_REVEAL).performClick()
        assertThat(revealed).isEqualTo(1)
    }

    @Test
    fun afterTheRevealKnewItAndAgainAnswer() {
        show(one.copy(revealed = true))
        compose.onNodeWithText("To say an idea clearly so others understand it.").assertIsDisplayed()
        compose.onNodeWithTag(TAG_REVISION_KNEW).performClick()
        compose.waitForIdle()
        assertThat(answers).containsExactly(true)
    }

    @Test
    fun againAnswersFalse() {
        show(one.copy(revealed = true))
        compose.onNodeWithTag(TAG_REVISION_AGAIN).performClick()
        compose.waitForIdle()
        assertThat(answers).containsExactly(false)
    }

    @Test
    fun theDoneScreenSummarisesAndOffersNewWords() {
        show(one.copy(index = 1, knew = 1, done = RevisionDone(total = 1, knew = 1, dueTomorrow = 3), countsToday = true))
        compose.onNodeWithText("Revision done").assertIsDisplayed()
        compose.onNodeWithText("You revised 1 word and knew 1.").assertIsDisplayed()
        compose.onNodeWithText("3 words are due tomorrow.").assertIsDisplayed()
        compose.onNodeWithTag(TAG_REVISION_LEARN).performClick()
        assertThat(learn).isEqualTo(1)
    }

    @Test
    fun nothingDueSaysAllCaughtUp() {
        show(RevisionUiState(loading = false, done = RevisionDone(0, 0, 2)))
        compose.onNodeWithTag(TAG_REVISION_EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Nothing to revise right now. 2 words are due tomorrow.").assertIsDisplayed()
    }
}
