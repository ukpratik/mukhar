package io.github.ukpratik.mukhar.domain.coach

import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.feedback.FeedbackFallback
import io.github.ukpratik.mukhar.domain.feedback.Glow
import io.github.ukpratik.mukhar.domain.feedback.Grow
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.feedback.WordUpgrade
import io.github.ukpratik.mukhar.domain.metrics.MetricAverages
import io.github.ukpratik.mukhar.domain.metrics.Metrics
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import io.github.ukpratik.mukhar.domain.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The offline coach (ADR-008 §2, `algo_version = 2`): deterministic notes from the talk's own metrics, compared with
 * the user's last-5 average in the same language, plus overused words and curated word upgrades. No grammar, no
 * rewrites, nothing invented: every quote is a real token of the transcript. Copy is in the talk's language (D-009).
 */
object RuleCoach {
    const val MODEL = "rule-coach"
    const val VERSION = "rule-coach-v1"

    const val MIN_COUNT = 3
    const val MIN_SHARE = 0.02
    const val CRUTCH_MIN_COUNT = 2
    const val MAX_UPGRADES = 3
    const val IMPROVED = 0.9

    /** Words people lean on whatever the count share (ADR-008: "crutch" list), normalised. */
    val CRUTCH: Map<Lang, Set<String>> = mapOf(
        Lang.EN to setOf("very", "really", "basically", "actually", "nice", "good", "thing", "things", "stuff", "literally", "totally"),
        Lang.HI to setOf("बहुत", "अच्छा", "अच्छी", "चीज", "चीजें", "मतलब", "बढिया", "एकदम"),
    )

    data class Input(
        val words: List<Word>,
        val lang: Lang,
        val metrics: Metrics,
        val average: MetricAverages?,
        val durationMs: Long,
        val stutterFriendly: Boolean,
        val synonyms: SynonymBook,
        val notes: List<AnalysisNote> = emptyList(),
    )

    data class Overused(val word: String, val count: Int, val firstIndex: Int)

    fun coach(input: Input): Feedback {
        val overused = overusedWords(input.words, input.lang, input.metrics.fillerIndices)
        val upgrades = overused.mapNotNull { o ->
            input.synonyms.lookup(o.word)?.takeIf { it.alternatives.isNotEmpty() }?.let { e ->
                WordUpgrade(o.word, o.count, o.firstIndex, e.alternatives.take(MAX_ALTERNATIVES))
            }
        }.take(MAX_UPGRADES)
        val glow = glow(input)
        val grow = grow(input, overused, upgrades)
        return Feedback(
            summary = summary(input.lang, glow.second, grow.issue),
            glow = glow.first,
            grow = grow,
            betterVersions = emptyList(),
            mistakes = emptyList(),
            safety = Safety.NONE,
            wordUpgrades = upgrades,
            notes = input.notes,
        )
    }

    /**
     * Content words said too often: not a stop word or a filler, recognised with conf ≥ 0.6, said ≥ 3 times and
     * ≥ 2% of the words; crutch words count from 2. Most frequent first.
     */
    fun overusedWords(words: List<Word>, lang: Lang, fillerIndices: Set<Int>): List<Overused> {
        val stop = StopWords.of(lang)
        val crutch = CRUTCH.getValue(lang)
        val counts = LinkedHashMap<String, Int>()
        val first = HashMap<String, Int>()
        val display = HashMap<String, String>()
        var total = 0
        words.forEachIndexed { i, w ->
            if (i in fillerIndices) return@forEachIndexed
            val n = Normalizer.norm(w.w)
            if (n.isEmpty()) return@forEachIndexed
            total++
            if (w.conf < MetricsCalculator.LOW_CONF) return@forEachIndexed
            if (n in stop && n !in crutch) return@forEachIndexed
            if (n.length < 2 && n !in crutch) return@forEachIndexed
            counts[n] = (counts[n] ?: 0) + 1
            first.putIfAbsent(n, i)
            display.putIfAbsent(n, w.w.trim().trim { !it.isLetterOrDigit() && !isDevanagari(it) })
        }
        if (total == 0) return emptyList()
        return counts.filter { (n, c) ->
            if (n in crutch) c >= CRUTCH_MIN_COUNT else c >= MIN_COUNT && c >= total * MIN_SHARE
        }.map { (n, c) -> Overused(display[n]?.ifEmpty { n } ?: n, c, first.getValue(n)) }
            .sortedWith(compareByDescending<Overused> { it.count }.thenBy { it.firstIndex })
    }

