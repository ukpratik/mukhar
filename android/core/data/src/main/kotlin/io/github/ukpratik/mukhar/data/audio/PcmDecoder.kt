package io.github.ukpratik.mukhar.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.ukpratik.mukhar.data.util.MukharLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

/** Decodes a saved recording back to 16 kHz mono PCM, chunk by chunk (D-048 voice-only analysis). */
fun interface PcmDecoder {
    /** Calls [onPcm] with 16 kHz mono samples; false if the file couldn't be decoded. */
    suspend fun decode(file: File, onPcm: (ShortArray, Int) -> Unit): Boolean

    companion object {
        /** No decoding (tests that don't exercise the voice-only path). */
        val None = PcmDecoder { _, _ -> false }
    }
}

/** MediaExtractor + MediaCodec: the AAC (ADTS) recordings the [AacEncoder] writes, or any audio Android can read. */
class MediaCodecPcmDecoder : PcmDecoder {
    override suspend fun decode(file: File, onPcm: (ShortArray, Int) -> Unit): Boolean = withContext(Dispatchers.Default) {
        if (!file.exists() || file.length() == 0L) return@withContext false
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val resampler = Resampler(TARGET_RATE)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleAfterInput = 0
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = c.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = c.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = c.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // A decoder that never reports end of stream must not hang the analysis (≈ 3 s of nothing).
                        if (inputDone && ++idleAfterInput > MAX_IDLE_POLLS) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = c.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIndex >= 0 -> {
                        if (info.size > 0) {
                            val out = c.getOutputBuffer(outIndex)!!
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val interleaved = ShortArray(shorts.remaining()).also { shorts.get(it) }
                            val mono = toMono(interleaved, channels)
                            resampler.push(mono, rate, onPcm)
                        }
                        c.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            true
        } catch (e: Exception) {
            // MediaCodec throws IllegalStateException / IOException / CodecException on damaged files.
            MukharLog.w("decode failed: ${e::class.simpleName}")
            false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun toMono(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        return ShortArray(frames) { i ->
            var sum = 0
            for (ch in 0 until channels) sum += interleaved[i * channels + ch]
            (sum / channels).toShort()
        }
    }

    /** Linear resampling to [target] Hz (the recorder writes 16 kHz already, so this is normally a pass-through). */
    private class Resampler(private val target: Int) {
        private var pos = 0.0
        private var prev: Short = 0

        fun push(input: ShortArray, rate: Int, onPcm: (ShortArray, Int) -> Unit) {
            if (rate == target) {
                onPcm(input, input.size)
                return
            }
            val step = rate.toDouble() / target
            val out = ShortArray((input.size / step).toInt() + 2)
            var n = 0
            while (pos < input.size) {
                val i = pos.toInt()
                val frac = pos - i
                val a = if (i == 0) prev.toDouble() else input[i - 1].toDouble()
                val b = input[i].toDouble()
                out[n++] = (a + (b - a) * frac).toInt().toShort()
                pos += step
            }
            pos -= input.size
            if (input.isNotEmpty()) prev = input.last()
            onPcm(out, n)
        }
    }

    private companion object {
        const val TARGET_RATE = 16_000
        const val TIMEOUT_US = 10_000L
        const val MAX_IDLE_POLLS = 300
    }
}
