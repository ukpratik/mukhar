package io.github.ukpratik.mukhar.app.feature.words

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewActions
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewContent
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewEvent
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewUiState
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_BACK
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_CHECKS_DONE
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_DONE
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_DONE_CREDIT
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_FRONT
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_OFFLINE
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_QUOTA
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_SAY
import io.github.ukpratik.mukhar.app.feature.words.review.TAG_SHOW
import io.github.ukpratik.mukhar.domain.fsrs.Rating
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S13 (every state) and S15 (fix, saved line, done) on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ReviewContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<ReviewEvent>()
    private var said = 0
    private var closed = 0

    private fun show(state: ReviewUiState) {
        compose.setContent {
            MukharTheme { ReviewContent(state, ReviewActions(onEvent = { events += it }, onClose = { closed++ }, onSay = { said++ })) }
        }
    }

    /** Eyebrows render in capitals, so text is matched ignoring case; some text sits outside the scrolling column. */
    private fun text(t: String) {
        compose.onAllNodes(hasText(t, substring = true, ignoreCase = true), useUnmergedTree = true).onFirst().assertExists()
    }

    @Test
    fun cardFrontRecallsFirstThenFlips() {
        show(WordsFixtures.front)
        compose.onNodeWithText("Practise").assertIsDisplayed()
        compose.onNodeWithContentDescription("Card 4 of 12").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close review. Progress is saved").assertIsDisplayed()
        compose.onNodeWithContentDescription("articulate, verb · power. Tap to show the meaning").assertIsDisplayed()
        compose.onAllNodesWithTag("review_grades").assertCountEquals(0) // no grading before recall
        compose.onNodeWithTag(TAG_SHOW).performClick()
        compose.onNodeWithTag(TAG_FRONT).performClick()
        assertThat(events).containsExactly(ReviewEvent.Flip, ReviewEvent.Flip)
    }

    @Test
    fun cardBackShowsMeaningGlossExampleAndFourGradesWithIntervals() {
        show(WordsFixtures.back)
        compose.onNodeWithTag(TAG_BACK).assertIsDisplayed()
        text("To say an idea clearly so others understand it.")
        text("साफ़-साफ़ शब्दों में कहना")
        text("“She articulated the risk in one line.”")
        text("How well did you remember?")
        compose.onNodeWithContentDescription("Again, next in under a minute").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hard, next in 2 days").assertIsDisplayed()
        compose.onNodeWithContentDescription("Good, next in 4 days").assertIsDisplayed()
        compose.onNodeWithContentDescription("Easy, next in 9 days").performClick()
        compose.onNodeWithTag(TAG_SAY).performScrollTo().performClick()
        assertThat(events).containsExactly(ReviewEvent.Grade(Rating.EASY))
        assertThat(said).isEqualTo(1)
    }

    @Test
    fun sentenceNicelyUsedSuggestsGoodWithoutPickingIt() {
        show(WordsFixtures.ok)
        text("Your sentence with")
        text("In the review, I articulated why the launch slipped by a week.")
        text("Nicely used")
        compose.onNodeWithTag(TAG_QUOTA).performScrollTo()
        text("3 of 10 free checks used today")
        compose.onNodeWithContentDescription("Good, suggested, next in 4 days").assertIsDisplayed()
        assertThat(events).isEmpty() // suggested is outlined, never auto-picked
    }

    @Test
    fun sentenceAlmostShowsTheCorrectionAndSuggestsHard() {
        show(WordsFixtures.almost)
        text("Almost there")
        text("Try it like this")
        text("tomorrow's")
        text("Say it again")
        compose.onNodeWithContentDescription("Hear the corrected sentence").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hard, suggested, next in 2 days").assertIsDisplayed()
        compose.onNodeWithText("Say it again").performClick()
        assertThat(events).containsExactly(ReviewEvent.RetrySentence)
    }

    @Test
    fun sentenceNotYetIsNeutralWithAnExampleAndSuggestsAgain() {
        show(WordsFixtures.notYet)
        text("Not quite yet")
        text("Here's how it's used")
        text("That client call was pivotal: it decided the whole quarter.")
        text("Try another sentence")
        compose.onNodeWithContentDescription("Again, suggested, next in under a minute").assertIsDisplayed()
    }

    @Test
    fun offlineHidesTheMicAndAsksForASelfGrade() {
        show(WordsFixtures.offline)
        compose.onNodeWithTag(TAG_OFFLINE).assertIsDisplayed()
        text("Sentence check is offline")
        text("Did your sentence sound like the example?")
        compose.onAllNodesWithTag(TAG_SAY).assertCountEquals(0)
        compose.onNodeWithContentDescription("Grade this card yourself").assertIsDisplayed()
    }

    @Test
    fun checksDoneForTodayFallsBackToSelfGradeWithoutAPaywall() {
        show(WordsFixtures.checksDone)
        compose.onNodeWithTag(TAG_CHECKS_DONE).performScrollTo()
        text("Checks are done for today")
        compose.onAllNodesWithTag(TAG_SAY).assertCountEquals(0)
    }

    @Test
    fun talksOffHidesTheCheckBehindOneLine() {
        show(WordsFixtures.talksOff)
        text("Sentence checks need talks on")
        compose.onAllNodesWithTag(TAG_SAY).assertCountEquals(0)
    }

    @Test
    fun mistakeFixFrontShowsOnlyWhatWasSaid() {
        show(WordsFixtures.reviseFix)
        compose.onNodeWithText("Revise").assertIsDisplayed()
        text("Past fix")
        text("You said")
        compose.onAllNodes(hasText("Say instead", ignoreCase = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag(TAG_SHOW).assertIsDisplayed()
    }

    @Test
    fun mistakeFixBackShowsTheFix() {
        show(WordsFixtures.reviseFix.copy(flipped = true))
        text("Say instead")
        text("From your talk on")
        compose.onNodeWithContentDescription("Hear the fixed line").assertIsDisplayed()
    }

    @Test
    fun savedLineIsReadAloudAndGradedForHowNaturalItFelt() {
        show(WordsFixtures.reviseLine)
        text("Saved line · read it aloud")
        text("We launched two weeks ahead of schedule.")
        text("How natural did it feel?")
    }

    @Test
    fun doneCountsForTodayAndOffersToKeepGoing() {
        show(WordsFixtures.done)
        compose.onNodeWithTag(TAG_DONE).assertIsDisplayed()
        text("Revision done. Nicely kept.")
        text("12 cards in 2 min 40 s.")
        compose.onNodeWithTag(TAG_DONE_CREDIT, useUnmergedTree = true).assertExists()
        text("Counts for today · Day 14")
        text("Past fixes")
        text("Saved lines read aloud")
        text("Next 6 cards due tomorrow.")
        compose.onNodeWithText("Keep going: 4 more cards").performScrollTo().performClick()
        compose.onNodeWithText("Back to Today").performScrollTo().performClick()
        assertThat(events).containsExactly(ReviewEvent.KeepGoing)
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun doneWithFewerThanFiveCardsDoesntClaimTheDay() {
        show(WordsFixtures.done.copy(done = WordsFixtures.done.done!!.copy(cards = 3, streakDay = null, more = 0)))
        compose.onAllNodesWithTag(TAG_DONE_CREDIT, useUnmergedTree = true).assertCountEquals(0)
        text("Five or more cards count for the day.")
        compose.onAllNodes(hasText("Keep going", substring = true)).assertCountEquals(0)
    }

    @Test
    fun emptyQueueSaysAllCaughtUp() {
        show(ReviewUiState(loading = false))
        text("Nothing to review right now")
    }
}
