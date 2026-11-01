package com.vaak.app.feature.onboarding.warmup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** Mic frames for the warm-up. A fake drives the tests; the real one reads the mic. Nothing is stored. */
interface WarmupMeter {
    /** Emits 20 ms 16 kHz mono frames until cancelled. Fails if the mic can't open. */
    fun frames(): Flow<ShortArray>
}

class AudioRecordWarmupMeter @Inject constructor(@param:ApplicationContext private val context: Context) : WarmupMeter {

    override fun frames(): Flow<ShortArray> = flow {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission missing"
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            WarmupAnalyzer.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WarmupAnalyzer.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, WarmupAnalyzer.FRAME_SAMPLES * 2 * 4),
            )
        } catch (e: SecurityException) {
            throw IllegalStateException("Microphone permission missing", e)
        }
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        try {
            record.startRecording()
            while (coroutineContext.isActive) {
                val frame = ShortArray(WarmupAnalyzer.FRAME_SAMPLES)
                val read = record.read(frame, 0, frame.size)
                if (read < 0) error("Microphone read failed")
                if (read > 0) emit(if (read == frame.size) frame else frame.copyOf(read))
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}
