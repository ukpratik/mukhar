package io.github.ukpratik.mukhar.app.feature.talk.report

import io.github.ukpratik.mukhar.data.repo.Credit
import io.github.ukpratik.mukhar.data.repo.TalkCodes
import io.github.ukpratik.mukhar.data.repo.TalkDetails
import io.github.ukpratik.mukhar.data.repo.TalkKind
import io.github.ukpratik.mukhar.data.repo.TalkStatus
import io.github.ukpratik.mukhar.domain.audio.EnergyVad
import io.github.ukpratik.mukhar.domain.feedback.FeedbackFallback
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.metrics.MetricAverages
import io.github.ukpratik.mukhar.domain.metrics.Metrics
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.domain.talk.SayItAgain
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Which S10/S11 state to draw. Analysing → Results is one screen (design-system §8). */
enum class ReportPhase { LISTENING, QUEUED, BUSY, STT_FAILED, TOO_LITTLE, METRICS_READY, FEEDBACK_FAILED, READY, HELPLINE, GONE }

/** The credit pill: [day] null means the neutral "30 s counts for your streak" variant (10 to 30 s of speech). */
data class CreditUi(val day: Int?, val goalMet: Boolean)

data class MetaUi(val lang: Lang, val track: Track, val durationMs: Long, val createdAt: Instant, val zone: ZoneId)

enum class MetricKind { PACE, FILLERS, PAUSES, REPEATS, VARIETY }

enum class MetricUnit { WPM, PER_MIN }

/** The delta row under a metric value: always an icon plus words, never colour alone. */
sealed interface MetricDelta {
    data class Band(val band: WpmBand, val provisional: Boolean) : MetricDelta
    data class Down(val from: String) : MetricDelta
    data class Up(val from: String) : MetricDelta
    data object Same : MetricDelta
    data class VarietyUp(val from: String) : MetricDelta
    data class VarietyDown(val from: String) : MetricDelta
    data object HindiProvisional : MetricDelta
}

data class MetricTileUi(
    val kind: MetricKind,
    val value: String? = null,
    val unit: MetricUnit? = null,
    /** Marker position on the pace band, 0..1 (slow 0–25%, comfortable 25–66.7%, fast beyond). */
    val bandPosition: Float? = null,
    val delta: MetricDelta? = null,
    val good: Boolean = false,
    /** Stutter-friendly: "Not scored" tile. */
    val off: Boolean = false,
    /** Word variety under 100 words: "Speak longer to unlock". */
    val locked: Boolean = false,
    val wide: Boolean = false,
)

data class TokenUi(
    val text: String,
    val filler: Boolean = false,
    val repeat: Boolean = false,
    val unsure: Boolean = false,
    val glow: Boolean = false,
    val grow: Boolean = false,
)

data class TranscriptUi(val tokens: List<TokenUi>, val paragraphs: List<IntRange>, val startsMs: List<Long>, val endsMs: List<Long>) {
    val hasFiller get() = tokens.any { it.filler }
    val hasRepeat get() = tokens.any { it.repeat }
    val hasUnsure get() = tokens.any { it.unsure }
    val hasGlow get() = tokens.any { it.glow }
    val hasGrow get() = tokens.any { it.grow }
}

/** [quote] is null for a metrics-based fallback (LLD §4.4). */
data class GlowUi(val quote: String?, val note: String)

data class GrowUi(val quote: String?, val why: String, val how: String)

data class BetterUi(val index: Int, val quote: String, val rewrite: String, val words: List<UpgradedWord>)

data class ReportUiState(
    val sessionId: String = "",
    val phase: ReportPhase = ReportPhase.LISTENING,
    /** The server said QUOTA_EXHAUSTED: the talk waits, saved, for the next quota day (S17, US-060). */
    val quotaRefused: Boolean = false,
    val meta: MetaUi? = null,
    val credit: CreditUi? = null,
    val stutterFriendly: Boolean = false,
    val noisy: Boolean = false,
    val tiles: List<MetricTileUi> = emptyList(),
    val transcript: TranscriptUi? = null,
    val summary: String? = null,
    val fillersDown: Boolean = false,
    val glow: GlowUi? = null,
    val grow: GrowUi? = null,
    val better: List<BetterUi> = emptyList(),
    val collectedWordIds: Set<String> = emptySet(),
    val savedLines: Set<Int> = emptySet(),
    val showRating: Boolean = false,
    val rating: Int? = null,
    val isRetake: Boolean = false,
    val retakeCapS: Long? = null,
    val promptId: String = "",
    val promptText: String = "",
    val speechMs: Long = 0,
    val manualRetryFailed: Boolean = false,
    val retrying: Boolean = false,
)

/** Pure mapping from Room rows to the screen state, unit-testable without Compose. */
object ReportMapper {
    const val COLLAPSED_PARAGRAPHS = 3
    private const val PARAGRAPH_GAP_MS = 1_500L
    private const val PARAGRAPH_MAX_WORDS = 60

