package com.vaak.app.feature.progress

import com.vaak.data.db.InsightEntity
import com.vaak.data.repo.ProgressData
import com.vaak.data.repo.TalkKind
import com.vaak.data.repo.TalkRecord
import com.vaak.domain.metrics.WpmBand
import com.vaak.domain.model.Lang
import java.time.LocalDate
import java.time.ZoneId

object ProgressFixtures {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val today: LocalDate = LocalDate.of(2026, 11, 6)

    fun record(
        id: String,
        day: LocalDate,
        kind: TalkKind = TalkKind.TALK,
        wpm: Double = 138.0,
        band: WpmBand = WpmBand.COMFORTABLE,
        fillers: Double = 2.1,
        pauses: Double? = 0.7,
        mattr: Double? = 0.71,
        lang: Lang = Lang.EN,
    ) = TalkRecord(
        id = id, kind = kind, lang = lang, at = day.atTime(19, 0).atZone(zone).toInstant(), day = day, speechMs = 100_000,
        durationMs = 120_000, audio = null, wpm = wpm, band = band, fillersPerMin = fillers, longPausesPerMin = pauses,
        repetitions = 1, mattr = mattr, lexicalTokens = 160,
    )

    val baseline = record("b", LocalDate.of(2026, 10, 12), TalkKind.BASELINE, wpm = 162.0, band = WpmBand.FAST, fillers = 5.8, pauses = 1.4, mattr = 0.58)

    fun data(records: List<TalkRecord>, insights: List<InsightEntity> = emptyList(), words: Int = 128) = ProgressData(
        records = records,
        practisedDays = records.map { it.day }.toSet(),
        bestStreak = 19,
        currentStreak = 19,
        weeklyGoal = 5,
        wordsLearned = words,
        insights = insights,
        today = today,
    )

    /** Baseline, then 6 weekly talks with fillers falling. */
    val many: ProgressData = data(
        listOf(baseline) + (0 until 6).map { i ->
            record("t$i", today.minusWeeks(5L - i), fillers = 6.0 - i * 0.8, band = if (i < 2) WpmBand.FAST else WpmBand.COMFORTABLE, mattr = 0.6 + i * 0.02)
        },
    )

    val one: ProgressData = data(listOf(record("t0", today)), words = 3)
}
