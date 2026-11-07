package io.github.ukpratik.mukhar.domain.coach

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.feedback.Alternative
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.hasSpan
import io.github.ukpratik.mukhar.domain.metrics.MetricAverages
import io.github.ukpratik.mukhar.domain.metrics.Metrics
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import org.junit.Test

/** The offline coach (ADR-008 §2): glow/grow from metrics vs own average, overused words, curated upgrades. */
class RuleCoachTest {
    private fun words(text: String, conf: Double = 0.95, gapMs: Long = 100): List<Word> {
        var t = 0L
        return text.split(" ").filter { it.isNotEmpty() }.map { w ->
            val word = Word(w, t, t + 300, conf)
            t += 300 + gapMs
            word
        }
    }

    private fun metrics(
        wpm: Double = 135.0,
        band: WpmBand = WpmBand.COMFORTABLE,
        fillers: Double = 2.0,
        pauses: Double? = 0.5,
        mattr: Double? = null,
        spanMs: Long = 60_000,
        fillerIndices: Set<Int> = emptySet(),
    ) = Metrics(wpm, band, 100, 2, fillers, 1, pauses, 0, mattr, spanMs, fillerIndices, emptySet())

    private val book = SynonymBook(
        Lang.EN,
        listOf(
            SynonymEntry(
                "good",
                listOf(
                    Alternative("excellent", "of very high quality", Register.FORMAL, "The team did an excellent job."),
                    Alternative("solid", "reliable", Register.CASUAL, "That was a solid plan."),
                    Alternative("outstanding", "clearly the best", Register.POWER, "Her work was outstanding."),
                ),
            ),
        ),
    )

    private fun input(
        words: List<Word> = words("I think the plan is clear and the team agreed on it"),
        m: Metrics = metrics(),
        avg: MetricAverages? = null,
        lang: Lang = Lang.EN,
        stutter: Boolean = false,
        synonyms: SynonymBook = book,
    ) = RuleCoach.Input(words, lang, m, avg, 60_000, stutter, synonyms)

    private fun avg(fillers: Double = 4.0, pauses: Double? = 1.0, mattr: Double? = null, span: Double? = null) =
        MetricAverages(5, 130.0, fillers, pauses, 1.0, mattr, span)

    @Test
    fun overusedNeedsThreeUsesAndTwoPercentAndSkipsStopWordsFillersAndUnclear() {
        val text = "the project went well the project had risks the project shipped and the team was happy so happy"
        val ws = words(text)
        val found = RuleCoach.overusedWords(ws, Lang.EN, emptySet())
        assertThat(found.map { it.word }).containsExactly("project")
        assertThat(found.single().count).isEqualTo(3)
        assertThat(found.single().firstIndex).isEqualTo(1)

        // Unclear words never count.
        val unclear = ws.mapIndexed { i, w -> if (w.w == "project" && i > 1) w.copy(conf = 0.4) else w }
        assertThat(RuleCoach.overusedWords(unclear, Lang.EN, emptySet())).isEmpty()

        // Words marked as fillers are left to the fillers metric.
        val fillerIdx = ws.indices.filter { ws[it].w == "project" }.toSet()
        assertThat(RuleCoach.overusedWords(ws, Lang.EN, fillerIdx)).isEmpty()
    }

    @Test
    fun threeUsesInALongTalkIsUnderTwoPercent() {
        val filler = (1..200).joinToString(" ") { "word$it" }
        val ws = words("project project project $filler")
        assertThat(RuleCoach.overusedWords(ws, Lang.EN, emptySet())).isEmpty()
    }

    @Test
    fun crutchWordsCountFromTwoEvenWhenStopWords() {
        val ws = words("it was very good and very clear and really good")
        val found = RuleCoach.overusedWords(ws, Lang.EN, emptySet()).map { it.word }
        assertThat(found).containsExactly("very", "good").inOrder()
    }

    @Test
    fun hindiCrutchMatchesWithAndWithoutNukta() {
        val ws = words("यह चीज़ बहुत अच्छी है और वो चीज भी बहुत काम की है")
        val found = RuleCoach.overusedWords(ws, Lang.HI, emptySet())
        assertThat(found.map { it.word }).containsAtLeast("चीज़", "बहुत")
        assertThat(found.first { it.word == "चीज़" }.count).isEqualTo(2)
    }

