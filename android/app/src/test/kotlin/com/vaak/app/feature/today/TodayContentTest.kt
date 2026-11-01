package com.vaak.app.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
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
import com.vaak.ui.theme.VaakTheme
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
        compose.setContent { VaakTheme { TodayContent(state, actions) } }
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
        exists("Three new words a day, picked for your goals, will show up here.")
        compose.onNodeWithTag(TAG_TALK_CARD).performClick()
        assertThat(talk).isEqualTo(1)
    }

    @Test
    fun midDay() {
        var streakOpened = false
        show(TodayFixtures.midDay, TodayActions(onOpenStreak = { streakOpened = true }))
        exists("Good afternoon,\nRohan")
        described("12-day streak. One talk today and this week's goal is met. 1 freeze saved. Weekly goal 4 of 5 days. Open streak details")
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
        compose.setContent { VaakTheme { TodayContent(state, TodayActions()) } }
        for (s in listOf(TodayFixtures.firstDay, TodayFixtures.midDay, TodayFixtures.done, TodayFixtures.offline, TodayFixtures.talksOff)) {
            state = s
            compose.waitForIdle()
            for (banned in listOf("lost", "broken", "failed")) {
                absent(banned, substring = true)
                compose.onAllNodes(hasContentDescription(banned, substring = true, ignoreCase = true)).assertCountEquals(0)
            }
        }
    }
}
