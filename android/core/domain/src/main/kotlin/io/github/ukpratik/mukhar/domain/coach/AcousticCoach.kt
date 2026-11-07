package io.github.ukpratik.mukhar.domain.coach

import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.audio.AcousticMetrics
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.feedback.FeedbackFallback
import io.github.ukpratik.mukhar.domain.feedback.Glow
import io.github.ukpratik.mukhar.domain.feedback.Grow
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.metrics.MetricAverages
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlin.math.roundToInt

/**
 * Coach's notes from the voice alone (D-048), when no transcript could be written. Only pace (estimated), pauses and
 * speaking time are known, so there's one Glow and one Grow about delivery, never about words or grammar.
 */
object AcousticCoach {
    const val MODEL = "acoustic-coach"
    const val VERSION = "acoustic-coach-v1"

    /** More long pauses per minute than this is worth a note (a 3-minute talk with 7+ long pauses). */
    const val MANY_PAUSES_PER_MIN = 2.0

    fun coach(m: AcousticMetrics, lang: Lang, average: MetricAverages?, notes: List<AnalysisNote>): Feedback {
        val c = if (lang == Lang.HI) HI else EN
        val wpm = m.estWpm.roundToInt()
        val avgPauses = average?.longPausesPerMin
        val glowNote = when {
            avgPauses != null && avgPauses > 0 && m.longPausesPerMin <= avgPauses * RuleCoach.IMPROVED ->
                c.pausesDown.format(one(m.longPausesPerMin), one(avgPauses))
            m.wpmBand == WpmBand.COMFORTABLE && wpm > 0 -> c.pace.format(wpm)
            m.longPausesPerMin < 1.0 && m.spanMs >= 30_000 -> c.flow
            else -> c.showedUp.format(FeedbackFallback.clock(m.spanMs.coerceAtLeast(m.speechMs)))
        }
        val grow = when {
            m.wpmBand == WpmBand.FAST -> Grow(Feedback.NO_SPAN, Feedback.NO_SPAN, "", GrowIssue.PACE, c.fastWhy.format(wpm), c.fastHow)
            m.longPausesPerMin > MANY_PAUSES_PER_MIN -> Grow(Feedback.NO_SPAN, Feedback.NO_SPAN, "", GrowIssue.STRUCTURE, c.pausesWhy.format(m.longPauses), c.pausesHow)
            m.wpmBand == WpmBand.SLOW && wpm > 0 -> Grow(Feedback.NO_SPAN, Feedback.NO_SPAN, "", GrowIssue.PACE, c.slowWhy.format(wpm), c.slowHow)
            else -> Grow(Feedback.NO_SPAN, Feedback.NO_SPAN, "", GrowIssue.CLARITY, c.nextWhy, c.nextHow)
        }
        return Feedback(
            summary = c.summary,
            glow = Glow(Feedback.NO_SPAN, Feedback.NO_SPAN, "", glowNote),
            grow = grow,
            betterVersions = emptyList(),
            mistakes = emptyList(),
            safety = Safety.NONE,
            notes = (listOf(AnalysisNote.AUDIO_ONLY) + notes).distinct(),
        )
    }

    private fun one(v: Double) = "%.1f".format(java.util.Locale.US, v)

    private class Copy(
        val summary: String,
        val pace: String,
        val pausesDown: String,
        val flow: String,
        val showedUp: String,
        val fastWhy: String,
        val fastHow: String,
        val slowWhy: String,
        val slowHow: String,
        val pausesWhy: String,
        val pausesHow: String,
        val nextWhy: String,
        val nextHow: String,
    )

    private val EN = Copy(
        summary = "Your voice numbers are in.",
        pace = "Your pace was about %d words a minute, right in the easy-to-follow range.",
        pausesDown = "Fewer long pauses than usual: %s a minute, down from %s.",
        flow = "You kept going with almost no long pauses. That's real fluency.",
        showedUp = "You spoke for %s. Showing up is the habit that makes the rest easier.",
        fastWhy = "You spoke fast, about %d words a minute. Listeners may miss your key points.",
        fastHow = "Stop for one breath at every full stop. Next time, aim for 130–150 words a minute.",
        slowWhy = "Your pace was slow, about %d words a minute, so the talk may feel heavy.",
        slowHow = "Before you start, decide your three points. Knowing what comes next keeps you moving.",
        pausesWhy = "There were %d long pauses (over 1.5 s), usually while finding the next idea.",
        pausesHow = "Use Point, Reason, Example, Point. When you lose the thread, say your point again and carry on.",
        nextWhy = "Your delivery sounded steady. Fillers and word choice need a transcript to check.",
        nextHow = "Try the prompt again with a pause after each idea, and listen for any \"um\" yourself.",
    )

    private val HI = Copy(
        summary = "आपकी आवाज़ के नंबर आ गए हैं।",
        pace = "आपकी रफ़्तार लगभग %d शब्द प्रति मिनट रही, जो सुनने में आसान है।",
        pausesDown = "लंबे ठहराव पहले से कम: %s प्रति मिनट, पहले %s थे।",
        flow = "आप लगभग बिना लंबे ठहराव के बोलते रहे। यही असली प्रवाह है।",
        showedUp = "आपने %s तक बोला। रोज़ बोलने की आदत ही सब आसान बनाती है।",
        fastWhy = "आप तेज़ बोले, लगभग %d शब्द प्रति मिनट। सुनने वाले आपकी मुख्य बातें चूक सकते हैं।",
        fastHow = "हर वाक्य के अंत में एक साँस लें। अगली बार 110–140 शब्द प्रति मिनट का लक्ष्य रखें।",
        slowWhy = "आपकी रफ़्तार धीमी रही, लगभग %d शब्द प्रति मिनट, इसलिए बात भारी लग सकती है।",
        slowHow = "शुरू करने से पहले अपनी तीन बातें तय कर लें। आगे क्या कहना है, पता हो तो बोलना आसान होता है।",
        pausesWhy = "%d लंबे ठहराव आए (1.5 सेकंड से ज़्यादा), अक्सर अगली बात सोचते समय।",
        pausesHow = "बात → वजह → उदाहरण → फिर से बात। धागा छूटे तो अपनी बात दोहराएँ और आगे बढ़ें।",
        nextWhy = "आपकी आवाज़ स्थिर लगी। भराव शब्द और शब्दों का चुनाव जाँचने के लिए ट्रांसक्रिप्ट चाहिए।",
        nextHow = "हर विचार के बाद एक ठहराव लेकर फिर से बोलें, और खुद \"मतलब\", \"वो\" जैसे शब्दों पर ध्यान दें।",
    )
}
