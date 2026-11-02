package com.vaak.app.feature.today

import com.vaak.data.prefs.ReminderSettings
import com.vaak.data.prefs.Settings
import com.vaak.data.repo.HabitSnapshot
import com.vaak.data.repo.WordItem
import com.vaak.domain.model.Lang
import com.vaak.data.today.RecoveredTalk
import com.vaak.data.today.WaitingTalks
import com.vaak.domain.streak.Source
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

enum class Greeting { MORNING, AFTERNOON, EVENING }

enum class DayDot { EMPTY, TODAY, DONE, TODAY_DONE, FROZEN }

/** Which line the streak card shows (S07 copy). */
enum class StreakLine { WARMUP, MET, COUNTED, COUNTED_QUEUED, TALKS_OFF, GOAL_TODAY, KEEP_GOING, FRESH }

data class StreakCardUi(
    /** "Day N" before the first week, "N-day streak" after (S07). */
    val dayNumber: Int,
    val streak: Int,
    val useDayLabel: Boolean,
    val line: StreakLine,
    val freezes: Int,
    val covered: Int,
    val goal: Int,
    val met: Boolean,
    val dots: List<DayDot>,
)

enum class TalkEyebrow { FIRST, TODAY, AGAIN }

data class TalkCardUi(
    val eyebrow: TalkEyebrow,
    val prompt: TalkPrompt,
    val easy: Boolean,
    val analysedLater: Boolean,
)

enum class ReviseKind { DUE, CAUGHT_UP, NOTHING }

data class ReviseUi(val kind: ReviseKind, val due: Int, val dueTomorrow: Int, val minutes: Int, val offline: Boolean, val talksOff: Boolean)

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.of(1970, 1, 1),
    val greeting: Greeting = Greeting.MORNING,
    val firstName: String? = null,
    val offline: Boolean = false,
    val waiting: WaitingTalks? = null,
    val recovered: RecoveredTalk? = null,
    val streak: StreakCardUi? = null,
    val talksEnabled: Boolean = true,
    val talk: TalkCardUi? = null,
    val doneForToday: Boolean = false,
    val tomorrowPrompt: TalkPrompt? = null,
    val revise: ReviseUi = ReviseUi(ReviseKind.NOTHING, 0, 0, 0, offline = false, talksOff = false),
    val showBaseline: Boolean = false,
    val baselineFirst: Boolean = false,
    /** "Want a nudge after your morning tea?" for 3 days when notifications were denied (US-004). */
    val notificationTip: ReminderSettings? = null,
    val habit: HabitSnapshot? = null,
    /** Opens the streak sheet once to tell the user about a freeze or a fresh start (US-032). */
    val autoOpenSheet: Boolean = false,
    /** Today's words (US-020): from the user's talks first, then curated; picked once per streak day. */
    val words: List<WordItem> = emptyList(),
    /** Installed TTS voices; the speaker is hidden for a language without one. */
    val voices: Map<Lang, Boolean> = emptyMap(),
)

data class TodayInputs(
    val now: ZonedDateTime,
    val settings: Settings,
    val habit: HabitSnapshot,
    val dueNow: Int,
    val dueTomorrow: Int,
    val online: Boolean,
    val waiting: WaitingTalks?,
    val recovered: RecoveredTalk?,
    val notificationsAllowed: Boolean,
    val shuffle: Int,
)

/** Pure S07 state derivation (unit-tested): order and copy per US-030 and the design notes. */
object TodayStateMapper {
    const val DAY_LABEL_UNTIL = 7
    val TIP_DAYS: Duration = Duration.ofDays(3)