    private enum class GlowKind { FILLERS, PAUSES, LONGER, VARIETY, PACE, SHOWED_UP }

    private fun glow(input: Input): Pair<Glow, GlowKind> {
        val m = input.metrics
        val avg = input.average
        val c = copy(input.lang)
        // The biggest improvement against the user's own average wins (each needs ≥ 10%).
        val candidates = mutableListOf<Triple<GlowKind, Double, String>>()
        if (avg != null) {
            if (avg.fillersPerMin > 0 && m.fillersPerMin <= avg.fillersPerMin * IMPROVED) {
                candidates += Triple(GlowKind.FILLERS, 1 - m.fillersPerMin / avg.fillersPerMin, c.fillersDown.f(m.fillersPerMin, avg.fillersPerMin))
            }
            val pauses = m.longPausesPerMin
            val avgPauses = avg.longPausesPerMin
            if (!input.stutterFriendly && pauses != null && avgPauses != null && avgPauses > 0 && pauses <= avgPauses * IMPROVED) {
                candidates += Triple(GlowKind.PAUSES, 1 - pauses / avgPauses, c.pausesDown.f(pauses, avgPauses))
            }
            val span = avg.spanMs
            if (span != null && span > 0 && m.spanMs >= span / IMPROVED) {
                candidates += Triple(GlowKind.LONGER, m.spanMs / span - 1, c.longer.f(FeedbackFallback.clock(m.spanMs), FeedbackFallback.clock(span.toLong())))
            }
            val mattr = m.mattr
            val avgMattr = avg.mattr
            if (mattr != null && avgMattr != null && avgMattr > 0 && mattr >= avgMattr / IMPROVED) {
                candidates += Triple(GlowKind.VARIETY, mattr / avgMattr - 1, c.variety.f(mattr, avgMattr))
            }
        }
        val best = candidates.maxByOrNull { it.second }
        val (kind, note) = when {
            best != null -> best.first to best.third
            m.wpmBand == WpmBand.COMFORTABLE && m.wpm > 0 -> GlowKind.PACE to c.pace.f(m.wpm.roundToInt())
            else -> GlowKind.SHOWED_UP to c.showedUp.f(FeedbackFallback.clock(input.durationMs))
        }
        return Glow(Feedback.NO_SPAN, Feedback.NO_SPAN, "", note) to kind
    }

    /** The worst band first: fillers > 5/min → pace → long pauses > 2/min → an overused word → a structure tip. */
    private fun grow(input: Input, overused: List<Overused>, upgrades: List<WordUpgrade>): Grow {
        val m = input.metrics
        val c = copy(input.lang)
        val pauses = m.longPausesPerMin
        val fluency = !input.stutterFriendly
        val none = Feedback.NO_SPAN
        return when {
            m.fillersPerMin > FeedbackFallback.FILLER_TARGET_PER_MIN ->
                Grow(none, none, "", GrowIssue.FILLER, c.fillersWhy.f(m.fillersPerMin), c.fillersHow)
            fluency && m.wpmBand == WpmBand.FAST -> Grow(none, none, "", GrowIssue.PACE, c.fastWhy.f(m.wpm.roundToInt()), c.fastHow)
            fluency && m.wpmBand == WpmBand.SLOW && m.wpm > 0 ->
                Grow(none, none, "", GrowIssue.PACE, c.slowWhy.f(m.wpm.roundToInt()), c.slowHow)
            fluency && pauses != null && pauses > FeedbackFallback.PAUSES_HIGH_PER_MIN ->
                Grow(none, none, "", GrowIssue.PACE, c.pausesWhy.f(pauses), c.pausesHow)
            overused.isNotEmpty() -> {
                val o = overused.first()
                val alts = upgrades.firstOrNull { Normalizer.norm(it.word) == Normalizer.norm(o.word) }?.alternatives.orEmpty()
                val how = if (alts.isEmpty()) c.overusedHowPlain else c.overusedHow.f(alts.take(2).joinToString(c.or) { "\"${it.word}\"" })
                // Quote the first time it was said: a real token, so the transcript can highlight it.
                Grow(o.firstIndex, o.firstIndex, input.words[o.firstIndex].w, GrowIssue.WEAK_WORD, c.overusedWhy.f(o.word, o.count), how)
            }
            else -> Grow(none, none, "", GrowIssue.STRUCTURE, c.structureWhy, c.structureHow)
        }
    }

