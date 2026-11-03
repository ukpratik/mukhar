package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.db.HabitDao
import io.github.ukpratik.mukhar.data.db.HabitStateEntity
import io.github.ukpratik.mukhar.data.db.StreakDayEntity
import io.github.ukpratik.mukhar.data.prefs.HabitMarks
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.streak.Activity
import io.github.ukpratik.mukhar.domain.streak.HabitState
import io.github.ukpratik.mukhar.domain.streak.Source
import io.github.ukpratik.mukhar.domain.streak.StreakEngine
import io.github.ukpratik.mukhar.domain.streak.StreakState
import io.github.ukpratik.mukhar.domain.streak.WeekProgress
import io.github.ukpratik.mukhar.domain.streak.WeeklyGoal
import io.github.ukpratik.mukhar.domain.streak.streakDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A repair session (US-032): a talk with ≥ 30 s of speech or a review of ≥ 5 cards. */
enum class SessionKind(val key: String) {
    TALK("talk"),
    REVIEW("review");

    companion object {
        fun fromKey(key: String): SessionKind? = entries.firstOrNull { it.key == key }
    }
}

/** Everything Today (S07) and the streak sheet (S20) need, for one streak day. */
data class HabitSnapshot(
    val today: LocalDate,
    val days: Map<LocalDate, Source>,
    val habit: HabitState,
    val streak: StreakState,
    /** Goal progress. Frozen days keep the streak but don't add to the week (S20). */
    val week: WeekProgress,
    val frozenThisWeek: Set<LocalDate>,
    val todaySource: Source?,
    /** The missed day a repair would cover, when one is open (US-032). */
    val repairDay: LocalDate?,
    /** The streak a repair brings back. */
    val repairableStreak: Int,
    val sessionsToday: List<SessionKind>,
    /** Set when freezes covered missed days; the sheet tells the user once (S20 state 2). */
    val freezeNoticeDay: LocalDate?,
    /** The repair window has passed: show "Fresh start" once (S20 state 4). */
    val freshStart: Boolean,
    /** Days until the next freeze, or null when 2 are already held. */
    val nextFreezeInDays: Int?,
) {
    val coveredToday: Boolean get() = streak.coveredToday
    val repairSessionsDone: Int get() = sessionsToday.size.coerceAtMost(StreakEngine.REPAIR_SESSIONS)
}

data class CreditResult(
    val creditedDay: LocalDate?,
    val streak: Int,
    val repairedDay: LocalDate?,
    val freezesEarned: Int,
)

/**
 * Wires the pure [StreakEngine] to Room (`streak_day`, `habit_state`). Credited days are inserted with IGNORE and
 * never rewritten, so a clock or zone change can't remove or double-credit a day (US-031). All writes go through
 * one mutex so a talk and a review finishing together can't race. "Now" comes from the injected [Clock]
 * (`OverridableClock` in the app, so QA's clock shift applies to credits too).
 */
