package io.github.ukpratik.mukhar.data.audio

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.net.TranscribeMetaDto
import io.github.ukpratik.mukhar.domain.audio.EnergyVad
import io.github.ukpratik.mukhar.domain.talk.TalkThresholds
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import java.io.File
import kotlin.math.ceil

/**
 * NFR-011 / TC-064 / TC-271: the upload for the longest talk (Premium cap, 3:00) stays ≤ 3 MB on the wire,
 * computed from the encoder settings (AAC-LC, 16 kHz mono, [AacEncoder.BIT_RATE]) and the real ADTS framing and
 * multipart body the client sends to `/transcribe`.
 */
class UploadSizeTest {
    private val seconds = TalkThresholds.PREMIUM_CAP_S

    /** Writes an ADTS file the size the encoder produces for [seconds] at [bitRate] (one frame = 1024 samples). */
    private fun talkFile(bitRate: Int): File {
        val frames = ceil(seconds * EnergyVad.SAMPLE_RATE / AdtsWriter.SAMPLES_PER_FRAME.toDouble()).toInt()
        val payload = bitRate / 8 * AdtsWriter.SAMPLES_PER_FRAME / EnergyVad.SAMPLE_RATE
        val file = File.createTempFile("talk", ".aac").apply { delete(); deleteOnExit() }
        AdtsWriter(file).use { w -> repeat(frames) { w.writeFrame(ByteArray(payload)) } }
        return file
    }

    private fun wireBytes(audio: File): Long {
        val meta = TranscribeMetaDto("01JD0000000000000000000000", "talk", null, "hi", seconds * 1000L, queued = true)
        return MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("meta", null, Json.encodeToString(TranscribeMetaDto.serializer(), meta).toRequestBody(JSON))
            .addFormDataPart("audio", "talk.aac", audio.asRequestBody("audio/aac".toMediaType()))
            .build()
            .contentLength()
    }

    @Test
    fun threeMinuteTalkFileMatchesTheEncoderRate() {
        val file = talkFile(AacEncoder.BIT_RATE)
        // 32 kbps × 180 s = 720,000 B of AAC plus 7 B of ADTS header per frame (2,813 frames).
        assertThat(file.length()).isEqualTo(256L * 2_813 + 7L * 2_813)
        assertThat(file.length()).isAtMost(750_000L) // TC-064: ≤ 0.25 MB a minute
    }

    @Test
    fun threeMinuteUploadIsUnderThreeMegabytes() {
        val bytes = wireBytes(talkFile(AacEncoder.BIT_RATE))
        assertThat(bytes).isAtMost(MAX_UPLOAD)
        // Encoders overshoot their target rate on some devices; even at 3× the budget still holds.
        assertThat(wireBytes(talkFile(AacEncoder.BIT_RATE * 3))).isAtMost(MAX_UPLOAD)
    }

    private companion object {
        const val MAX_UPLOAD = 3L * 1024 * 1024
        val JSON = "application/json".toMediaType()
    }
}
