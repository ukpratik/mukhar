package com.vaak.app.feature.words

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.vaak.app.feature.words.review.ReviewActions
import com.vaak.app.feature.words.review.ReviewContent
import com.vaak.app.feature.words.review.ReviewEvent
import com.vaak.app.feature.words.review.ReviewUiState
import com.vaak.app.feature.words.review.TAG_DONE_TALLY
import com.vaak.app.feature.words.review.TAG_FRONT
import com.vaak.data.repo.ReviewMode
import com.vaak.domain.fsrs.Rating
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NFR-008: TalkBack semantics of Word Gym, Revise and the word bank (click actions, counts, selection). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WordsA11yTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<ReviewEvent>()
    private val bankEvents = mutableListOf<WordBankEvent>()
    private var gym = 0

    private fun review(state: ReviewUiState) {
        compose.setContent { VaakTheme { ReviewContent(state, ReviewActions(onEvent = { events += it })) } }
    }

    private fun bank(state: WordBankUiState) {
        compose.setContent { VaakTheme { WordBankContent(state, WordBankActions(onEvent = { bankEvents += it }, onOpenGym = { gym++ })) } }
    }

    @Test
    fun gradeButtonsKeepTheirClickActionUnderTheOneSentenceLabel() {
        review(WordsFixtures.back)
        Rating.entries.forEach { r ->
            compose.onNodeWithTag("grade_${r.name.lowercase()}").assert(hasClickAction())
        }
        compose.onNodeWithTag("grade_hard").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(ReviewEvent.Grade(Rating.HARD))
    }

    @Test
    fun suggestedGradeIsSaidInWordsNotOnlyColour() {
        review(WordsFixtures.ok)
        compose.onNodeWithContentDescription("Good, suggested, next in 4 days").assert(hasClickAction())
    }

    @Test
    fun cardFrontFlipsByActionAndOffersTheSpeakerAsACustomAction() {
        review(WordsFixtures.front)
        val front = compose.onNodeWithTag(TAG_FRONT).assert(hasClickAction())
        val custom = front.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertThat(custom.map { it.label }).containsExactly("Hear articulate, Indian English voice")
        custom.single().action()
        front.performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(ReviewEvent.Speak("articulate", Lang.EN), ReviewEvent.Flip).inOrder()
    }

    @Test
    fun doneTallyReadsTheCounts() {
        review(WordsFixtures.done)
        compose.onNodeWithTag(TAG_DONE_TALLY).performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("What you revised: 7 words, 3 fixes, 2 saved lines")))
    }

    @Test
    fun gymTallyReadsTheWordCount() {
        review(WordsFixtures.done.copy(mode = ReviewMode.GYM, done = WordsFixtures.done.done!!.copy(words = 1)))
        compose.onNodeWithTag(TAG_DONE_TALLY).performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("What you revised: 1 word")))
    }

    @Test
    fun gymRowIsOneClickableSentence() {
        bank(WordBankUiState(loading = false, words = listOf(WordsFixtures.pivotal), gymDue = 8))
        compose.onNodeWithTag(TAG_GYM_ROW).assert(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        assertThat(gym).isEqualTo(1)
    }

    @Test
    fun filterChipsExposeSelectionAndTheCountIsLive() {
        bank(WordBankUiState(loading = false, words = listOf(WordsFixtures.pivotal, WordsFixtures.concise), trackChips = listOf("interviews"), tracks = setOf("interviews")))
        compose.onNode(hasText("All") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("From your talks") and hasClickAction()).assertIsNotSelected()
        compose.onNode(hasText("Interviews") and hasClickAction()).assertIsSelected()
        compose.onNodeWithTag(TAG_WORDS_COUNT).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }
}
