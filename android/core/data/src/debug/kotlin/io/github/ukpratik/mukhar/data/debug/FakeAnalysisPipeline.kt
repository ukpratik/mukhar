package io.github.ukpratik.mukhar.data.debug

import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.Stage
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.FeedbackRequest
import io.github.ukpratik.mukhar.data.work.FeedbackResult
import io.github.ukpratik.mukhar.data.work.ReportRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckResult
import io.github.ukpratik.mukhar.data.work.TranscribeRequest
import io.github.ukpratik.mukhar.data.work.TranscribeResult
import io.github.ukpratik.mukhar.domain.feedback.BetterVersion
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.feedback.Glow
import io.github.ukpratik.mukhar.domain.feedback.Grow
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.Mistake
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import io.github.ukpratik.mukhar.domain.text.Normalizer
import io.github.ukpratik.mukhar.domain.words.TargetWordMatcher
import kotlinx.coroutines.delay
import java.io.File

/**
 * Debug-only canned pipeline (build-plan "Debug hooks"): no network, no cost. English and Hindi transcripts with
 * word timestamps and confidences, and grounded coach's notes whose token-index spans are computed from the canned
 * transcript (plus one deliberately ungrounded item that the client validator must drop). The debug menu sets a
 * delay and failure modes (STT fail, LLM fail, busy, offline, self-harm cue).
 */
class FakeAnalysisPipeline(private val controls: DebugControls) : AnalysisPipeline {

    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> {
        val state = controls.state.value
        delay(state.delayMs)
        failure(state.mode, Stage.STT)?.let { return Outcome.Err(it) }
        val words = CannedTalks.words(request.lang, retake = request.kind == "retake")
        return Outcome.Ok(TranscribeResult("srv-${request.clientSessionId}", "fake", "canned-v1", request.durationMs, words))
    }

    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> {
        val state = controls.state.value
        delay(state.delayMs)
        failure(state.mode, Stage.LLM)?.let { return Outcome.Err(it) }
        val tokens = request.words.map { it.w }
        val feedback = if (state.mode == FakeMode.SELF_HARM) {
            Feedback("", null, null, emptyList(), emptyList(), Safety.SELF_HARM)
        } else {
            CannedTalks.feedback(request.lang, tokens)
        }
        return Outcome.Ok(FeedbackResult("fb-2026-11-01a", "fake-haiku", feedback, 0, fallbackGlow = false, fallbackGrow = false))
    }

    override suspend fun report(request: ReportRequest): Outcome<Unit> {
        val state = controls.state.value
        delay(state.delayMs / 3)
        return if (state.mode == FakeMode.OFFLINE) Outcome.Err(AppError.Offline) else Outcome.Ok(Unit)
    }

    private var checks = 0

    /**
     * Canned sentence checks (US-023), cycling ✅ → almost → ❌ so every S13 state can be demoed. The canned
     * transcript is matched with the same fuzzy rule as the Worker, so `word_found` is honest.
     */
    override suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult> {
        val state = controls.state.value
        delay(state.delayMs.coerceAtMost(2_000))
        failure(state.mode, Stage.LLM)?.let { return Outcome.Err(it) }
        return Outcome.Ok(CannedChecks.answer(request.lang, request.targetWord, checks++ % 3))
    }

    private fun failure(mode: FakeMode, stage: Stage): AppError? = when (mode) {
        FakeMode.STT_FAIL -> if (stage == Stage.STT) AppError.Upstream(Stage.STT, "STT_FAILED") else null
        FakeMode.LLM_FAIL -> if (stage == Stage.LLM) AppError.Upstream(Stage.LLM, "LLM_FAILED") else null
        FakeMode.BUSY -> AppError.Busy(null)
        FakeMode.OFFLINE -> AppError.Offline
        FakeMode.NONE, FakeMode.SELF_HARM -> null
    }
}

/** Canned talks and notes, shared with unit tests through the debug source set. */
object CannedTalks {
    // ¶ marks a long pause (a paragraph break in the transcript); ~ marks a low-confidence word.
    private const val EN = "So last quarter I led the migration of our billing dashboard to the new ~Zuora platform. " +
        "Um, the main challenge was that the old system had, uh, almost eight years of data and nobody was fully " +
        "knowing how the reports were calculated. ¶ What I did was, I sat with the finance team every Tuesday and we " +
        "mapped each report one by one. That really helped, because we found three reports that were giving wrong " +
        "totals since 2019. ¶ Basically, the project, you know, the project went live two weeks before deadline. If I " +
        "do it again, I will involve the finance team from day one itself, not after the design was done, because, " +
        "um, that would have saved us a lot of rework. My manager later asked me to share the same approach with two " +
        "other teams."

    private const val EN_RETAKE = "Last quarter I led the migration of our billing dashboard to the new ~Zuora " +
        "platform. The old system had almost eight years of data and no one could explain how the reports were " +
        "calculated. ¶ I sat with the finance team every Tuesday and we mapped each report one by one. We caught " +
        "three reports that had been giving wrong totals since 2019. ¶ We went live two weeks early. Next time, um, " +
        "I would involve finance from day one, because that would have saved us a lot of rework."

