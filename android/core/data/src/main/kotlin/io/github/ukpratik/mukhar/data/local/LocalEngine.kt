package io.github.ukpratik.mukhar.data.local

import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * On-device speech-to-text (ADR-008 "LocalAnalysisPipeline"). Stage 2B plugs Vosk in here (live PCM tee during
 * recording, AAC decode for re-runs); stage 2A binds [UnavailableLocalTranscriber].
 */
interface LocalTranscriber {
    /** Words with ms timestamps and 0..1 confidence; [AppError.EngineUnavailable] when the model isn't installed. */
    suspend fun transcribe(audio: File, lang: Lang, durationMs: Long): Outcome<List<Word>>

    /** True when the model for [lang] is installed and loadable. */
    fun isReady(lang: Lang): Boolean

    /** The model id stored with the transcript (`talk_transcript.model`), e.g. `small-hi-0.22`. */
    fun modelId(lang: Lang): String = "none"
}

/** 2A: no offline engine yet. Every call says "model not installed"; the talk stays saved for later. */
object UnavailableLocalTranscriber : LocalTranscriber {
    override suspend fun transcribe(audio: File, lang: Lang, durationMs: Long): Outcome<List<Word>> = Outcome.Err(AppError.EngineUnavailable)
    override fun isReady(lang: Lang): Boolean = false
}

enum class ModelState { NOT_INSTALLED, DOWNLOADING, INSTALLED, FAILED }

/** One offline speech model as the Settings → Analysis rows show it. */
data class OfflineModel(val lang: Lang, val name: String, val sizeMb: Int, val state: ModelState, val progress: Float? = null)

/**
 * Offline speech models (download on opt-in, ~40 MB per language, SHA-256 checked, in `noBackupFilesDir`). 2B
 * replaces [NoOpModelManager] with the Vosk download manager (WorkManager, Wi-Fi preference, storage check, delete).
 */
interface ModelManager {
    /** False while downloads aren't available in this build (2A): the UI says "coming in the next update". */
    val canDownload: Boolean
    val models: Flow<List<OfflineModel>>
    suspend fun download(lang: Lang)
    suspend fun delete(lang: Lang)
}

/** 2A: both models listed as not installed; download and delete do nothing. */
class NoOpModelManager : ModelManager {
    override val canDownload: Boolean = false
    private val state = MutableStateFlow(
        listOf(
            OfflineModel(Lang.EN, "vosk-model-small-en-in-0.4", 36, ModelState.NOT_INSTALLED),
            OfflineModel(Lang.HI, "vosk-model-small-hi-0.22", 42, ModelState.NOT_INSTALLED),
        ),
    )
    override val models: Flow<List<OfflineModel>> = state.asStateFlow()
    override suspend fun download(lang: Lang) = Unit
    override suspend fun delete(lang: Lang) = Unit
}
