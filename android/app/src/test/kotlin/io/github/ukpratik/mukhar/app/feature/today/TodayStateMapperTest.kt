package io.github.ukpratik.mukhar.app.feature.today

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.today.TodayFixtures.consented
import io.github.ukpratik.mukhar.app.feature.today.TodayFixtures.inputs
import io.github.ukpratik.mukhar.app.feature.today.TodayFixtures.monday
import io.github.ukpratik.mukhar.app.feature.today.TodayFixtures.snapshot
import io.github.ukpratik.mukhar.data.prefs.HabitMarks
import io.github.ukpratik.mukhar.data.prefs.ReminderSettings
import io.github.ukpratik.mukhar.data.prefs.Routine
import io.github.ukpratik.mukhar.domain.streak.Source
import org.junit.Test
import java.time.LocalTime

/** S07 state derivation (US-030). */
class TodayStateMapperTest {
    @Test
    fun firstDay() {
        val s = TodayFixtures.firstDay
        assertThat(s.greeting).isEqualTo(Greeting.MORNING)
        assertThat(s.streak!!.dayNumber).isEqualTo(1)
        assertThat(s.streak!!.line).isEqualTo(StreakLine.WARMUP)
        assertThat(s.talk!!.eyebrow).isEqualTo(TalkEyebrow.FIRST)
        assertThat(s.talk!!.easy).isTrue()
        assertThat(s.showBaseline).isTrue()
        assertThat(s.baselineFirst).isTrue() // Revise is empty on day 1, so the baseline card moves up
        assertThat(s.revise.kind).isEqualTo(ReviseKind.NOTHING)
        assertThat(s.streak!!.dots.first()).isEqualTo(DayDot.TODAY_DONE)
    }

    @Test
    fun midDay() {
        val s = TodayFixtures.midDay
        assertThat(s.greeting).isEqualTo(Greeting.AFTERNOON)
        assertThat(s.streak!!.useDayLabel).isFalse()
        assertThat(s.streak!!.streak).isEqualTo(12)
        assertThat(s.streak!!.covered).isEqualTo(4)
        assertThat(s.streak!!.line).isEqualTo(StreakLine.GOAL_TODAY)
        assertThat(s.streak!!.dots).containsExactly(DayDot.DONE, DayDot.DONE, DayDot.DONE, DayDot.DONE, DayDot.TODAY, DayDot.EMPTY, DayDot.EMPTY).inOrder()
        assertThat(s.talk!!.eyebrow).isEqualTo(TalkEyebrow.TODAY)
        assertThat(s.revise.kind).isEqualTo(ReviseKind.DUE)
        assertThat(s.revise.minutes).isEqualTo(2) // 8 cards × 15 s
        assertThat(s.doneForToday).isFalse()
    }

    @Test
    fun reviseMinutesGrowWithTheDueCount() {
        // I-002: 10 and 30 due both said "~3 min".
        val habit = TodayFixtures.snapshot(emptyMap(), TodayFixtures.monday)
        assertThat(TodayStateMapper.map(TodayFixtures.inputs(habit = habit, dueNow = 10)).revise.minutes).isEqualTo(3)
        assertThat(TodayStateMapper.map(TodayFixtures.inputs(habit = habit, dueNow = 30)).revise.minutes).isEqualTo(8)
    }

    @Test
    fun doneForTodayTeasesTomorrow() {
        val s = TodayFixtures.done
        assertThat(s.greeting).isEqualTo(Greeting.EVENING)
        assertThat(s.doneForToday).isTrue()
        assertThat(s.talk).isNull()
        assertThat(s.tomorrowPrompt).isNotNull()
        assertThat(s.streak!!.met).isTrue()
        assertThat(s.streak!!.line).isEqualTo(StreakLine.MET)
        assertThat(s.revise.kind).isEqualTo(ReviseKind.CAUGHT_UP)
    }

    @Test
    fun offline() {
        val s = TodayFixtures.offline
        assertThat(s.offline).isTrue()
        assertThat(s.talk!!.analysedLater).isTrue()
        assertThat(s.talk!!.eyebrow).isEqualTo(TalkEyebrow.AGAIN)
        assertThat(s.revise.offline).isTrue()
    }

    @Test
    fun talksOff() {
        val s = TodayFixtures.talksOff
        assertThat(s.talksEnabled).isFalse()
        assertThat(s.talk).isNull()
        assertThat(s.showBaseline).isFalse()
        assertThat(s.streak!!.line).isEqualTo(StreakLine.TALKS_OFF)
        assertThat(s.streak!!.dayNumber).isEqualTo(3) // two days done, today is day 3
    }

    @Test
    fun freshStartAfterABreak() {
        val h = snapshot(mapOf(monday to Source.TALK), monday.plusDays(5))
        val s = TodayStateMapper.map(inputs(habit = h))
        assertThat(s.streak!!.line).isEqualTo(StreakLine.FRESH)
        assertThat(s.streak!!.dayNumber).isEqualTo(1)
        assertThat(s.autoOpenSheet).isTrue() // the fresh-start sheet opens once
    }

    @Test
    fun aFreezeNoticeOpensTheSheet() {
        val h = snapshot(TodayFixtures.days(monday, monday.plusDays(3)), monday.plusDays(4), marks = HabitMarks(freezeNoticeDay = monday.plusDays(3)))
        assertThat(TodayStateMapper.map(inputs(habit = h)).autoOpenSheet).isTrue()
    }

    @Test
    fun notificationTipOnlyWhenDeniedAndRecent() {
        val h = snapshot(mapOf(monday to Source.ONBOARDING), monday)
        val reminder = ReminderSettings(LocalTime.of(8, 15), Routine.MORNING_TEA)
        val base = inputs(habit = h, settings = consented.copy(reminder = reminder))
        assertThat(TodayStateMapper.map(base.copy(notificationsAllowed = false)).notificationTip).isEqualTo(reminder)
        assertThat(TodayStateMapper.map(base).notificationTip).isNull()
        val dismissed = base.copy(settings = base.settings.copy(notificationTipDismissed = true), notificationsAllowed = false)
        assertThat(TodayStateMapper.map(dismissed).notificationTip).isNull()
        val later = base.copy(now = base.now.plusDays(4), notificationsAllowed = false)
        assertThat(TodayStateMapper.map(later).notificationTip).isNull()
    }

    @Test
    fun greetings() {
        assertThat(TodayStateMapper.greeting(3)).isEqualTo(Greeting.MORNING)
        assertThat(TodayStateMapper.greeting(12)).isEqualTo(Greeting.AFTERNOON)
        assertThat(TodayStateMapper.greeting(17)).isEqualTo(Greeting.EVENING)
        assertThat(TodayStateMapper.greeting(1)).isEqualTo(Greeting.EVENING)
    }
}
