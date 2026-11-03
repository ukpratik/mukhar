package io.github.ukpratik.mukhar.domain.fsrs

import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * FSRS-6 scheduler core, ported from py-fsrs 6.3.2 (`fsrs/scheduler.py`, MIT, open-spaced-repetition).
 * Default 21 weights, desired retention 0.90, learning steps [1 m, 10 m], relearning [10 m], fuzz on, no optimiser
 * (LLD §4.5). Python's `round` is half-to-even, so this uses [round] (also half-to-even); `timedelta.days` floors, as does [Duration.toDays]
 * for the non-negative spans used here.
 *
 * @param random returns a value in [0, 1) for interval fuzz; injected for deterministic tests.
 */
class Fsrs(
    private val w: DoubleArray = DEFAULT_PARAMETERS,
    private val desiredRetention: Double = 0.9,
    private val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    private val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    private val maximumInterval: Int = 36_500,
    private val enableFuzzing: Boolean = true,
    private val random: () -> Double = { kotlin.random.Random.nextDouble() },
) {
    init {
        require(w.size == 21) { "FSRS-6 needs 21 parameters" }
        require(desiredRetention > 0 && desiredRetention < 1) { "desiredRetention must be in (0, 1)" }
    }

    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    /** Probability of recall at [now] (0 for a card never reviewed). */
    fun retrievability(card: Card, now: Instant): Double {
        val last = card.lastReview ?: return 0.0
        val s = card.stability ?: return 0.0
        val elapsed = max(0L, Duration.between(last, now).toDays())
        return (1 + factor * elapsed / s).pow(decay)
    }

    fun review(card: Card, rating: Rating, now: Instant, durationMs: Long? = null): Pair<Card, ReviewLogEntry> {
        val daysSinceLast: Long? = card.lastReview?.let { Duration.between(it, now).toDays() }
        val sameDay = daysSinceLast != null && daysSinceLast < 1
        val s0 = card.stability
        val d0 = card.difficulty
        val r = retrievability(card, now)

        val s: Double
        val d: Double
        if (s0 == null || d0 == null) {
            s = initialStability(rating)
            d = clampDifficulty(initialDifficulty(rating))
        } else if (sameDay) {
            s = shortTermStability(s0, rating)
            d = nextDifficulty(d0, rating)
        } else {
            s = nextStability(d0, s0, r, rating)
            d = nextDifficulty(d0, rating)
        }

        var state: CardState
        var step: Int?
        val interval: Duration
        when (card.state) {
            CardState.NEW, CardState.LEARNING, CardState.RELEARNING -> {
                val steps = if (card.state == CardState.RELEARNING) relearningSteps else learningSteps
                val currentStep = card.step ?: 0
                state = card.state.let { if (it == CardState.NEW) CardState.LEARNING else it }
                step = currentStep
                if (steps.isEmpty() || (currentStep >= steps.size && rating != Rating.AGAIN)) {
                    state = CardState.REVIEW
                    step = null
                    interval = days(nextInterval(s))
                } else {
                    when (rating) {
                        Rating.AGAIN -> {
                            step = 0
                            interval = steps[0]
                        }
                        Rating.HARD -> interval = when {
                            currentStep == 0 && steps.size == 1 -> scale(steps[0], 1.5)
                            currentStep == 0 -> scale(steps[0].plus(steps[1]), 0.5)
                            else -> steps[currentStep]
                        }
                        Rating.GOOD -> if (currentStep + 1 == steps.size) {
                            state = CardState.REVIEW
                            step = null
                            interval = days(nextInterval(s))
                        } else {
                            step = currentStep + 1
                            interval = steps[currentStep + 1]
                        }
                        Rating.EASY -> {
                            state = CardState.REVIEW
                            step = null
                            interval = days(nextInterval(s))
                        }
                    }
                }
            }
            CardState.REVIEW -> {
                if (rating == Rating.AGAIN && relearningSteps.isNotEmpty()) {
                    state = CardState.RELEARNING
                    step = 0
                    interval = relearningSteps[0]
                } else {
                    state = CardState.REVIEW
                    step = null
                    interval = days(nextInterval(s))
                }
            }
        }

        val finalInterval = if (enableFuzzing && state == CardState.REVIEW) fuzz(interval) else interval
        val lapse = card.state == CardState.REVIEW && rating == Rating.AGAIN
        val next = card.copy(
            due = now.plus(finalInterval),
            stability = s,
            difficulty = d,
            elapsedDays = daysSinceLast?.toInt() ?: 0,
            scheduledDays = finalInterval.toDays().toInt(),
            reps = card.reps + 1,
            lapses = card.lapses + if (lapse) 1 else 0,
            state = state,
            step = step,
            lastReview = now,
        )
        return next to ReviewLogEntry(card.id, rating, now, card.state, s, durationMs)
    }

    /** What each rating would do, for the grade buttons' interval hints. */
    fun preview(card: Card, now: Instant): Map<Rating, Card> =
        Rating.entries.associateWith { review(card, it, now).first }

    // ---- FSRS-6 formulas ----

    private fun clampStability(s: Double) = max(s, STABILITY_MIN)
    private fun clampDifficulty(d: Double) = min(max(d, MIN_DIFFICULTY), MAX_DIFFICULTY)

    private fun initialStability(rating: Rating) = clampStability(w[rating.value - 1])

    private fun initialDifficulty(rating: Rating) = w[4] - exp(w[5] * (rating.value - 1)) + 1

    internal fun nextInterval(stability: Double): Int {
        val raw = (stability / factor) * (desiredRetention.pow(1 / decay) - 1)
        return min(max(round(raw), 1.0), maximumInterval.toDouble()).toInt()
    }

    private fun shortTermStability(s: Double, rating: Rating): Double {
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * s.pow(-w[19])
        if (rating != Rating.AGAIN) increase = max(increase, 1.0)
        return clampStability(s * increase)
    }

    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val delta = -(w[6] * (rating.value - 3))
        val damped = d + (10.0 - d) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Rating.EASY) + (1 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun nextStability(d: Double, s: Double, r: Double, rating: Rating): Double = clampStability(
        if (rating == Rating.AGAIN) {
            val longTerm = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])
            val shortTerm = s / exp(w[17] * w[18])
            min(longTerm, shortTerm)
        } else {
            val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
            val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
            s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus)
        },
    )

    internal fun fuzzRange(intervalDays: Long): Pair<Int, Int> {
        var delta = 1.0
        for ((start, end, f) in FUZZ_RANGES) delta += f * max(min(intervalDays.toDouble(), end) - start, 0.0)
        var lo = round(intervalDays - delta).toInt()
        var hi = round(intervalDays + delta).toInt()
        lo = max(2, lo)
        hi = min(hi, maximumInterval)
        lo = min(lo, hi)
        return lo to hi
    }

    private fun fuzz(interval: Duration): Duration {
        val intervalDays = interval.toDays()
        if (intervalDays < 2.5) return interval
        val (lo, hi) = fuzzRange(intervalDays)
        val fuzzed = random() * (hi - lo + 1) + lo
        return days(min(round(fuzzed).toInt(), maximumInterval))
    }

    private fun days(n: Int): Duration = Duration.ofDays(n.toLong())

    private fun scale(d: Duration, by: Double): Duration = Duration.ofNanos((d.toNanos() * by).toLong())

    companion object {
        const val STABILITY_MIN = 0.001
        const val MIN_DIFFICULTY = 1.0
        const val MAX_DIFFICULTY = 10.0

        val DEFAULT_PARAMETERS = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        private val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )
    }
}
