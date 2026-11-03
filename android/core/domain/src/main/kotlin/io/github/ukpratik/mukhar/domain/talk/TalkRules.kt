package io.github.ukpratik.mukhar.domain.talk

import io.github.ukpratik.mukhar.domain.metrics.Metrics
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import kotlin.math.abs
import kotlin.math.roundToLong

/** Free-storage policy before recording (US-018, D-028): warn below 50 MB, block below 10 MB. */
enum class StorageLevel { OK, LOW, BLOCKED }

object StoragePolicy {
    const val WARN_BYTES = 50L * 1024 * 1024
    const val BLOCK_BYTES = 10L * 1024 * 1024

    fun level(availableBytes: Long): StorageLevel = when {
        availableBytes < BLOCK_BYTES -> StorageLevel.BLOCKED
        availableBytes < WARN_BYTES -> StorageLevel.LOW
        else -> StorageLevel.OK
    }
}

/** Speech thresholds at stop time (US-011, US-018, US-031). */
object TalkThresholds {
    /** Below this, "We couldn't hear much": no API call, no credit, audio discarded. */
    const val MIN_ANALYSE_SPEECH_MS = 10_000L

    /** At or above this, the streak day is credited at stop time (same as `StreakEngine.MIN_SPEECH_MS`). */
    const val MIN_CREDIT_SPEECH_MS = 30_000L

    const val MAX_PAUSES = 2
    const val THINK_TIME_S = 30
    const val FREE_CAP_S = 120
    const val PREMIUM_CAP_S = 180
}

/** Say It Again (US-017). */
object SayItAgain {
    const val MIN_CAP_S = 45L
    const val CELEBRATE_RATIO = 0.10

    /**
     * The re-take cap: about 25% less than the first take, rounded to 5 s, at least 0:45 and never above the tier
     * cap (S11 notes: 3:00 → 2:15, 2:40 → 2:00, 2:00 → 1:30).
     */
    fun capSeconds(firstTakeMs: Long, tierCapS: Int): Long {
        val reduced = (firstTakeMs * 0.75 / 1000.0 / 5.0).roundToLong() * 5
        return reduced.coerceAtLeast(MIN_CAP_S).coerceAtMost(tierCapS.toLong())
    }

    /** Relative change, positive = the re-take is lower. `null` when the first value is 0 (nothing to compare). */
    fun reduction(first: Double, retake: Double): Double? = if (first <= 0.0) null else (first - retake) / first

    /** Fillers/min and long pauses/min improve when they fall by ≥ 10%. */
    fun improvedDown(first: Double?, retake: Double?): Boolean {
        if (first == null || retake == null) return false
        val r = reduction(first, retake) ?: return false
        return r >= CELEBRATE_RATIO
    }

    /**
     * Pace improves when the re-take moved into the comfortable band from outside it, or its distance from the band
     * shrank by ≥ 10% of the first take's pace.
     */
    fun paceImproved(first: Metrics, retake: Metrics, low: Double, high: Double): Boolean {
        if (first.wpmBand == WpmBand.COMFORTABLE) return false
        if (retake.wpmBand == WpmBand.COMFORTABLE) return true
        fun distance(w: Double) = if (w < low) low - w else if (w > high) w - high else 0.0
        if (first.wpm <= 0.0) return false
        return (distance(first.wpm) - distance(retake.wpm)) / first.wpm >= CELEBRATE_RATIO
    }

    data class Deltas(
        val fillersImproved: Boolean,
        val pausesImproved: Boolean,
        val paceImproved: Boolean,
    ) {
        val celebrate: Boolean get() = fillersImproved || pausesImproved || paceImproved
    }

    fun deltas(first: Metrics, retake: Metrics, bandLow: Double, bandHigh: Double): Deltas = Deltas(
        fillersImproved = improvedDown(first.fillersPerMin, retake.fillersPerMin),
        pausesImproved = improvedDown(first.longPausesPerMin, retake.longPausesPerMin),
        paceImproved = paceImproved(first, retake, bandLow, bandHigh),
    )

    /** "About the same" when the change is under 10% either way. */
    fun aboutSame(first: Double, retake: Double): Boolean =
        if (first <= 0.0) retake <= 0.0 else abs(first - retake) / first < CELEBRATE_RATIO
}
