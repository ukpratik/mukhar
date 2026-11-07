package io.github.ukpratik.mukhar.app.feature.settings

import android.media.MediaPlayer
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.data.content.PromptRepository
import io.github.ukpratik.mukhar.data.repo.RecordingItem
import io.github.ukpratik.mukhar.data.repo.RecordingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingRowUi(val item: RecordingItem, val promptText: String)

data class RecordingsUiState(
    val loading: Boolean = true,
    val rows: List<RecordingRowUi> = emptyList(),
    val totalBytes: Long = 0,
    val capMb: Int = 500,
    val playingId: String? = null,
)

/** US-080: Settings → Recordings. Playback is one MediaPlayer at a time, released when the screen goes. */
@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordings: RecordingsRepository,
    private val prompts: PromptRepository,
) : ViewModel() {
    private val playing = MutableStateFlow<String?>(null)
    private var player: MediaPlayer? = null
    private val messages = Channel<Int>(Channel.BUFFERED)
    val message: Flow<Int> = messages.receiveAsFlow()

    val state: StateFlow<RecordingsUiState> = combine(recordings.observe(), recordings.capMb, playing) { items, cap, now ->
        RecordingsUiState(
            loading = false,
            rows = items.map { RecordingRowUi(it, prompts.byId(it.promptId)?.text.orEmpty()) },
            totalBytes = items.sumOf { it.bytes },
            capMb = cap,
            playingId = now?.takeIf { id -> items.any { it.sessionId == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsUiState())

    fun onCap(mb: Int) = viewModelScope.launch { recordings.setCapMb(mb) }

    fun onDelete(id: String) = viewModelScope.launch {
        if (playing.value == id) stop()
        recordings.delete(id)
        say(R.string.recordings_deleted)
    }

    fun onDeleteAll() = viewModelScope.launch {
        stop()
        recordings.deleteAll()
        say(R.string.recordings_deleted_all)
    }

    fun onPlay(row: RecordingRowUi) {
        if (playing.value == row.item.sessionId) {
            stop()
            return
        }
        stop()
        val p = MediaPlayer()
        try {
            p.setDataSource(row.item.path)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            player = p
            playing.value = row.item.sessionId
        } catch (e: Exception) {
            // IOException / IllegalStateException on a damaged or deleted file.
            p.release()
            say(R.string.recordings_play_failed)
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playing.value = null
    }

    private fun say(@StringRes id: Int) {
        messages.trySend(id)
    }

    override fun onCleared() {
        stop()
    }
}
