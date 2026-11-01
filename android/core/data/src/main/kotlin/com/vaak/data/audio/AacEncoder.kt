package com.vaak.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.vaak.domain.audio.EnergyVad
import java.io.File

/** Where the recorder's PCM goes. The real one encodes AAC; tests use a counting fake. */
interface AudioSink {
    fun write(pcm: ShortArray, length: Int)

    /** Flushes the encoder and closes the file. */
    fun finish()

    /** Closes without flushing (discard). */
    fun abort()
}

fun interface AudioSinkFactory {
    fun create(file: File): AudioSink
}

/** `MediaCodec` AAC-LC, 16 kHz mono, 32 kbps (≈ 0.24 MB/min, NFR-011) → [AdtsWriter]. */
class AacEncoder(file: File, bitRate: Int = BIT_RATE) : AudioSink {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val writer = AdtsWriter(file)
    private val info = MediaCodec.BufferInfo()
    private var samplesIn = 0L
    private var payload = ByteArray(1024)

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, EnergyVad.SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, EnergyVad.FRAME_SAMPLES * 2 * 4)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    override fun write(pcm: ShortArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain(false)
                continue
            }
            val buffer = codec.getInputBuffer(index) ?: continue
            buffer.clear()
            val count = minOf(length - offset, buffer.remaining() / 2)
            for (i in 0 until count) {
                val s = pcm[offset + i].toInt()
                buffer.put((s and 0xFF).toByte())
                buffer.put((s shr 8 and 0xFF).toByte())
            }
            codec.queueInputBuffer(index, 0, count * 2, presentationUs(), 0)
            samplesIn += count
            offset += count
            drain(false)
        }
    }

    override fun finish() {
        try {
            var queued = false
            while (!queued) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, presentationUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    queued = true
                } else {
                    drain(false)
                }
            }
            drain(true)
        } finally {
            release()
        }
    }

    override fun abort() = release()

    private fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { writer.close() }
    }

    private fun drain(untilEnd: Boolean) {
        var idleTries = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index >= 0 -> {
                    val out = codec.getOutputBuffer(index)
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && !config) {
                        if (payload.size < info.size) payload = ByteArray(info.size)
                        out.position(info.offset)
                        out.get(payload, 0, info.size)
                        writer.writeFrame(payload, 0, info.size)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEnd || ++idleTries > MAX_IDLE_TRIES) return
                }
                else -> Unit // format or buffers changed: ADTS needs nothing from the format
            }
        }
    }

    private fun presentationUs(): Long = samplesIn * 1_000_000L / EnergyVad.SAMPLE_RATE

    companion object {
        const val BIT_RATE = 32_000
        private const val TIMEOUT_US = 10_000L
        private const val MAX_IDLE_TRIES = 100
    }
}
