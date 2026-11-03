package io.github.ukpratik.mukhar.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.debug.CannedTalks
import io.github.ukpratik.mukhar.data.debug.DebugControls
import io.github.ukpratik.mukhar.data.debug.FakeAnalysisPipeline
import io.github.ukpratik.mukhar.data.debug.FakeMode
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.Stage
import io.github.ukpratik.mukhar.domain.feedback.GroundingValidator
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The debug fake (build-plan "Debug hooks"): canned EN/HI talks with grounded notes and switchable failures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FakeAnalysisPipelineTest {
    private val controls = DebugControls(ApplicationProvider.getApplicationContext<Context>()).apply { update { it.copy(delayMs = 0, mode = FakeMode.NONE) } }
    private val fake = FakeAnalysisPipeline(controls)

    @Test
    fun cannedNotesAreGroundedInBothLanguages() {
        for (lang in Lang.entries) {
            val words = CannedTalks.words(lang)
            val lowConf = words.indices.filter { words[it].conf < 0.6 }.toSet()
            val r = GroundingValidator.validate(CannedTalks.feedback(lang, words.map { it.w }), words.map { it.w }, lowConf, false)
            assertThat(r.fallbackGlow).isFalse()
            assertThat(r.fallbackGrow).isFalse()
            assertThat(r.feedback.betterVersions).hasSize(3)
            assertThat(r.dropped).isEqualTo(if (lang == Lang.EN) 1 else 0) // EN has one deliberate low-confidence quote
            val m = MetricsCalculator.compute(words, lang)
            assertThat(m.fillerCount).isAtLeast(3)
        }
    }

    @Test
    fun retakeIsCleaner() {
        for (lang in Lang.entries) {
            val first = MetricsCalculator.compute(CannedTalks.words(lang), lang)
            val retake = MetricsCalculator.compute(CannedTalks.words(lang, retake = true), lang)
            assertThat(retake.fillersPerMin).isLessThan(first.fillersPerMin * 0.9)
        }
    }

    @Test
    fun failureModes() = runTest {
        val req = TranscribeRequest("c", "talk", null, Lang.EN, 60_000, false)
        val audio = File.createTempFile("fake", ".aac")
        assertThat(fake.transcribe(req, audio)).isInstanceOf(Outcome.Ok::class.java)
        controls.update { it.copy(mode = FakeMode.STT_FAIL) }
        assertThat((fake.transcribe(req, audio) as Outcome.Err).error).isEqualTo(AppError.Upstream(Stage.STT, "STT_FAILED"))
        controls.update { it.copy(mode = FakeMode.BUSY) }
        assertThat((fake.transcribe(req, audio) as Outcome.Err).error).isEqualTo(AppError.Busy(null))
        controls.update { it.copy(mode = FakeMode.OFFLINE) }
        assertThat((fake.transcribe(req, audio) as Outcome.Err).error).isEqualTo(AppError.Offline)
        controls.update { it.copy(mode = FakeMode.LLM_FAIL) }
        assertThat(fake.transcribe(req, audio)).isInstanceOf(Outcome.Ok::class.java)
    }
}
