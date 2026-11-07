package io.github.ukpratik.mukhar.data.debug

import android.content.Context
import androidx.core.content.edit
import io.github.ukpratik.mukhar.data.analytics.AnalyticsEvent
import io.github.ukpratik.mukhar.data.analytics.AnalyticsSink
import io.github.ukpratik.mukhar.data.audio.StorageInfo
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.data.repo.Entitlements
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.FeedbackRequest
import io.github.ukpratik.mukhar.data.work.FeedbackResult
import io.github.ukpratik.mukhar.data.work.ReportRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckResult
import io.github.ukpratik.mukhar.data.work.TranscribeRequest
import io.github.ukpratik.mukhar.data.work.TranscribeResult
import io.github.ukpratik.mukhar.data.net.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

/** What the fake pipeline should do (debug menu). */
enum class FakeMode { NONE, STT_FAIL, LLM_FAIL, BUSY, OFFLINE, SELF_HARM }

/** Simulated free space (D-028: warn < 50 MB, block < 10 MB). */
enum class LowStorage(val bytes: Long?) { OFF(null), WARN(38L * 1024 * 1024), BLOCK(6L * 1024 * 1024) }

data class DebugState(
    /** Force the canned fake pipeline (demos). Off: the real analysis-mode router, as in release (ADR-008). */
    val useFake: Boolean = false,
    val mode: FakeMode = FakeMode.NONE,
    val delayMs: Long = 1_500,
    val lowStorage: LowStorage = LowStorage.OFF,
    val premium: Boolean = false,
    /** Fake Play billing (no Play account needed). Applies after a restart. */
    val fakeBilling: Boolean = true,
)

/** Debug-only switches, persisted so QA settings survive restarts. Stripped from release builds. */
class DebugControls(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mukhar_debug", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    fun update(f: (DebugState) -> DebugState) {
        val next = f(_state.value)
        prefs.edit {
            putBoolean(K_FAKE, next.useFake)
            putString(K_MODE, next.mode.name)
            putLong(K_DELAY, next.delayMs)
            putString(K_STORAGE, next.lowStorage.name)
            putBoolean(K_PREMIUM, next.premium)
            putBoolean(K_FAKE_BILLING, next.fakeBilling)
        }
        _state.value = next
    }

    private fun read() = DebugState(
        useFake = prefs.getBoolean(K_FAKE, false),
        mode = runCatching { FakeMode.valueOf(prefs.getString(K_MODE, null) ?: "NONE") }.getOrDefault(FakeMode.NONE),
        delayMs = prefs.getLong(K_DELAY, 1_500),
        lowStorage = runCatching { LowStorage.valueOf(prefs.getString(K_STORAGE, null) ?: "OFF") }.getOrDefault(LowStorage.OFF),
        premium = prefs.getBoolean(K_PREMIUM, false),
        fakeBilling = prefs.getBoolean(K_FAKE_BILLING, true),
    )

    private companion object {
        const val K_FAKE = "force_fake"
        const val K_MODE = "fake_mode"
        const val K_DELAY = "fake_delay_ms"
        const val K_STORAGE = "low_storage"
        const val K_PREMIUM = "premium"
        const val K_FAKE_BILLING = "fake_billing"
    }
}

/** Routes each call to the fake or the real analysis-mode router, as the debug menu says. */
class DebugAnalysisPipeline(
    private val controls: DebugControls,
    private val fake: AnalysisPipeline,
    private val remote: AnalysisPipeline,
) : AnalysisPipeline {
    private val target get() = if (controls.state.value.useFake) fake else remote
    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> = target.transcribe(request, audio)
    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> = target.feedback(request)
    override suspend fun report(request: ReportRequest): Outcome<Unit> = target.report(request)
    override suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult> =
        target.checkSentence(request, audio)
}

class DebugStorageInfo(private val real: StorageInfo, private val controls: DebugControls) : StorageInfo {
    override fun availableBytes(): Long = controls.state.value.lowStorage.bytes ?: real.availableBytes()
}

/** The debug "Premium" switch forces Premium on top of the real (or fake-billing) entitlement. */
class DebugEntitlements(controls: DebugControls, real: Entitlements) : Entitlements {
    override val isPremium: Flow<Boolean> = combine(controls.state.map { it.premium }, real.isPremium) { forced, paid -> forced || paid }
}

/** Debug builds keep the last consented analytics events in memory (Logcat too) so QA can see what would be sent. */
object DebugAnalyticsSink : AnalyticsSink {
    private const val MAX = 50
    private val _events = MutableStateFlow<List<AnalyticsEvent>>(emptyList())
    val events: StateFlow<List<AnalyticsEvent>> = _events.asStateFlow()

    override fun record(event: AnalyticsEvent) {
        _events.value = (_events.value + event).takeLast(MAX)
        MukharLog.i("analytics: ${event.name} ${event.params}")
    }
}

/** The fake Play account lives in its own prefs file, so "Delete all my data" leaves it (as Play would). */
class SharedPrefsFakeStore(context: Context) : FakeStorePersistence {
    private val prefs = context.applicationContext.getSharedPreferences("mukhar_fake_play", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString(KEY, null)
    override fun save(json: String?) = prefs.edit { if (json == null) remove(KEY) else putString(KEY, json) }

    private companion object {
        const val KEY = "subscription"
    }
}
