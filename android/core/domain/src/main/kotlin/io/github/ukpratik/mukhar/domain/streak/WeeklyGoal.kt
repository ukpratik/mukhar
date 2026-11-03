package io.github.ukpratik.mukhar.domain.streak

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One week's ring (Mon–Sun in streak-day terms). [days] has 7 entries, Monday first. */
data class WeekProgress(val weekStart: LocalDate, val days: List<Boolean>, val goal: Int) {
    val covered: Int get() = days.count { it }
    val met: Boolean get() = covered >= goal
}

object WeeklyGoal {
    val ALLOWED: Set<Int> = setOf(3, 5, 7)

    fun weekProgress(days: Map<LocalDate, Source>, today: LocalDate, goal: Int): WeekProgress {
        require(goal in ALLOWED) { "Weekly goal must be 3, 5 or 7" }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return WeekProgress(monday, (0L until 7L).map { monday.plusDays(it) in days }, goal)
    }
}
