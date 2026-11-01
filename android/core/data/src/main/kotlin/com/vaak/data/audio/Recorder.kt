package com.vaak.data.audio

import com.vaak.domain.audio.EnergyVad
import com.vaak.domain.talk.TalkThresholds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** What the recording screen draws (LLD §5 recording state machine; think time lives in the ViewModel). */
sealed interface RecorderState {
    data object Idle : RecorderState

    data class Active(
        val elapsedMs: Long,
        val capMs: Long,
        val pausesUsed: Int,
        val paused: Boolean,
        /** Paused by the app going to the background or a call, not by the user. */
        val autoPaused: Boolean,
        val speechMs: Long,
        /** Recent peaks, 0..1, one per 50 ms (oldest first). */
        val waveform: List<Float>,
    ) : RecorderState {
        val pausesLeft: Int get() = (TalkThresholds.MAX_PAUSES - pausesUsed).coerceAtLeast(0)
    }

    data class Finished(val result: RecordingResult) : RecorderState

    /** The mic couldn't be opened or failed mid-talk; what was written so far is kept. */
    data class Failed(val result: RecordingResult?) : RecorderState
}

data class RecordingResult(
    val file: File,
    val durationMs: Long,
    val speechMs: Long,
    val snrDb: Double?,
    val stoppedAtCap: Boolean,
)

/** Ambient check on the ready screen (S09 states 1 and 5): noise never blocks recording. */
data class MicCheck(val levelDb: Double, val noisy: Boolean) {
    /** 0..5 bars for the level meter. */
    val bars: Int get() = (((levelDb + 70.0) / 50.0) * 5).toInt().coerceIn(1, 5)
}

/**
 * AudioRecord PCM 16 kHz mono → [EnergyVad] (speech ms, SNR) → AAC (LLD §4.1, US-011). App-scoped: the UI reads
 * [state] and survives configuration changes. Pausing (≤ 2 by the user; the app auto-pauses on background) stops
 * reading but keeps the file open. Time is measured in samples, so the elapsed time is exact.
 */