    fun map(
        d: TalkDetails,
        averages: MetricAverages?,
        promptText: String,
        stutterFriendly: Boolean,
        tierCapS: Int,
        manualRetryFailed: Boolean = false,
    ): ReportUiState {
        val s = d.session
        val lang = d.lang
        val metrics: Metrics? = d.words?.takeIf { it.isNotEmpty() }?.let { MetricsCalculator.compute(it, lang, stutterFriendly) }
        val safety = d.feedback?.feedback?.safety ?: Safety.NONE
        val phase = phase(s.status, s.errorCode, d.words != null, safety)
        val feedback = d.feedback?.feedback
        val words = d.words.orEmpty()

        val showFallback = phase == ReportPhase.READY || (phase == ReportPhase.FEEDBACK_FAILED && manualRetryFailed)
        val glow = feedback?.glow?.let { GlowUi(it.quote, it.note) }
            ?: if (showFallback && metrics != null) GlowUi(null, FeedbackFallback.glow(metrics, averages, s.durationMs.toLong(), lang).note) else null
        val grow = feedback?.grow?.let { GrowUi(it.quote, it.why, it.how) }
            ?: if (showFallback && metrics != null) FeedbackFallback.grow(metrics, lang).let { GrowUi(null, it.why, it.how) } else null

        val glowRange = feedback?.glow?.let { it.start..it.end }
        val growRange = feedback?.grow?.let { it.start..it.end }

        return ReportUiState(
            sessionId = s.id,
            phase = phase,
            quotaRefused = s.status == TalkStatus.QUEUED && s.errorCode == "QUOTA_EXHAUSTED",
            meta = MetaUi(lang, Track.fromKey(s.track), s.durationMs.toLong(), Instant.ofEpochMilli(s.createdAt), ZoneId.of(s.zoneId)),
            credit = credit(d.credit, s.speechMs.toLong()),
            stutterFriendly = stutterFriendly,
            noisy = s.snrDb > 0 && s.snrDb < EnergyVad.NOISY_SNR_DB,
            tiles = metrics?.let { tiles(it, averages, lang, stutterFriendly) }.orEmpty(),
            transcript = metrics?.let { transcript(words, it, glowRange, growRange, stutterFriendly) },
            summary = feedback?.summary?.takeIf { it.isNotBlank() },
            fillersDown = metrics != null && averages != null && metrics.fillersPerMin < averages.fillersPerMin,
            glow = glow,
            grow = grow,
            better = feedback?.betterVersions.orEmpty().mapIndexed { i, b -> BetterUi(i, b.quote, b.rewrite, b.upgradedWords) },
            showRating = phase == ReportPhase.READY && ratingEligible(s.id),
            rating = d.feedback?.rating,
            isRetake = s.kind == TalkKind.RETAKE.key,
            retakeCapS = SayItAgain.capSeconds(s.durationMs.toLong(), tierCapS),
            promptId = s.promptId,
            promptText = promptText,
            speechMs = s.speechMs.toLong(),
            manualRetryFailed = manualRetryFailed,
        )
    }

    fun phase(status: String, errorCode: String?, hasTranscript: Boolean, safety: Safety): ReportPhase = when (status) {
        TalkStatus.QUEUED -> if (errorCode == "BUSY") ReportPhase.BUSY else ReportPhase.QUEUED
        TalkStatus.FAILED -> if (errorCode == "TOO_LITTLE_SPEECH") ReportPhase.TOO_LITTLE else ReportPhase.STT_FAILED
        TalkStatus.TRANSCRIBED -> ReportPhase.METRICS_READY
        TalkStatus.FEEDBACK_FAILED -> ReportPhase.FEEDBACK_FAILED
        TalkStatus.FEEDBACK_READY -> if (safety == Safety.SELF_HARM) ReportPhase.HELPLINE else ReportPhase.READY
        TalkStatus.DISCARDED -> ReportPhase.GONE
        TalkStatus.RECORDED -> if (errorCode == TalkCodes.RECOVERED) ReportPhase.QUEUED else ReportPhase.LISTENING
        else -> if (hasTranscript) ReportPhase.METRICS_READY else ReportPhase.LISTENING
    }

    /** "Was this useful?" shows in 1 of 3 sessions (US-016), stable per talk. */
    fun ratingEligible(sessionId: String): Boolean = abs(sessionId.hashCode() % 3) == 0

    private fun credit(credit: Credit?, speechMs: Long): CreditUi? = when {
        credit != null -> CreditUi(credit.streak, credit.weeklyGoalMet)
        speechMs in 10_000 until 30_000 -> CreditUi(null, false)
        else -> null
    }