    fun map(i: TodayInputs): TodayUiState {
        val s = i.settings
        val h = i.habit
        val talksOn = s.talksEnabled
        val talkToday = h.todaySource == Source.TALK
        val everTalked = h.days.values.any { it == Source.TALK }
        val done = if (talksOn) talkToday && i.dueNow == 0 else h.todaySource == Source.REVIEW && i.dueNow == 0

        val reviseKind = when {
            i.dueNow > 0 -> ReviseKind.DUE
            i.dueTomorrow > 0 -> ReviseKind.CAUGHT_UP
            else -> ReviseKind.NOTHING
        }
        val talk = if (talksOn && !done) {
            TalkCardUi(
                eyebrow = when {
                    talkToday -> TalkEyebrow.AGAIN
                    !everTalked -> TalkEyebrow.FIRST
                    else -> TalkEyebrow.TODAY
                },
                prompt = StarterPrompts.pick(s.langs, s.tracks, h.today, i.shuffle, firstTalk = !everTalked),
                easy = !everTalked,
                analysedLater = !i.online,
            )
        } else {
            null
        }
        val tipWindowOpen = s.onboardedAt?.let { Duration.between(it, i.now.toInstant()) < TIP_DAYS } ?: false
        return TodayUiState(
            loading = false,
            date = i.now.toLocalDate(),
            greeting = greeting(i.now.hour),
            firstName = s.firstName?.trim()?.takeIf(String::isNotEmpty),
            offline = !i.online,
            waiting = i.waiting,
            recovered = i.recovered,
            streak = streakCard(h, talksOn, queued = i.waiting != null),
            talksEnabled = talksOn,
            talk = talk,
            doneForToday = done,
            tomorrowPrompt = if (done && talksOn) StarterPrompts.pick(s.langs, s.tracks, h.today.plusDays(1), 0, firstTalk = false) else null,
            revise = ReviseUi(
                kind = reviseKind,
                due = i.dueNow,
                dueTomorrow = i.dueTomorrow,
                minutes = ((i.dueNow + 2) / 3).coerceIn(1, 3),
                offline = !i.online,
                talksOff = !talksOn,
            ),
            showBaseline = talksOn && !s.baselineRecorded,
            baselineFirst = reviseKind == ReviseKind.NOTHING,
            notificationTip = s.reminder?.takeIf { !i.notificationsAllowed && !s.notificationTipDismissed && tipWindowOpen },
            habit = h,
            autoOpenSheet = h.freezeNoticeDay != null || h.freshStart,
        )
    }

    fun greeting(hour: Int): Greeting = when (hour) {
        in 3..11 -> Greeting.MORNING
        in 12..16 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }

    fun streakCard(h: HabitSnapshot, talksOn: Boolean, queued: Boolean): StreakCardUi {
        val covered = h.coveredToday
        val current = h.streak.current
        val dayNumber = current + if (covered) 0 else 1
        val line = when {
            covered && h.todaySource == Source.ONBOARDING && current == 1 -> StreakLine.WARMUP
            covered && h.week.met -> StreakLine.MET
            covered && queued -> StreakLine.COUNTED_QUEUED
            covered -> StreakLine.COUNTED
            !talksOn -> StreakLine.TALKS_OFF
            current == 0 && h.streak.best > 0 -> StreakLine.FRESH
            h.week.covered + 1 >= h.week.goal && !h.week.met -> StreakLine.GOAL_TODAY
            else -> StreakLine.KEEP_GOING
        }
        val dots = (0L until 7L).map { offset ->
            val d = h.week.weekStart.plusDays(offset)
            val src = h.days[d]
            when {
                src == Source.FREEZE -> DayDot.FROZEN
                src != null && d == h.today -> DayDot.TODAY_DONE
                src != null -> DayDot.DONE
                d == h.today -> DayDot.TODAY
                else -> DayDot.EMPTY
            }
        }
        return StreakCardUi(
            dayNumber = dayNumber,
            streak = current,
            useDayLabel = dayNumber < DAY_LABEL_UNTIL,
            line = line,
            freezes = h.streak.freezesHeld,
            covered = h.week.covered,
            goal = h.week.goal,
            met = h.week.met,
            dots = dots,
        )
    }
}
