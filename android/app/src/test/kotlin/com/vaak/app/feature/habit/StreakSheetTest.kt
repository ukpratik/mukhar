package com.vaak.app.feature.habit

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.vaak.app.feature.today.TodayFixtures
import com.vaak.app.feature.today.TodayStateMapper
import com.vaak.data.prefs.HabitMarks
import com.vaak.data.repo.HabitSnapshot
import com.vaak.domain.streak.HabitState
import com.vaak.domain.streak.Source
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** S20 copy in each state: freezes, freeze used, repair, fresh start. Never shaming. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StreakSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val friday = TodayFixtures.friday
    private var goal = 0
    private var talk = 0

    private fun show(h: HabitSnapshot, talksEnabled: Boolean = true, words: Int = 128) {
        val card = TodayStateMapper.streakCard(h, talksEnabled, queued = false)
        compose.setContent {
            VaakTheme {
                StreakSheetContent(h, card, talksEnabled, words, onGoal = { goal = it }, onTalk = { talk++ }, onRevise = {}, onDismiss = {})
            }
        }
    }

    private fun exists(text: String) = compose.onNodeWithText(text).performScrollTo()

    private fun noShaming() {
        for (banned in listOf("lost", "broken", "failed")) compose.onAllNodes(hasText(banned, substring = true, ignoreCase = true)).assertCountEquals(0)
    }

    @Test
    fun defaultShowsStreakFreezesAndGoal() {
        val days = TodayFixtures.days(friday.minusDays(12), friday.minusDays(1)) + (LocalDate.parse("2026-10-01") to Source.TALK)
        val h = TodayFixtures.snapshot(days, friday, HabitState(freezesHeld = 2, lastFreezeAwardStreak = 14))
        show(h)
        exists("12-day streak")
        exists("Your best: 12 days")
        exists("Freezes")
        exists("2 of 2 held. A missed day uses one for you.")
        exists("Weekly goal")
        exists("Meeting it counts as much as the streak.")
        exists("A day counts with 30 s of speaking or 5 review cards, before 3 am. You earn a freeze every 7 days.")
        exists("Do today's talk").performClick()
        assertThat(talk).isEqualTo(1)
        exists("3 days").performClick()
        assertThat(goal).isEqualTo(3)
        noShaming()
    }

    @Test
    fun freezeUsedIsToldKindly() {
        val days = TodayFixtures.days(friday.minusDays(15), friday.minusDays(3)) + (friday.minusDays(2) to Source.FREEZE) + (friday.minusDays(1) to Source.TALK)
        val h = TodayFixtures.snapshot(days, friday, HabitState(freezesHeld = 1, lastFreezeAwardStreak = 14), HabitMarks(freezeNoticeDay = friday.minusDays(2)))
        show(h)
        exists("A freeze covered Wednesday")
        exists("Busy days happen. Your streak is safe and still growing.")
        exists("Frozen days keep the streak; they don't add to the week.")
        exists("Weekly goal · 3 of 5")
        exists("2 more days this week. Still very doable.")
        noShaming()
    }

    @Test
    fun freezeCoveringYesterday() {
        val days = TodayFixtures.days(friday.minusDays(9), friday.minusDays(2)) + (friday.minusDays(1) to Source.FREEZE)
        val h = TodayFixtures.snapshot(days, friday, HabitState(freezesHeld = 0, lastFreezeAwardStreak = 7), HabitMarks(freezeNoticeDay = friday.minusDays(1)))
        show(h)
        exists("A freeze covered yesterday")
        exists("0 of 2 held. Next one in 5 days.")
    }

    @Test
    fun repairAvailable() {
        // Missed Thursday with no freeze; today Friday, one review done.
        val days = TodayFixtures.days(friday.minusDays(18), friday.minusDays(2)) + (friday to Source.REVIEW)
        val h = TodayFixtures.snapshot(days, friday, HabitState(), HabitMarks(sessionDay = friday, sessionKinds = listOf("review")))
        assertThat(h.repairDay).isEqualTo(friday.minusDays(1))
        show(h)
        exists("Open until 3 am tomorrow night")
        exists("Your 17-day streak can come back")
        exists("Do 2 sessions today and we'll restore it, as if the missed days never happened.")
        exists("REPAIR · 1 OF 2 DONE")
        exists("Revise · 5+ cards")
        exists("A talk · 30 s+")
        exists("Do a talk to finish")
        exists("Not today")
        noShaming()
    }

    @Test
    fun freshStartKeepsTheBestAndTheWords() {
        val days = TodayFixtures.days(friday.minusDays(30), friday.minusDays(13))
        val h = TodayFixtures.snapshot(days, friday)
        assertThat(h.freshStart).isTrue()
        show(h, words = 128)
        exists("Fresh start. Let's go.")
        exists("Life got busy, and that's fine. Today's talk is Day 1 of your next streak.")
        exists("18 days")
        exists("your best streak, kept")
        exists("128 words")
        exists("learned, all still yours")
        exists("Want a gentler restart? 3 days a week is a great rhythm.")
        exists("Start Day 1")
        noShaming()
    }

    @Test
    fun talksOffOffersRevisionInstead() {
        val h = TodayFixtures.snapshot(TodayFixtures.days(friday.minusDays(2), friday.minusDays(1), Source.REVIEW), friday)
        show(h, talksEnabled = false)
        exists("Revise 5 cards")
        compose.onAllNodes(hasText("Do today's talk")).assertCountEquals(0)
    }
}