class Recorder(
    private val sources: PcmSourceFactory,
    private val sinks: AudioSinkFactory,
    private val dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val paused = MutableStateFlow(false)
    private var job: Job? = null
    private var stopRequested = false
    private var discardRequested = false
    private var session: Session? = null

    private class Session(val file: File, val capMs: Long, val source: PcmSource, val sink: AudioSink) {
        val vad = EnergyVad()
        var samples = 0L
        var stoppedAtCap = false
        var failed = false
    }

    /** Listens for [durationMs] without recording and reports the ambient level. */
    suspend fun micCheck(durationMs: Long = MIC_CHECK_MS): MicCheck? = withContext(dispatcher) {
        val source = sources.create()
        try {
            if (!source.start()) return@withContext null
            val buffer = ShortArray(EnergyVad.FRAME_SAMPLES)
            val levels = mutableListOf<Double>()
            var samples = 0L
            while (samples * 1000 / EnergyVad.SAMPLE_RATE < durationMs && isActive) {
                val n = source.read(buffer)
                if (n < 0) return@withContext null
                if (n == 0) continue
                levels += EnergyVad.levelDb(buffer, n)
                samples += n
            }
            if (levels.isEmpty()) return@withContext null
            val median = levels.sorted()[levels.size / 2]
            MicCheck(median, median > EnergyVad.NOISY_AMBIENT_DB)
        } finally {
            source.release()
        }
    }

    /** Starts a new recording into [file]. Returns false if one is already running or the mic can't open. */
    fun start(file: File, capMs: Long): Boolean {
        if (_state.value is RecorderState.Active) return false
        val source = sources.create()
        if (!source.start()) {
            source.release()
            return false
        }
        val s = Session(file, capMs, source, sinks.create(file))
        session = s
        stopRequested = false
        discardRequested = false
        paused.value = false
        _state.value = RecorderState.Active(0, capMs, 0, paused = false, autoPaused = false, speechMs = 0, waveform = emptyList())
        job = scope.launch { loop(s) }
        return true
    }

    /**
     * Pauses. A user pause is refused once [TalkThresholds.MAX_PAUSES] are used; an automatic pause (background, call)
     * always happens because the mic can't record in the background.
     */
    fun pause(auto: Boolean = false): Boolean {
        val current = _state.value as? RecorderState.Active ?: return false
        if (current.paused) return false
        if (!auto && current.pausesUsed >= TalkThresholds.MAX_PAUSES) return false
        paused.value = true
        _state.value = current.copy(paused = true, autoPaused = auto, pausesUsed = current.pausesUsed + 1)
        return true
    }

    fun resume(): Boolean {
        val current = _state.value as? RecorderState.Active ?: return false
        if (!current.paused) return false
        _state.value = current.copy(paused = false, autoPaused = false)
        paused.value = false
        return true
    }

    /** Stops, flushes the encoder and returns the result (also published as [RecorderState.Finished]). */
    suspend fun stop(): RecordingResult? {
        val running = job ?: return (_state.value as? RecorderState.Finished)?.result
        stopRequested = true
        paused.value = false
        running.join()
        return when (val s = _state.value) {
            is RecorderState.Finished -> s.result
            is RecorderState.Failed -> s.result
            else -> null
        }
    }

    /** Stops and deletes the file (discard confirmed by the user). */
    suspend fun discard() {
        val running = job
        discardRequested = true
        stopRequested = true
        paused.value = false
        running?.cancelAndJoin()
        session?.let { s ->
            runCatching { s.sink.abort() }
            s.source.release()
            s.file.delete()
        }
        session = null
        job = null
        _state.value = RecorderState.Idle
    }

    /** Back to idle after the UI consumed a finished result. */
    fun reset() {
        if (_state.value !is RecorderState.Active) _state.value = RecorderState.Idle
    }

    private suspend fun loop(s: Session) {
        val buffer = ShortArray(EnergyVad.FRAME_SAMPLES)
        val peaks = ArrayDeque<Float>()
        var peakWindowSamples = 0
        var peak = 0
        var lastEmit = 0L
        try {
            while (!stopRequested) {
                if (paused.value) {
                    s.source.stop()
                    paused.first { !it }
                    if (stopRequested) break
                    if (!s.source.start()) {
                        s.failed = true
                        break
                    }
                }
                val n = s.source.read(buffer)
                if (n < 0) {
                    s.failed = true
                    break
                }
                if (n == 0 || paused.value) continue
                s.vad.process(buffer, n)
                s.sink.write(buffer, n)
                s.samples += n
                for (i in 0 until n) peak = maxOf(peak, abs(buffer[i].toInt()))
                peakWindowSamples += n
                if (peakWindowSamples >= PEAK_WINDOW_SAMPLES) {
                    peaks.addLast((peak / 32768f).coerceIn(0f, 1f))
                    if (peaks.size > WAVEFORM_BARS) peaks.removeFirst()
                    peak = 0
                    peakWindowSamples = 0
                }
                val elapsed = elapsedMs(s)
                if (elapsed >= s.capMs) {
                    s.stoppedAtCap = true
                    break
                }
                if (elapsed - lastEmit >= EMIT_EVERY_MS) {
                    lastEmit = elapsed
                    _state.update { st ->
                        if (st is RecorderState.Active && !st.paused) {
                            st.copy(elapsedMs = elapsed, speechMs = s.vad.speechMs, waveform = peaks.toList())
                        } else {
                            st
                        }
                    }
                }
            }
        } finally {
            if (!discardRequested) finish(s)
        }
    }

    private fun finish(s: Session) {
        s.source.release()
        val flushed = runCatching { s.sink.finish() }.isSuccess
        val result = RecordingResult(
            file = s.file,
            durationMs = elapsedMs(s),
            speechMs = s.vad.speechMs,
            snrDb = s.vad.snrDb(),
            stoppedAtCap = s.stoppedAtCap,
        )
        session = null
        job = null
        _state.value = if (s.failed || !flushed) RecorderState.Failed(result) else RecorderState.Finished(result)
    }

    private fun elapsedMs(s: Session): Long = s.samples * 1000 / EnergyVad.SAMPLE_RATE

    companion object {
        const val MIC_CHECK_MS = 2_000L
        const val EMIT_EVERY_MS = 100L
        const val WAVEFORM_BARS = 26
        private const val PEAK_WINDOW_SAMPLES = EnergyVad.SAMPLE_RATE / 20 // 50 ms
    }
}