    private const val HI = "मेरे शहर नागपुर में सबसे बड़ी problem है public transport की। उम्म, मतलब, metro तो आ " +
        "गई है, लेकिन last mile connectivity अभी भी बहुत कमज़ोर है। ¶ मैं रोज़ office जाता हूँ तो station से घर तक " +
        "auto मिलना मुश्किल होता है। मतलब, कभी-कभी बीस मिनट खड़े रहना पड़ता है। ¶ अगर मुझे एक चीज़ बदलनी हो, तो मैं " +
        "हर metro station पर shared e-rickshaw की एक fixed line शुरू करूँगा, जिसका किराया metro card से ही कट जाए। " +
        "उम्म, इससे लोग अपनी गाड़ी कम निकालेंगे और traffic भी कम होगा।"

    private const val HI_RETAKE = "मेरे शहर नागपुर में सबसे बड़ी problem public transport की है। metro आ चुकी है, " +
        "पर last mile connectivity अब भी कमज़ोर है। ¶ station से घर तक auto मिलना मुश्किल होता है, कभी-कभी बीस मिनट " +
        "तक इंतज़ार करना पड़ता है। ¶ मेरा हल है हर metro station पर shared e-rickshaw की एक fixed line, जिसका किराया " +
        "metro card से ही कट जाए। उम्म, इससे traffic भी कम होगा।"

    private val FILLERS = setOf("um", "uh", "basically", "उम्म", "मतलब")

    fun words(lang: Lang, retake: Boolean = false): List<Word> {
        val text = when (lang) {
            Lang.EN -> if (retake) EN_RETAKE else EN
            Lang.HI -> if (retake) HI_RETAKE else HI
        }
        return build(text)
    }

    /** Deterministic timings: fillers sit between 450 ms pauses, commas 350 ms, sentences 700 ms, ¶ is a 1.8 s pause. */
    fun build(text: String): List<Word> {
        val out = mutableListOf<Word>()
        var t = 400L
        var pendingGap = 0L
        for (raw in text.split(' ').filter { it.isNotBlank() }) {
            if (raw == "¶") {
                pendingGap = 1_800
                continue
            }
            val low = raw.startsWith("~")
            val w = raw.removePrefix("~")
            val n = Normalizer.norm(w)
            val filler = n in FILLERS
            val gapBefore = maxOf(pendingGap, if (filler) 450L else 90L)
            val start = t + gapBefore
            val end = start + (160L + 40L * n.length).coerceAtMost(620L)
            out += Word(w, start, end, if (low) 0.42 else 0.93)
            t = end
            pendingGap = when {
                filler -> 450
                w.endsWith(".") || w.endsWith("।") || w.endsWith("?") -> 700
                w.endsWith(",") -> 350
                else -> 0
            }
        }
        return out
    }

    fun feedback(lang: Lang, tokens: List<String>): Feedback = if (lang == Lang.HI) hindi(tokens) else english(tokens)

    private fun english(t: List<String>): Feedback {
        val glowQ = "I sat with the finance team every Tuesday and we mapped each report one by one."
        val growQ = "Basically, the project, you know, the project went live two weeks before deadline."
        val bv1 = "nobody was fully knowing how the reports were calculated"
        val bv2 = "That really helped, because we found three reports that were giving wrong totals since 2019"
        val bv3 = "the project went live two weeks before deadline"
        return Feedback(
            summary = "A clear story with a strong middle. Let the ending land more cleanly.",
            glow = span(t, glowQ)?.let { (s, e) ->
                Glow(s, e, glowQ, "This is your strongest moment. A real habit (every Tuesday) and a clear method (one by one) make your process easy to picture, and you sound like someone who owns the work.")
            },
            grow = span(t, growQ)?.let { (s, e) ->
                Grow(
                    s, e, growQ, GrowIssue.FILLER,
                    why = "This is your biggest result, but two fillers and a restart blur it. People remember results that land cleanly.",
                    how = "Take a one-beat pause before the result, then say it in one short line: \"We went live two weeks early.\"",
                )
            },
            betterVersions = listOfNotNull(
                better(t, bv1, "No one could fully articulate how the reports were calculated; the logic was undocumented.",
                    UpgradedWord("articulate", "explain clearly in words", Register.FORMAL),
                    UpgradedWord("undocumented", "not written down anywhere", Register.FORMAL)),
                better(t, bv2, "Those sessions proved pivotal: we caught three reports that had been miscalculating totals since 2019.",
                    UpgradedWord("pivotal", "the thing everything turned on", Register.POWER),
                    UpgradedWord("miscalculating", "working something out wrongly", Register.FORMAL)),
                better(t, bv3, "We launched two weeks ahead of schedule.",
                    UpgradedWord("ahead of schedule", "earlier than planned", Register.POWER)),
            ),
            mistakes = listOfNotNull(
                span(t, "from day one itself")?.let { (s, e) -> Mistake(s, e, "from day one itself", "from day one") },
                // Ungrounded on purpose: it quotes a low-confidence word, so the client validator must drop it.
                span(t, "Zuora platform.")?.let { (s, e) -> Mistake(s, e, "Zuora platform.", "Zoho platform.") },
            ),
            safety = Safety.NONE,
        )
    }

