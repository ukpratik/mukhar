package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.db.HabitDao
import io.github.ukpratik.mukhar.data.db.InsightEntity
import io.github.ukpratik.mukhar.data.db.ProgressDao
import io.github.ukpratik.mukhar.data.db.TalkMetricsRow
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.insight.InsightInput
import io.github.ukpratik.mukhar.domain.insight.InsightRules
import io.github.ukpratik.mukhar.domain.metrics.TalkSummary
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.progress.TalkPoint
import io.github.ukpratik.mukhar.domain.streak.Source
import io.github.ukpratik.mukhar.domain.streak.StreakEngine
import io.github.ukpratik.mukhar.domain.streak.streakDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One analysed talk's numbers. [audio] is set only when its recording was kept and still exists on the phone. */
data class TalkRecord(
    val id: String,
    val kind: TalkKind,
    val lang: Lang,
    val at: Instant,
    val day: LocalDate,
    val speechMs: Long,
    val durationMs: Long,
    val audio: File?,
    val wpm: Double,
    val band: WpmBand,
    val fillersPerMin: Double,
    val longPausesPerMin: Double?,
    val repetitions: Int?,
    val mattr: Double?,
    val lexicalTokens: Int,
) {
    fun toPoint() = TalkPoint(lang, day, speechMs, band, fillersPerMin, mattr)
    fun toSummary() = TalkSummary(lang, at, wpm, fillersPerMin, longPausesPerMin, repetitions, mattr, lexicalTokens)
}

data class ProgressData(
    /** Every analysed talk, re-take and baseline, oldest first. */
    val records: List<TalkRecord>,
    /** Credited streak days (not freezes), for "goal met" weeks. */
    val practisedDays: Set<LocalDate>,
    val bestStreak: Int,
    val currentStreak: Int,
    val weeklyGoal: Int,
    val wordsLearned: Int,
    val insights: List<InsightEntity>,
    val today: LocalDate,
) {
    /** Coached talks: the trends and insights count these (not re-takes or the baseline). */
    val talks: List<TalkRecord> get() = records.filter { it.kind == TalkKind.TALK }
    val baseline: TalkRecord? get() = records.firstOrNull { it.kind == TalkKind.BASELINE }

    /** The latest coached talk after the baseline, in the baseline's language (comparisons stay within a language). */
    val latestComparable: TalkRecord?
        get() {
            val b = baseline ?: return null
            return talks.lastOrNull { it.lang == b.lang && it.at > b.at }
        }
}

/**
 * Progress (US-050, US-051): Room flows mapped to [ProgressData], and the R1 insight rules evaluated and persisted in
 * `insight` (each fires once; a dismissed one is marked seen and never shown again).
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val dao: ProgressDao,
    private val habitDao: HabitDao,
    private val clock: Clock,
) {
    fun observe(): Flow<ProgressData> = combine(
        dao.observeAnalysedTalks(),
        habitDao.observeStreakDays(),
        habitDao.observeHabitState(),
        dao.observeWordCount(),
        dao.observeInsights(),
    ) { rows, days, habit, words, insights ->
        val sources = days.associate { LocalDate.parse(it.day) to Source.fromKey(it.source) }
        val today = streakDay(clock.now(), clock.zone())
        ProgressData(
            records = rows.mapNotNull(::toRecord),
            practisedDays = sources.filterValues { it != Source.FREEZE }.keys,
            bestStreak = StreakEngine.best(sources),
            currentStreak = StreakEngine.streak(sources, today),
            weeklyGoal = habit?.weeklyGoal ?: DEFAULT_GOAL,
            wordsLearned = words,
            insights = insights,
            today = today,
        )
    }

    /** Runs the insight rules on everything stored and saves the new ones unseen. Returns their keys. */
    suspend fun evaluateInsights(): List<String> {
        val talks = dao.analysedTalks().mapNotNull(::toRecord).filter { it.kind == TalkKind.TALK }
        val sources = habitDao.streakDays().associate { LocalDate.parse(it.day) to Source.fromKey(it.source) }
        val achieved = dao.insights().map { it.ruleKey }.toSet()
        val hits = InsightRules.evaluate(
            InsightInput(
                talks = talks.map { it.toSummary() },
                bestStreak = StreakEngine.best(sources),
                wordsLearned = dao.wordCount(),
                achieved = achieved,
            ),
        )
        val now = clock.now().toEpochMilli()
        hits.forEach { dao.insertInsight(InsightEntity(it, now, seen = false)) }
        return hits
    }

    /** ✕ on the insight card: permanent for that insight (S16 notes). */
    suspend fun dismissInsight(key: String) = dao.markInsightSeen(key)

    private fun toRecord(r: TalkMetricsRow): TalkRecord? {
        val kind = TalkKind.entries.firstOrNull { it.key == r.kind } ?: return null
        val lang = Lang.entries.firstOrNull { it.code == r.lang } ?: return null
        val at = Instant.ofEpochMilli(r.createdAt + r.durationMs)
        val zone = runCatching { ZoneId.of(r.zoneId) }.getOrDefault(clock.zone())
        val audio = r.audioPath?.takeIf { r.audioKept }?.let(::File)?.takeIf(File::exists)
        return TalkRecord(
            id = r.id,
            kind = kind,
            lang = lang,
            at = at,
            day = streakDay(at, zone),
            speechMs = r.speechMs.toLong(),
            durationMs = r.durationMs.toLong(),
            audio = audio,
            wpm = r.wpm,
            band = WpmBand.entries.firstOrNull { it.key == r.wpmBand } ?: WpmBand.COMFORTABLE,
            fillersPerMin = r.fillersPerMin,
            longPausesPerMin = r.longPausesPerMin,
            repetitions = r.repetitions,
            mattr = r.mattr,
            lexicalTokens = r.lexicalTokens,
        )
    }

    private companion object {
        const val DEFAULT_GOAL = 5
    }
}