@Singleton
class HabitRepository @Inject constructor(
    private val dao: HabitDao,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Pass a ticker to re-evaluate "today" across the 03:00 boundary while the screen stays open. */
    fun observe(ticks: Flow<Unit> = flowOf(Unit)): Flow<HabitSnapshot> =
        combine(dao.observeStreakDays(), dao.observeHabitState(), settings.habitMarks, ticks) { days, habit, marks, _ ->
            compute(days.toDomain(), habit.toDomain(), marks, today())
        }

    suspend fun snapshot(): HabitSnapshot =
        compute(dao.streakDays().toDomain(), dao.habitState().toDomain(), settings.habitMarks.first(), today())

    /** On app open: spend freezes on missed days when they save the streak (US-032, D-029). */
    suspend fun refresh(): HabitSnapshot {
        mutex.withLock { applyMissedDays(today(), clock.zone()) }
        return snapshot()
    }

    /**
     * Credits a practice event at its stop time (US-031): applies missed days first, counts it towards a repair,
     * credits the day if it qualifies and isn't covered yet, then awards freezes for the new streak.
     */
    suspend fun credit(activity: Activity): CreditResult = mutex.withLock {
        val today = streakDay(activity.at, activity.zone)
        var (days, habit) = applyMissedDays(today, activity.zone)
        if (!StreakEngine.qualifies(activity)) {
            return@withLock CreditResult(null, StreakEngine.streak(days, today), null, 0)
        }
        val now = clock.now().toEpochMilli()

        var repaired: LocalDate? = null
        val kind = when (activity) {
            is Activity.Talk -> SessionKind.TALK
            is Activity.Review -> SessionKind.REVIEW
            is Activity.Onboarding -> null
        }
        if (kind != null) {
            settings.recordSession(today, kind.key)
            val sessions = settings.habitMarks.first().let { if (it.sessionDay == today) it.sessionKinds.size else 0 }
            StreakEngine.repairIfEarned(days, habit, today, sessions)?.let { (newDays, newHabit) ->
                repaired = (newDays.keys - days.keys).single()
                dao.insertStreakDay(StreakDayEntity(repaired.toString(), Source.REPAIR.key, now, activity.zone.id))
                saveHabit(newHabit)
                days = newDays
                habit = newHabit
            }
        }

        val day = StreakEngine.creditFor(activity, days.keys.toSortedSet())
        if (day != null) {
            val source = when (activity) {
                is Activity.Talk -> Source.TALK
                is Activity.Review -> Source.REVIEW
                is Activity.Onboarding -> Source.ONBOARDING
            }
            dao.insertStreakDay(StreakDayEntity(day.toString(), source.key, now, activity.zone.id))
            days = days + (day to source)
        }

        val streak = StreakEngine.streak(days, today)
        val awarded = StreakEngine.awardFreezes(streak, habit)
        if (awarded != habit) saveHabit(awarded)
        CreditResult(day, streak, repaired, (awarded.freezesHeld - habit.freezesHeld).coerceAtLeast(0))
    }

    /**
     * The talk pipeline's entry point (US-031): a talk stopped at [at] with [speechMs] of speech, offline-queued
     * and recovered talks too. Goes through [credit] (missed days, repair, freezes). Returns the "Day N counted"
     * pill when this talk credited its day, null when it didn't qualify or the day was already covered.
     */
    suspend fun creditTalk(at: Instant, zone: ZoneId, speechMs: Long): Credit? {
        val day = credit(Activity.Talk(at, zone, speechMs)).creditedDay ?: return null
        return creditFor(day, dao.streakDays().toDomain(), dao.habitState().toDomain())
    }

    /** The credit pill for a talk recorded at [at] (S11), if its streak day is covered. */
    fun observeCredit(at: Instant, zone: ZoneId): Flow<Credit?> =
        combine(dao.observeStreakDays(), dao.observeHabitState()) { rows, habit ->
            val day = streakDay(at, zone)
            val days = rows.toDomain()
            if (day in days) creditFor(day, days, habit.toDomain()) else null
        }

    suspend fun setWeeklyGoal(goal: Int) {
        require(goal in WeeklyGoal.ALLOWED) { "Weekly goal must be 3, 5 or 7" }
        mutex.withLock { saveHabit(dao.habitState().toDomain().copy(weeklyGoal = goal)) }
    }

    suspend fun acknowledgeFreezeNotice() = settings.setFreezeNotice(null)

    /** Fresh start is shown once per break: remember which last-covered day it was shown for. */
    suspend fun acknowledgeFreshStart() {
        dao.streakDays().maxOfOrNull { LocalDate.parse(it.day) }?.let { settings.setFreshStartAck(it) }
    }

    suspend fun wordsLearned(): Int = dao.wordCount()

    private suspend fun applyMissedDays(today: LocalDate, zone: ZoneId): Pair<Map<LocalDate, Source>, HabitState> {
        val days = dao.streakDays().toDomain()
        val habit = dao.habitState().toDomain()
        val (newDays, newHabit) = StreakEngine.applyMissedDays(days, habit, today)
        val frozen = (newDays.keys - days.keys).sorted()
        if (frozen.isNotEmpty()) {
            val now = clock.now().toEpochMilli()
            frozen.forEach { dao.insertStreakDay(StreakDayEntity(it.toString(), Source.FREEZE.key, now, zone.id)) }
            saveHabit(newHabit)
            settings.setFreezeNotice(frozen.last())
        }
        return newDays to newHabit
    }

    private suspend fun saveHabit(h: HabitState) = dao.upsertHabitState(
        HabitStateEntity(
            freezesHeld = h.freezesHeld,
            lastRepairDay = h.lastRepairDay?.toString(),
            weeklyGoal = h.weeklyGoal,
            lastFreezeAwardStreak = h.lastFreezeAwardStreak,
        ),
    )

    private fun today(): LocalDate = streakDay(clock.now(), clock.zone())

    /** Frozen days keep the streak but don't count towards the weekly goal (S20), as in [compute]. */
    private fun creditFor(day: LocalDate, days: Map<LocalDate, Source>, habit: HabitState): Credit {
        val goal = habit.weeklyGoal.takeIf { it in WeeklyGoal.ALLOWED } ?: HabitState().weeklyGoal
        return Credit(
            day = day,
            streak = StreakEngine.streak(days, day),
            weeklyGoalMet = WeeklyGoal.weekProgress(days.filterValues { it != Source.FREEZE }, day, goal).met,
        )
    }

    companion object {
        /** Pure snapshot for one streak day (public for UI tests and previews). */
        fun compute(days: Map<LocalDate, Source>, habit: HabitState, marks: HabitMarks, today: LocalDate): HabitSnapshot {
            val state = StreakEngine.state(days, habit, today)
            val goal = habit.weeklyGoal.takeIf { it in WeeklyGoal.ALLOWED } ?: HabitState().weeklyGoal
            val week = WeeklyGoal.weekProgress(days.filterValues { it != Source.FREEZE }, today, goal)
            val weekDays = (0L until 7L).map { week.weekStart.plusDays(it) }.toSet()
            val repairDay = StreakEngine.canRepair(days, habit, today)
            val lastCovered = days.keys.maxOrNull()
            return HabitSnapshot(
                today = today,
                days = days,
                habit = habit.copy(weeklyGoal = goal),
                streak = state,
                week = week,
                frozenThisWeek = days.filter { it.value == Source.FREEZE && it.key in weekDays }.keys,
                todaySource = days[today],
                repairDay = repairDay,
                repairableStreak = repairDay?.let { StreakEngine.streak(days, it.minusDays(1)) } ?: 0,
                sessionsToday = if (marks.sessionDay == today) marks.sessionKinds.mapNotNull(SessionKind::fromKey) else emptyList(),
                freezeNoticeDay = marks.freezeNoticeDay,
                freshStart = state.current == 0 && lastCovered != null && repairDay == null &&
                    marks.freshStartAckDay != lastCovered,
                nextFreezeInDays = if (habit.freezesHeld >= StreakEngine.MAX_FREEZES) {
                    null
                } else {
                    StreakEngine.FREEZE_EVERY_DAYS - state.current % StreakEngine.FREEZE_EVERY_DAYS
                },
            )
        }

        private fun List<StreakDayEntity>.toDomain(): Map<LocalDate, Source> =
            associate { LocalDate.parse(it.day) to Source.fromKey(it.source) }

        private fun HabitStateEntity?.toDomain(): HabitState = this?.let {
            HabitState(it.freezesHeld, it.lastRepairDay?.let(LocalDate::parse), it.weeklyGoal, it.lastFreezeAwardStreak)
        } ?: HabitState()
    }
}
