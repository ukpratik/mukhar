package io.github.ukpratik.mukhar.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import io.github.ukpratik.mukhar.domain.audio.EnergyVad

/** 16 kHz mono 16-bit PCM in 20 ms frames (LLD §4.1). Faked in tests with synthetic PCM. */
interface PcmSource {
    /** Starts (or restarts after a pause) capturing. Returns false if the mic can't be opened. */
    fun start(): Boolean

    /** Reads up to `buffer.size` samples; returns the count, or a negative value on error. May block one frame. */
    suspend fun read(buffer: ShortArray): Int

    /** Stops capturing but keeps the source reusable (pause). */
    fun stop()

    fun release()
}

fun interface PcmSourceFactory {
    fun create(): PcmSource
}

/** `AudioRecord(MIC, 16000, MONO, PCM_16BIT)`. Callers must hold RECORD_AUDIO (checked in the UI before start). */
class AudioRecordSource : PcmSource {
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // The recording screen requests RECORD_AUDIO before it can start.
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(): Boolean {
        val r = record ?: run {
            val min = AudioRecord.getMinBufferSize(EnergyVad.SAMPLE_RATE, CHANNEL, ENCODING)
            if (min <= 0) return false
            val created = AudioRecord(
                MediaRecorder.AudioSource.MIC, EnergyVad.SAMPLE_RATE, CHANNEL, ENCODING,
                maxOf(min, EnergyVad.FRAME_SAMPLES * 2 * 8),
            )
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                created.release()
                return false
            }
            created.also { record = it }
        }
        return runCatching { r.startRecording() }.isSuccess && r.recordingState == AudioRecord.RECORDSTATE_RECORDING
    }

    override suspend fun read(buffer: ShortArray): Int = record?.read(buffer, 0, buffer.size) ?: -1

    override fun stop() {
        runCatching { record?.stop() }
    }

    override fun release() {
        stop()
        record?.release()
        record = null
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
