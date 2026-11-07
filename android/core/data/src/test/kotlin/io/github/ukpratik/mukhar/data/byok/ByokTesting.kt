package io.github.ukpratik.mukhar.data.byok

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import io.github.ukpratik.mukhar.data.work.FeedbackRequest
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import kotlinx.serialization.json.Json

/** Real Tink AES256_GCM with an in-memory keyset (the Android Keystore wrapping isn't available on the JVM). */
class TestCipher : AeadKeyCipher({ AEAD }) {
    var destroyed = false
    override fun destroy() {
        destroyed = true
    }

    companion object {
        private val AEAD: Aead by lazy {
            AeadConfig.register()
            KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
    }
}

/** Keys from a map, for pipeline tests. */
class MapKeys(val keys: MutableMap<ByokProvider, String> = mutableMapOf()) : KeySource {
    override suspend fun key(provider: ByokProvider): String? = keys[provider]
}

class FakeMeter(var over: Boolean = false) : UsageMeter {
    val records = mutableListOf<List<Long>>()
    override suspend fun overSoftCap(): Boolean = over
    override suspend fun record(talks: Int, deepgramMs: Long, sarvamMs: Long, inputTokens: Long, outputTokens: Long) {
        records += listOf(talks.toLong(), deepgramMs, sarvamMs, inputTokens, outputTokens)
    }
}

val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

fun words(text: String, conf: Double = 0.95): List<Word> {
    var t = 0L
    return text.split(" ").map { w -> Word(w, t, t + 300, conf).also { t += 400 } }
}

fun feedbackRequest(ws: List<Word>, lang: Lang = Lang.EN, provider: String? = "deepgram", id: String = "local-1") = FeedbackRequest(
    serverSessionId = id, lang = lang, track = "interviews", prompt = "Tell us about a project", stutterFriendly = false,
    words = ws, lowConf = emptySet(), metrics = MetricsCalculator.compute(ws, lang), speechMs = 60_000, durationMs = 62_000,
    transcriptProvider = provider,
)
