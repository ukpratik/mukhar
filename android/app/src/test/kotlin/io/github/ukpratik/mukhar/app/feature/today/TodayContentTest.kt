package io.github.ukpratik.mukhar.app.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S07, every state (Compose on Robolectric). Tappable cards read as one sentence (design §10), so the streak and
 * talk cards are checked through their descriptions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TodayContentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: TodayUiState, actions: TodayActions = TodayActions()) {
        compose.setContent { MukharTheme { TodayContent(state, actions) } }
    }

    private fun exists(text: String) = compose.onNodeWithText(text).performScrollTo()

    private fun described(text: String) = compose.onNodeWithContentDescription(text).performScrollTo()

    private fun absent(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).assertCountEquals(0)

    @Test
    fun firstDay() {
        var talk = 0
        show(TodayFixtures.firstDay, TodayActions(onStartTalk = { talk++ }))
        exists("Good morning,\nRohan")
        described("Day 1. Your warm-up counted. One talk a day keeps it going. Weekly goal 1 of 5 days. Open streak details")
        described("Your first talk, English, Interviews. Introduce yourself as you would at the start of an interview. Start talk")
        compose.onNodeWithContentDescription("Record your 1-minute starting point").performScrollTo()
        compose.onNodeWithContentDescription("Revise. Nothing due yet. Words you add show up here tomorrow.").performScrollTo()
        exists("Today's words")
        exists("New words for your goals show up here each morning.")
        compose.onNodeWithTag(TAG_TALK_CARD).performClick()
        assertThat(talk).isEqualTo(1)
    }

    @Test
    fun midDay() {
        var streakOpened = false
        show(TodayFixtures.midDay, TodayActions(onOpenStreak = { streakOpened = true }))
        exists("Good afternoon,\nRohan")
        // The description and the click action live on the same node, so TalkBack can open the sheet.
        described("12-day streak. One talk today and this week's goal is met. 1 freeze saved. Weekly goal 4 of 5 days. Open streak details")
            .assertHasClickAction()
        compose.onNodeWithTag(TAG_STREAK_CARD).assertHasClickAction()
        compose.onNode(hasContentDescription("Today's talk, English", substring = true)).performScrollTo()
        compose.onNodeWithContentDescription("Revise · 8 due. ~3 min").performScrollTo()
        compose.onAllNodes(hasContentDescription("starting point", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag(TAG_STREAK_CARD).performClick()
        assertThat(streakOpened).isTrue()
    }

    @Test
    fun doneForTodayTeasesTomorrow() {
        show(TodayFixtures.done)
        described("13-day streak. Weekly goal met. That's as big as the streak. Weekly goal met, 5 of 5 days. Open streak details")
        exists("You're done for today.")
        exists("TOMORROW'S TALK · OPENS AT 3 AM")
        compose.onNodeWithContentDescription("Revise · all caught up. Next 6 cards due tomorrow").performScrollTo()
        compose.onAllNodesWithTag(TAG_TALK_CARD).assertCountEquals(0)
    }

    @Test
    fun offline() {
        show(TodayFixtures.offline)
        exists("You're offline")
        exists("You can still talk. We'll analyse it when you're back online.")
        compose.onNode(hasContentDescription("1 talk waiting. Recorded", substring = true)).performScrollTo()
        compose.onNode(hasContentDescription("day counted", substring = true)).performScrollTo()
        compose.onNode(hasContentDescription("Practise again, English", substring = true)).performScrollTo()
        // A shuffled prompt is read out: the talk card is a polite live region.
        compose.onNodeWithTag(TAG_TALK_CARD).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithContentDescription("Revise · 6 due. Works offline · ~2 min").performScrollTo()
    }

    @Test
    fun talksOffShowsTheCalmTurnOnCard() {
        var turnOn = false
        show(TodayFixtures.talksOff, TodayActions(onTurnOnTalks = { turnOn = true }))
        described("Day 3. Revising 5 cards keeps your streak going. Weekly goal 2 of 5 days. Open streak details")
        compose.onNodeWithContentDescription("Revise · 7 due. Works fully on your phone · ~3 min").performScrollTo()
        compose.onAllNodesWithTag(TAG_TALK_CARD).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("starting point", substring = true)).assertCountEquals(0)
        described("Talks are off. Turn on talks: see what leaves your phone first").performClick()
        assertThat(turnOn).isTrue()
    }

    @Test
    fun noShamingCopyInAnyState() {
        var state by mutableStateOf(TodayFixtures.firstDay)
        compose.setContent { MukharTheme { TodayContent(state, TodayActions()) } }
        for (s in listOf(TodayFixtures.firstDay, TodayFixtures.midDay, TodayFixtures.done, TodayFixtures.offline, TodayFixtures.talksOff)) {
            state = s
            compose.waitForIdle()
            for (banned in listOf("lost", "broken", "failed")) {
                absent(banned, substring = true)
                compose.onAllNodes(hasContentDescription(banned, substring = true, ignoreCase = true)).assertCountEquals(0)
            }
        }
    }

    @Test
    fun todaysWordsShowRealDailyWordsWithTheSpeakerOnlyWithAVoice() {
        var opened: String? = null
        val words = listOf(
            io.github.ukpratik.mukhar.app.feature.words.WordsFixtures.articulate,
            io.github.ukpratik.mukhar.app.feature.words.WordsFixtures.concise,
            io.github.ukpratik.mukhar.app.feature.words.WordsFixtures.sateek,
        )
        show(
            TodayFixtures.midDay.copy(words = words, voices = mapOf(io.github.ukpratik.mukhar.domain.model.Lang.EN to true, io.github.ukpratik.mukhar.domain.model.Lang.HI to false)),
            TodayActions(onOpenWord = { opened = it }),
        )
        compose.onAllNodes(hasText("From your talk", substring = true)).assertCountEquals(2) // concise is curated
        compose.onNodeWithContentDescription("Hear articulate, Indian English voice").performScrollTo()
        compose.onNodeWithContentDescription("Hear concise, Indian English voice").performScrollTo()
        compose.onAllNodes(hasContentDescription("Hear सटीक", substring = true)).assertCountEquals(0) // no Hindi voice: hidden
        compose.onNode(hasText("Short and clear", substring = true)).performScrollTo().performClick()
        assertThat(opened).isEqualTo("en:concise")
    }

    @Test
    fun aRecoveredBaselineIsShownAsTheStartingPoint() {
        var opened: String? = null
        show(
            TodayFixtures.firstDay.copy(recovered = io.github.ukpratik.mukhar.data.today.RecoveredTalk("b-1", java.time.Instant.parse("2026-11-02T03:00:00Z"), 42_000, isBaseline = true)),
            TodayActions(onOpenRecovered = { opened = it }),
        )
        compose.onNode(hasContentDescription("We kept your starting point", substring = true)).performScrollTo().performClick()
        assertThat(opened).isEqualTo("b-1")
    }
}