    private fun summary(lang: Lang, glow: GlowKind, grow: GrowIssue): String {
        val c = copy(lang)
        val first = when (glow) {
            GlowKind.FILLERS -> c.sumFillers
            GlowKind.PAUSES -> c.sumPauses
            GlowKind.LONGER -> c.sumLonger
            GlowKind.VARIETY -> c.sumVariety
            GlowKind.PACE -> c.sumPace
            GlowKind.SHOWED_UP -> c.sumShowedUp
        }
        val next = when (grow) {
            GrowIssue.FILLER -> c.nextFillers
            GrowIssue.PACE -> c.nextPace
            GrowIssue.WEAK_WORD -> c.nextWords
            else -> c.nextStructure
        }
        return "$first $next"
    }

    private const val MAX_ALTERNATIVES = 3

    private fun String.f(vararg args: Any): String = format(Locale.US, *args)

    private fun isDevanagari(ch: Char) = ch in 'ऀ'..'ॿ'

    private class Copy(
        val fillersDown: String, val pausesDown: String, val longer: String, val variety: String, val pace: String,
        val showedUp: String, val fillersWhy: String, val fillersHow: String, val fastWhy: String, val fastHow: String,
        val slowWhy: String, val slowHow: String, val pausesWhy: String, val pausesHow: String, val overusedWhy: String,
        val overusedHow: String, val overusedHowPlain: String, val or: String, val structureWhy: String,
        val structureHow: String, val sumFillers: String, val sumPauses: String, val sumLonger: String,
        val sumVariety: String, val sumPace: String, val sumShowedUp: String, val nextFillers: String,
        val nextPace: String, val nextWords: String, val nextStructure: String,
    )

    private val EN = Copy(
        fillersDown = "Fewer fillers than your average: %.1f a minute, down from %.1f.",
        pausesDown = "Fewer long pauses than your average: %.1f a minute, down from %.1f.",
        longer = "You kept going for %s, longer than your usual %s.",
        variety = "You used a wider range of words than usual (%.2f, your average is %.2f).",
        pace = "Your pace stayed comfortable at %d wpm.",
        showedUp = "You spoke for %s — showing up is the hard part.",
        fillersWhy = "Fillers (%.1f a minute) make a clear point sound less sure.",
        fillersHow = "Pause instead of saying \"um\". A short silence sounds confident.",
        fastWhy = "At %d wpm, listeners can miss your key points.",
        fastHow = "Slow down at the end of each sentence and take a breath.",
        slowWhy = "At %d wpm, the talk can feel hesitant.",
        slowHow = "Plan your next point while you finish the current one, then keep going.",
        pausesWhy = "Long pauses (%.1f a minute) break the flow.",
        pausesHow = "Before you start, note three points so you always know what comes next.",
        overusedWhy = "You said \"%s\" %d times, so it starts to lose its punch.",
        overusedHow = "Swap in %s now and then. More ideas are under \"Try instead\".",
        overusedHowPlain = "Say it once, then find another way to make the same point.",
        or = " or ",
        structureWhy = "A clear shape helps people follow you.",
        structureHow = "Try one point, one reason, one example, then repeat your point.",
        sumFillers = "Fewer fillers than usual.",
        sumPauses = "A smoother flow than usual.",
        sumLonger = "You spoke for longer than usual.",
        sumVariety = "A richer mix of words than usual.",
        sumPace = "A comfortable pace.",
        sumShowedUp = "You showed up and spoke.",
        nextFillers = "Next: fewer fillers.",
        nextPace = "Next: steady your pace.",
        nextWords = "Next: vary your words.",
        nextStructure = "Next: give your talk a clear shape.",
    )