    private fun hindi(t: List<String>): Feedback {
        val glowQ = "हर metro station पर shared e-rickshaw की एक fixed line शुरू करूँगा,"
        val growQ = "उम्म, मतलब, metro तो आ गई है,"
        return Feedback(
            summary = "सुझाव साफ़ और ठोस है। बस शुरुआत को थोड़ा कसिए।",
            glow = span(t, glowQ)?.let { (s, e) ->
                Glow(s, e, glowQ, "यह आपकी सबसे मज़बूत बात है। सुझाव ठोस है: कहाँ, क्या और कैसे, तीनों एक ही वाक्य में। सुनने वाला तुरंत तस्वीर बना लेता है।")
            },
            grow = span(t, growQ)?.let { (s, e) ->
                Grow(
                    s, e, growQ, GrowIssue.FILLER,
                    why = "शुरुआत में ही दो filler आने से बात की रफ़्तार टूटती है, और आपकी असली बात देर से पहुँचती है।",
                    how = "जब \"मतलब\" बोलने का मन हो, वहाँ एक पल रुकिए। रुकना \"मतलब\" से ज़्यादा भरोसेमंद लगता है।",
                )
            },
            betterVersions = listOfNotNull(
                better(t, "metro तो आ गई है, लेकिन last mile connectivity अभी भी बहुत कमज़ोर है।",
                    "metro आ चुकी है, पर last mile connectivity अब भी सबसे कमज़ोर कड़ी है।",
                    UpgradedWord("कमज़ोर कड़ी", "सबसे कमज़ोर हिस्सा", Register.POWER)),
                better(t, "कभी-कभी बीस मिनट खड़े रहना पड़ता है।", "कभी-कभी बीस मिनट तक इंतज़ार करना पड़ता है।",
                    UpgradedWord("इंतज़ार", "किसी के आने तक रुकना", Register.CASUAL)),
                better(t, "इससे लोग अपनी गाड़ी कम निकालेंगे", "इससे निजी गाड़ियों पर लोगों की निर्भरता घटेगी।",
                    UpgradedWord("निर्भरता", "किसी चीज़ पर टिके रहना", Register.FORMAL)),
            ),
            mistakes = emptyList(),
            safety = Safety.NONE,
        )
    }

    private fun better(t: List<String>, quote: String, rewrite: String, vararg words: UpgradedWord): BetterVersion? =
        span(t, quote)?.let { (s, e) -> BetterVersion(s, e, quote, rewrite, words.toList()) }

    /** The token range whose normalised text equals [quote] (the canned notes are grounded by construction). */
    fun span(tokens: List<String>, quote: String): Pair<Int, Int>? {
        val norms = tokens.map(Normalizer::norm)
        val q = Normalizer.norm(quote)
        for (s in norms.indices) {
            val sb = StringBuilder()
            for (e in s until norms.size) {
                if (norms[e].isEmpty()) continue
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(norms[e])
                if (sb.length > q.length) break
                if (sb.toString() == q) return s to e
            }
        }
        return null
    }
}

/** Canned `/check-sentence` answers: 0 = ✅ ok, 1 = almost, 2 = ❌ no. */
object CannedChecks {
    fun answer(lang: Lang, target: String, kind: Int): SentenceCheckResult {
        val (transcript, result, corrected, example, note) = when (lang) {
            Lang.EN -> when (kind) {
                0 -> listOf("In the review I used $target to explain why the launch slipped.", "ok", "", "", "Right meaning, natural grammar. That's a sentence you could say in a real meeting.")
                1 -> listOf("I will $target about the plan in tomorrow meeting.", "almost", "I will $target the plan in tomorrow's meeting.", "", "Good use of the word. Drop \"about\" and say \"tomorrow's meeting\".")
                else -> listOf("I moved the meeting to Thursday because the client was busy.", "no", "", "That client call was $target for the whole quarter.", "We didn't hear \"$target\" used with its meaning. Try copying the example's shape.")
            }
            Lang.HI -> when (kind) {
                0 -> listOf("कल की meeting में मैंने $target शब्द सही जगह इस्तेमाल किया।", "ok", "", "", "सही अर्थ, सहज वाक्य। ऐसे ही बोलिए।")
                1 -> listOf("मैं कल $target के बारे में बात करेगा।", "almost", "मैं कल $target के बारे में बात करूँगा।", "", "शब्द ठीक है, बस क्रिया का रूप बदलिए: \"करूँगा\"।")
                else -> listOf("मैंने meeting को गुरुवार कर दिया।", "no", "", "यह फ़ैसला पूरे साल के लिए $target रहा।", "\"$target\" का अर्थ वाक्य में नहीं आया। उदाहरण जैसा वाक्य बनाकर देखिए।")
            }
        }
        val found = TargetWordMatcher.found(target, transcript.split(' '))
        return SentenceCheckResult(transcript, found, result, corrected, example, note)
    }
}
