package com.vaak.data.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class AdtsWriterTest {
    @Test
    fun headerEncodesAacLc16kMonoAndLength() {
        val h = AdtsWriter.header(payloadLength = 100)
        assertThat(h.size).isEqualTo(7)
        assertThat(h[0].toInt() and 0xFF).isEqualTo(0xFF)
        assertThat(h[1].toInt() and 0xFF).isEqualTo(0xF1)
        // profile LC (1), sampling index 8 (16 kHz), channel config 1.
        assertThat((h[2].toInt() and 0xC0) shr 6).isEqualTo(1)
        assertThat((h[2].toInt() and 0x3C) shr 2).isEqualTo(8)
        assertThat(((h[2].toInt() and 1) shl 2) or ((h[3].toInt() and 0xC0) shr 6)).isEqualTo(1)
        val len = ((h[3].toInt() and 3) shl 11) or ((h[4].toInt() and 0xFF) shl 3) or ((h[5].toInt() and 0xE0) shr 5)
        assertThat(len).isEqualTo(107)
    }

    @Test
    fun recoveryTrimsAHalfWrittenFrame() {
        val file = File.createTempFile("rec", ".aac")
        file.delete()
        AdtsWriter(file).use { w -> repeat(10) { w.writeFrame(ByteArray(90) { it.toByte() }) } }
        val complete = file.length()
        file.appendBytes(AdtsWriter.header(90) + ByteArray(30)) // the process died mid-frame
        assertThat(AdtsWriter.truncateToLastFrame(file)).isEqualTo(10)
        assertThat(file.length()).isEqualTo(complete)
        assertThat(AdtsWriter.durationMs(10)).isEqualTo(640) // 10 × 1024 samples at 16 kHz
    }

    @Test
    fun emptyOrMissingFilesHaveNoFrames() {
        assertThat(AdtsWriter.truncateToLastFrame(File("/nonexistent/x.aac"))).isEqualTo(0)
        val garbage = File.createTempFile("bad", ".aac").apply { writeBytes(ByteArray(50) { 1 }) }
        assertThat(AdtsWriter.truncateToLastFrame(garbage)).isEqualTo(0)
        assertThat(garbage.length()).isEqualTo(0)
    }
}
