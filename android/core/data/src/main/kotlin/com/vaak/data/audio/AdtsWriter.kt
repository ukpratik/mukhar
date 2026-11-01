package com.vaak.data.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Appends AAC-LC frames to `recordings/<id>.aac` with a 7-byte ADTS header each (LLD §4.1). The stream is unbuffered,
 * so every finished frame is in the file even if the process dies; [truncateToLastFrame] drops a half-written tail.
 */
class AdtsWriter(file: File, private val sampleRate: Int = 16_000, private val channels: Int = 1) : AutoCloseable {
    private val out = FileOutputStream(file, true)
    var frames: Long = 0
        private set

    fun writeFrame(payload: ByteArray, offset: Int = 0, length: Int = payload.size) {
        out.write(header(length, sampleRate, channels))
        out.write(payload, offset, length)
        frames++
    }

    override fun close() = out.close()

    companion object {
        const val HEADER_BYTES = 7
        const val SAMPLES_PER_FRAME = 1024
        private const val PROFILE_AAC_LC = 2
        private val SAMPLE_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

        fun header(payloadLength: Int, sampleRate: Int = 16_000, channels: Int = 1): ByteArray {
            val freqIndex = SAMPLE_RATES.indexOf(sampleRate)
            require(freqIndex >= 0) { "Unsupported sample rate $sampleRate" }
            val frameLength = payloadLength + HEADER_BYTES
            require(frameLength < (1 shl 13)) { "ADTS frame too long" }
            return byteArrayOf(
                0xFF.toByte(),
                0xF1.toByte(), // MPEG-4, layer 0, no CRC
                (((PROFILE_AAC_LC - 1) shl 6) or (freqIndex shl 2) or (channels shr 2)).toByte(),
                (((channels and 3) shl 6) or (frameLength shr 11)).toByte(),
                ((frameLength and 0x7FF) shr 3).toByte(),
                (((frameLength and 7) shl 5) or 0x1F).toByte(),
                0xFC.toByte(),
            )
        }

        /**
         * Crash recovery (US-018): keeps only complete ADTS frames and returns how many there are. A frame is
         * 1024 samples, so the duration is `frames × 1024 / sampleRate`.
         */
        fun truncateToLastFrame(file: File): Long {
            if (!file.exists()) return 0
            RandomAccessFile(file, "rw").use { raf ->
                val size = raf.length()
                val head = ByteArray(HEADER_BYTES)
                var pos = 0L
                var frames = 0L
                while (pos + HEADER_BYTES <= size) {
                    raf.seek(pos)
                    raf.readFully(head)
                    val sync = (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xF0) == 0xF0
                    val len = ((head[3].toInt() and 0x03) shl 11) or ((head[4].toInt() and 0xFF) shl 3) or
                        ((head[5].toInt() and 0xE0) shr 5)
                    if (!sync || len < HEADER_BYTES || pos + len > size) break
                    pos += len
                    frames++
                }
                if (pos < size) raf.setLength(pos)
                return frames
            }
        }

        fun durationMs(frames: Long, sampleRate: Int = 16_000): Long = frames * SAMPLES_PER_FRAME * 1000 / sampleRate
    }
}