    private val HI = Copy(
        fillersDown = "आपके filler औसत से कम रहे: %.1f प्रति मिनट, पहले %.1f थे।",
        pausesDown = "लंबे ठहराव औसत से कम रहे: %.1f प्रति मिनट, पहले %.1f थे।",
        longer = "आप %s तक बोलते रहे, जो आपके आम %s से ज़्यादा है।",
        variety = "आपने आम दिनों से ज़्यादा अलग-अलग शब्द इस्तेमाल किए (%.2f, आपका औसत %.2f है)।",
        pace = "आपकी रफ़्तार %d शब्द प्रति मिनट पर आरामदायक रही।",
        showedUp = "आपने %s बोला — शुरुआत करना ही सबसे मुश्किल काम है।",
        fillersWhy = "बार-बार filler (%.1f प्रति मिनट) आने से आपकी बात कम भरोसेमंद लगती है।",
        fillersHow = "\"उम्म\" या \"मतलब\" की जगह एक पल रुकिए। छोटा-सा ठहराव भरोसेमंद लगता है।",
        fastWhy = "%d शब्द प्रति मिनट पर सुनने वाले आपकी ज़रूरी बातें छोड़ सकते हैं।",
        fastHow = "हर वाक्य के आख़िर में थोड़ा धीमे होइए और साँस लीजिए।",
        slowWhy = "%d शब्द प्रति मिनट पर बात थोड़ी झिझक भरी लग सकती है।",
        slowHow = "एक बात ख़त्म करते हुए ही अगली बात सोच लीजिए, फिर बोलते रहिए।",
        pausesWhy = "लंबे ठहराव (%.1f प्रति मिनट) बात का बहाव तोड़ते हैं।",
        pausesHow = "शुरू करने से पहले तीन मुख्य बातें सोच लीजिए, ताकि आगे क्या कहना है पता रहे।",
        overusedWhy = "आपने \"%s\" %d बार कहा, इसलिए इसका असर कम होने लगता है।",
        overusedHow = "बीच-बीच में %s कहकर देखिए। और विकल्प \"इसकी जगह आज़माएँ\" में हैं।",
        overusedHowPlain = "इसे एक बार कहिए, फिर वही बात किसी और तरीक़े से कहिए।",
        or = " या ",
        structureWhy = "साफ़ ढाँचा होने से लोग आपकी बात आसानी से समझते हैं।",
        structureHow = "पहले अपनी बात, फिर वजह, फिर एक उदाहरण, और आख़िर में अपनी बात दोहराइए।",
        sumFillers = "आम दिनों से कम filler।",
        sumPauses = "बात का बहाव आम दिनों से बेहतर रहा।",
        sumLonger = "आप आम दिनों से ज़्यादा देर बोले।",
        sumVariety = "शब्दों में आम दिनों से ज़्यादा विविधता।",
        sumPace = "रफ़्तार आरामदायक रही।",
        sumShowedUp = "आपने हिम्मत करके बोला।",
        nextFillers = "अगला क़दम: filler कम करना।",
        nextPace = "अगला क़दम: रफ़्तार एक-सी रखना।",
        nextWords = "अगला क़दम: शब्दों में विविधता लाना।",
        nextStructure = "अगला क़दम: बात को साफ़ ढाँचा देना।",
    )

    private fun copy(lang: Lang): Copy = if (lang == Lang.HI) HI else EN
}