    @Test
    fun wordUpgradesComeFromTheBookAndTheGrowQuotesARealToken() {
        val ws = words("it was good the food was good the people were good and the view was good")
        val fb = RuleCoach.coach(input(words = ws))
        val upgrade = fb.wordUpgrades.single()
        assertThat(upgrade.word).isEqualTo("good")
        assertThat(upgrade.count).isEqualTo(4)
        assertThat(upgrade.alternatives.map { it.word }).containsExactly("excellent", "solid", "outstanding")
        val grow = fb.grow!!
        assertThat(grow.issue).isEqualTo(GrowIssue.WEAK_WORD)
        assertThat(grow.hasSpan).isTrue()
        assertThat(ws[grow.start].w).isEqualTo(grow.quote)
        assertThat(grow.how).contains("\"excellent\"")
        assertThat(fb.betterVersions).isEmpty()
        assertThat(fb.mistakes).isEmpty()
    }

    @Test
    fun growOrderFillersThenPaceThenPausesThenWordsThenStructure() {
        assertThat(RuleCoach.coach(input(m = metrics(fillers = 6.0, band = WpmBand.FAST))).grow!!.issue).isEqualTo(GrowIssue.FILLER)
        assertThat(RuleCoach.coach(input(m = metrics(band = WpmBand.FAST, wpm = 180.0))).grow!!.why).contains("180 wpm")
        assertThat(RuleCoach.coach(input(m = metrics(pauses = 3.0))).grow!!.why).contains("Long pauses")
        assertThat(RuleCoach.coach(input()).grow!!.issue).isEqualTo(GrowIssue.STRUCTURE)
    }

    @Test
    fun stutterFriendlyNeverGetsAPaceOrPauseGrow() {
        val fb = RuleCoach.coach(input(m = metrics(band = WpmBand.SLOW, wpm = 80.0, pauses = null), stutter = true))
        assertThat(fb.grow!!.issue).isEqualTo(GrowIssue.STRUCTURE)
    }

    @Test
    fun glowPicksTheBiggestImprovementOverTheOwnAverage() {
        // Fillers down 50%, pauses down 20%: fillers win.
        val fb = RuleCoach.coach(input(m = metrics(fillers = 2.0, pauses = 0.8), avg = avg(fillers = 4.0, pauses = 1.0)))
        assertThat(fb.glow!!.note).contains("Fewer fillers")
        assertThat(fb.glow!!.hasSpan).isFalse()
        assertThat(fb.glow!!.start).isEqualTo(Feedback.NO_SPAN)

        val pauses = RuleCoach.coach(input(m = metrics(fillers = 3.9, pauses = 0.2), avg = avg(fillers = 4.0, pauses = 1.0)))
        assertThat(pauses.glow!!.note).contains("long pauses")

        val longer = RuleCoach.coach(input(m = metrics(fillers = 4.0, spanMs = 120_000), avg = avg(fillers = 4.0, pauses = 0.5, span = 60_000.0)))
        assertThat(longer.glow!!.note).contains("2:00")

        val variety = RuleCoach.coach(input(m = metrics(fillers = 4.0, mattr = 0.8), avg = avg(fillers = 4.0, pauses = 0.5, mattr = 0.6)))
        assertThat(variety.glow!!.note).contains("wider range")
    }

    @Test
    fun withoutHistoryTheGlowIsPaceOrShowingUp() {
        assertThat(RuleCoach.coach(input()).glow!!.note).contains("135 wpm")
        assertThat(RuleCoach.coach(input(m = metrics(band = WpmBand.FAST, wpm = 170.0))).glow!!.note).contains("showing up")
    }

    @Test
    fun hindiTalksGetHindiCopy() {
        val fb = RuleCoach.coach(input(words = words("आज मैं अपने काम के बारे में बताऊंगी"), lang = Lang.HI, synonyms = SynonymBook.empty(Lang.HI)))
        assertThat(fb.summary).contains("।")
        assertThat(fb.glow!!.note).contains("रफ़्तार")
        assertThat(fb.grow!!.how).contains("उदाहरण")
    }

    @Test
    fun notesArePassedThrough() {
        val fb = RuleCoach.coach(input().copy(notes = listOf(AnalysisNote.LLM_FALLBACK)))
        assertThat(fb.notes).containsExactly(AnalysisNote.LLM_FALLBACK)
    }

    @Test
    fun realMetricsEndToEnd() {
        val ws = words("so um the thing is the thing works and the thing is very very nice", gapMs = 50)
        val m = MetricsCalculator.compute(ws, Lang.EN)
        val fb = RuleCoach.coach(input(words = ws, m = m))
        assertThat(fb.glow).isNotNull()
        assertThat(fb.grow).isNotNull()
        assertThat(fb.summary).isNotEmpty()
    }
}
