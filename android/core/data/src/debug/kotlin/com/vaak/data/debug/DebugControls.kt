package com.vaak.data.debug

import android.content.Context
import androidx.core.content.edit
import com.vaak.data.audio.StorageInfo
import com.vaak.data.repo.Entitlements
import com.vaak.data.work.AnalysisPipeline
import com.vaak.data.work.FeedbackRequest
import com.vaak.data.work.FeedbackResult
import com.vaak.data.work.ReportRequest
import com.vaak.data.work.TranscribeRequest
import com.vaak.data.work.TranscribeResult
import com.vaak.data.net.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

/** What the fake pipeline should do (debug menu). */
enum class FakeMode { NONE, STT_FAIL, LLM_FAIL, BUSY, OFFLINE, SELF_HARM }

/** Simulated free space (D-028: warn < 50 MB, block < 10 MB). */
enum class LowStorage(val bytes: Long?) { OFF(null), WARN(38L * 1024 * 1024), BLOCK(6L * 1024 * 1024) }

data class DebugState(
    val useFake: Boolean = true,
    val mode: FakeMode = FakeMode.NONE,
    val delayMs: Long = 1_500,
    val lowStorage: LowStorage = LowStorage.OFF,
    val premium: Boolean = false,
)

/** Debug-only switches, persisted so QA settings survive restarts. Stripped from release builds. */
class DebugControls(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vaak_debug", Context.MODE_PRIVATE)
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
        }
        _state.value = next
    }

    private fun read() = DebugState(
        useFake = prefs.getBoolean(K_FAKE, true),
        mode = runCatching { FakeMode.valueOf(prefs.getString(K_MODE, null) ?: "NONE") }.getOrDefault(FakeMode.NONE),
        delayMs = prefs.getLong(K_DELAY, 1_500),
        lowStorage = runCatching { LowStorage.valueOf(prefs.getString(K_STORAGE, null) ?: "OFF") }.getOrDefault(LowStorage.OFF),
        premium = prefs.getBoolean(K_PREMIUM, false),
    )

    private companion object {
        const val K_FAKE = "use_fake"
        const val K_MODE = "fake_mode"
        const val K_DELAY = "fake_delay_ms"
        const val K_STORAGE = "low_storage"
        const val K_PREMIUM = "premium"
    }
}

/** Routes each call to the fake or the real Worker, as the debug menu says. */
class DebugAnalysisPipeline(
    private val controls: DebugControls,
    private val fake: AnalysisPipeline,
    private val remote: AnalysisPipeline,
) : AnalysisPipeline {
    private val target get() = if (controls.state.value.useFake) fake else remote
    override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> = target.transcribe(request, audio)
    override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> = target.feedback(request)
    override suspend fun report(request: ReportRequest): Outcome<Unit> = target.report(request)
}

class DebugStorageInfo(private val real: StorageInfo, private val controls: DebugControls) : StorageInfo {
    override fun availableBytes(): Long = controls.state.value.lowStorage.bytes ?: real.availableBytes()
}

class DebugEntitlements(controls: DebugControls) : Entitlements {
    override val isPremium: Flow<Boolean> = controls.state.map { it.premium }
}