    fun tiles(m: Metrics, avg: MetricAverages?, lang: Lang, stutter: Boolean): List<MetricTileUi> {
        val (low, high) = if (lang == Lang.HI) 100.0 to 150.0 else 110.0 to 160.0
        val pace = MetricTileUi(
            kind = MetricKind.PACE,
            value = m.wpm.toInt().toString(),
            unit = MetricUnit.WPM,
            bandPosition = bandPosition(m.wpm, low, high),
            delta = MetricDelta.Band(m.wpmBand, provisional = lang == Lang.HI),
            good = m.wpmBand == WpmBand.COMFORTABLE,
        )
        val fillers = MetricTileUi(
            kind = MetricKind.FILLERS, value = one(m.fillersPerMin), unit = MetricUnit.PER_MIN,
            delta = avg?.let { lowerIsBetter(m.fillersPerMin, it.fillersPerMin, ::one) },
            good = avg != null && better(m.fillersPerMin, avg.fillersPerMin),
        )
        val avgMattr = avg?.mattr
        val variety = when (val mattr = m.mattr) {
            null -> MetricTileUi(MetricKind.VARIETY, locked = true, wide = !stutter)
            else -> MetricTileUi(
                kind = MetricKind.VARIETY, value = two(mattr), wide = !stutter,
                delta = when {
                    lang == Lang.HI -> MetricDelta.HindiProvisional
                    avgMattr == null -> null
                    mattr >= avgMattr -> MetricDelta.VarietyUp(two(avgMattr))
                    else -> MetricDelta.VarietyDown(two(avgMattr))
                },
                good = lang != Lang.HI && avgMattr != null && mattr > avgMattr,
            )
        }
        if (stutter) {
            return listOf(pace, variety, MetricTileUi(MetricKind.REPEATS, off = true), MetricTileUi(MetricKind.PAUSES, off = true))
        }
        val pausesValue = m.longPausesPerMin ?: 0.0
        val pauses = MetricTileUi(
            kind = MetricKind.PAUSES, value = one(pausesValue), unit = MetricUnit.PER_MIN,
            delta = avg?.longPausesPerMin?.let { lowerIsBetter(pausesValue, it, ::one) },
            good = avg?.longPausesPerMin?.let { better(pausesValue, it) } == true,
        )
        val reps = (m.repetitions ?: 0).toDouble()
        val repeats = MetricTileUi(
            kind = MetricKind.REPEATS, value = reps.toInt().toString(),
            delta = avg?.repetitions?.let { lowerIsBetter(reps, it) { v -> v.toInt().toString() } },
            good = avg?.repetitions?.let { better(reps, it) } == true,
        )
        return listOf(pace, fillers, pauses, repeats, variety)
    }

    /** Within 5% of the average reads as "the same". */
    private fun lowerIsBetter(value: Double, avg: Double, fmt: (Double) -> String): MetricDelta = when {
        avg <= 0.0 && value <= 0.0 -> MetricDelta.Same
        abs(value - avg) <= maxOf(avg, 0.0001) * 0.05 -> MetricDelta.Same
        value < avg -> MetricDelta.Down(fmt(avg))
        else -> MetricDelta.Up(fmt(avg))
    }

    private fun better(value: Double, avg: Double) = value < avg && abs(value - avg) > avg * 0.05

    /** Slow occupies 0–25% of the band, comfortable 25–66.7%, fast 66.7–100% (base.css `.band`). */
    fun bandPosition(wpm: Double, low: Double, high: Double): Float {
        val p = when {
            wpm < low -> 0.25 * (wpm / low)
            wpm <= high -> 0.25 + (wpm - low) / (high - low) * (0.667 - 0.25)
            else -> 0.667 + ((wpm - high) / high).coerceAtMost(1.0) * 0.333
        }
        return p.toFloat().coerceIn(0.02f, 0.98f)
    }

    fun transcript(words: List<Word>, m: Metrics, glow: IntRange?, grow: IntRange?, stutter: Boolean): TranscriptUi {
        val tokens = words.mapIndexed { i, w ->
            TokenUi(
                text = w.w,
                filler = i in m.fillerIndices,
                repeat = !stutter && i in m.repeatIndices,
                unsure = w.conf < MetricsCalculator.LOW_CONF,
                glow = glow != null && i in glow,
                grow = grow != null && i in grow,
            )
        }
        val paragraphs = mutableListOf<IntRange>()
        var start = 0
        for (i in 1 until words.size) {
            val gap = words[i].startMs - words[i - 1].endMs
            val long = i - start >= PARAGRAPH_MAX_WORDS && words[i - 1].w.trimEnd().lastOrNull() in SENTENCE_END
            if (gap >= PARAGRAPH_GAP_MS || long) {
                paragraphs += start until i
                start = i
            }
        }
        if (words.isNotEmpty()) paragraphs += start until words.size
        return TranscriptUi(tokens, paragraphs, paragraphs.map { words[it.first].startMs }, paragraphs.map { words[it.last].endMs })
    }

    private val SENTENCE_END = setOf('.', '?', '!', '।')

    fun one(v: Double): String = "%.1f".format(java.util.Locale.US, v)
    fun two(v: Double): String = "%.2f".format(java.util.Locale.US, v)
}
