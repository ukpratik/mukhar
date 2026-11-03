package io.github.ukpratik.mukhar.domain.feedback

import io.github.ukpratik.mukhar.domain.metrics.MetricAverages
import io.github.ukpratik.mukhar.domain.metrics.Metrics
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Safe metrics-based Glow and Grow (LLD §4.4 "Fallbacks"), used when grounding drops an item or the coach's notes
 * can't be fetched. Written in the talk's language (D-009); template strings are content, not UI chrome.
 */
object FeedbackFallback {
    const val FILLER_TARGET_PER_MIN = 5.0
    const val PAUSES_HIGH_PER_MIN = 2.0

    data class FallbackGlow(val note: String)
    data class FallbackGrow(val issue: GrowIssue, val why: String, val how: String)

    fun glow(metrics: Metrics, average: MetricAverages?, durationMs: Long, lang: Lang): FallbackGlow {
        val copy = copy(lang)
        if (average != null && average.fillersPerMin > 0 && metrics.fillersPerMin <= average.fillersPerMin * 0.9) {
            return FallbackGlow(copy.fillersDown.format(Locale.US, metrics.fillersPerMin, average.fillersPerMin))
        }
        if (metrics.wpmBand == WpmBand.COMFORTABLE && metrics.wpm > 0) {
            return FallbackGlow(copy.paceComfortable.format(Locale.US, metrics.wpm.roundToInt()))
        }
        return FallbackGlow(copy.showedUp.format(Locale.US, clock(durationMs)))
    }

    /** The worst-band metric with a fixed tip. Stutter-friendly talks never get a pause tip. */
    fun grow(metrics: Metrics, lang: Lang): FallbackGrow {
        val c = copy(lang)
        val pauses = metrics.longPausesPerMin
        return when {
            metrics.fillersPerMin > FILLER_TARGET_PER_MIN ->
                FallbackGrow(GrowIssue.FILLER, c.fillersWhy.format(Locale.US, metrics.fillersPerMin), c.fillersHow)
            metrics.wpmBand == WpmBand.FAST ->
                FallbackGrow(GrowIssue.PACE, c.fastWhy.format(Locale.US, metrics.wpm.roundToInt()), c.fastHow)
            metrics.wpmBand == WpmBand.SLOW && metrics.wpm > 0 ->
                FallbackGrow(GrowIssue.PACE, c.slowWhy.format(Locale.US, metrics.wpm.roundToInt()), c.slowHow)
            pauses != null && pauses > PAUSES_HIGH_PER_MIN ->
                FallbackGrow(GrowIssue.PACE, c.pausesWhy.format(Locale.US, pauses), c.pausesHow)
            else -> FallbackGrow(GrowIssue.STRUCTURE, c.structureWhy, c.structureHow)
        }
    }

    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, s / 60, s % 60)
    }

    private class Copy(
        val fillersDown: String,
        val paceComfortable: String,
        val showedUp: String,
        val fillersWhy: String,
        val fillersHow: String,
        val fastWhy: String,
        val fastHow: String,
        val slowWhy: String,
        val slowHow: String,
        val pausesWhy: String,
        val pausesHow: String,
        val structureWhy: String,
        val structureHow: String,
    )

    private val EN = Copy(
        fillersDown = "Fewer fillers than your average: %.1f a minute, down from %.1f.",
        paceComfortable = "Your pace stayed comfortable at %d wpm.",
        showedUp = "You spoke for %s — showing up is the hard part.",
        fillersWhy = "Fillers (%.1f a minute) make a clear point sound less sure.",
        fillersHow = "Pause instead of saying \"um\". A short silence sounds confident.",
        fastWhy = "At %d wpm, listeners can miss your key points.",
        fastHow = "Slow down at the end of each sentence and take a breath.",
        slowWhy = "At %d wpm, the talk can feel hesitant.",
        slowHow = "Plan your next point while you finish the current one, then keep going.",
        pausesWhy = "Long pauses (%.1f a minute) break the flow.",
        pausesHow = "Before you start, note three points so you always know what comes next.",
        structureWhy = "A clear shape helps people follow you.",
        structureHow = "Try one point, one reason, one example, then repeat your point.",
    )

    private val HI = Copy(
        fillersDown = "आपके filler औसत से कम रहे: %.1f प्रति मिनट, पहले %.1f थे।",
        paceComfortable = "आपकी रफ़्तार %d शब्द प्रति मिनट पर आरामदायक रही।",
        showedUp = "आपने %s बोला — शुरुआत करना ही सबसे मुश्किल काम है।",
        fillersWhy = "बार-बार filler (%.1f प्रति मिनट) आने से आपकी बात कम भरोसेमंद लगती है।",
        fillersHow = "\"उम्म\" या \"मतलब\" की जगह एक पल रुकिए। छोटा-सा ठहराव भरोसेमंद लगता है।",
        fastWhy = "%d शब्द प्रति मिनट पर सुनने वाले आपकी ज़रूरी बातें छोड़ सकते हैं।",
        fastHow = "हर वाक्य के आख़िर में थोड़ा धीमे होइए और साँस लीजिए।",
        slowWhy = "%d शब्द प्रति मिनट पर बात थोड़ी झिझक भरी लग सकती है।",
        slowHow = "एक बात ख़त्म करते हुए ही अगली बात सोच लीजिए, फिर बोलते रहिए।",
        pausesWhy = "लंबे ठहराव (%.1f प्रति मिनट) बात का बहाव तोड़ते हैं।",
        pausesHow = "शुरू करने से पहले तीन मुख्य बातें सोच लीजिए, ताकि आगे क्या कहना है पता रहे।",
        structureWhy = "साफ़ ढाँचा होने से लोग आपकी बात आसानी से समझते हैं।",
        structureHow = "पहले अपनी बात, फिर वजह, फिर एक उदाहरण, और आख़िर में अपनी बात दोहराइए।",
    )

    private fun copy(lang: Lang): Copy = if (lang == Lang.HI) HI else EN
}
