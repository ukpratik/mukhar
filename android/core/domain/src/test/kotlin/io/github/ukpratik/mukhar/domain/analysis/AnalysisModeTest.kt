package io.github.ukpratik.mukhar.domain.analysis

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.metrics.EngineFamily
import io.github.ukpratik.mukhar.domain.metrics.EngineReliability
import io.github.ukpratik.mukhar.domain.metrics.Reliability
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test

class AnalysisModeTest {
    @Test
    fun cloudIsOffInR1AndRunsOnDevice() {
        assertThat(AnalysisMode.CLOUD_ENABLED).isFalse()
        assertThat(AnalysisMode.CLOUD.effective).isEqualTo(AnalysisMode.ON_DEVICE)
        assertThat(AnalysisMode.OWN_KEYS.effective).isEqualTo(AnalysisMode.OWN_KEYS)
        assertThat(AnalysisMode.entries.none { it.usesServerQuota }).isTrue()
    }

    @Test
    fun unknownKeysFallBackToOnDevice() {
        assertThat(AnalysisMode.fromKey(null)).isEqualTo(AnalysisMode.ON_DEVICE)
        assertThat(AnalysisMode.fromKey("nope")).isEqualTo(AnalysisMode.ON_DEVICE)
        assertThat(AnalysisMode.fromKey("own_keys")).isEqualTo(AnalysisMode.OWN_KEYS)
        assertThat(AnalysisNote.fromKey("llm_fallback")).isEqualTo(AnalysisNote.LLM_FALLBACK)
    }

    @Test
    fun reliabilityPerEngine() {
        val local = EngineReliability.of("vosk", Lang.EN)
        assertThat(local.pace).isEqualTo(Reliability.HIGH)
        assertThat(local.fillers).isEqualTo(Reliability.APPROX)
        assertThat(local.anyApprox).isTrue()
        assertThat(EngineReliability.of("deepgram", Lang.EN).anyApprox).isFalse()
        assertThat(EngineReliability.of("deepgram", Lang.HI).fillers).isEqualTo(Reliability.APPROX)
        assertThat(EngineReliability.of("sarvam", Lang.HI).pauses).isEqualTo(Reliability.APPROX)
        assertThat(EngineReliability.of("fake", Lang.EN).anyApprox).isFalse()
        assertThat(EngineReliability.family("vosk/small-hi-0.22")).isEqualTo(EngineFamily.ON_DEVICE)
        assertThat(EngineReliability.family(null)).isEqualTo(EngineFamily.CLOUD)
    }
}
