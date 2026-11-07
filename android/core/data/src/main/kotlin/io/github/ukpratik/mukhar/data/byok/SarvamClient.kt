package io.github.ukpratik.mukhar.data.byok

import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sarvam speech-to-text (Hindi, Hinglish), straight from the phone with the user's key (ADR-008). The sync REST
 * endpoint (`POST /speech-to-text`, header `api-subscription-key`, multipart `file`) is meant for clips under 30 s,
 * and the batch job API's upload steps aren't documented well enough to build on yet, so a talk is cut into ADTS
 * pieces of at most [MAX_CHUNK_MS] at frame boundaries (each piece is valid AAC on its own) and sent one by one.
 *
 * Sarvam returns chunk-level timestamps only ("word-level timestamps are not supported"), so word times are
 * interpolated by character length inside each timestamp chunk and offset by the piece's start: they're approximate
 * (pauses and repeats are marked ≈ for this engine). Sarvam gives no confidence, so every word gets [CONFIDENCE].
 * `saaras:v3` with `mode=verbatim` keeps fillers.
 */
class SarvamClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val base: HttpUrl = BASE.toHttpUrl(),
) {
    suspend fun transcribe(key: String, audio: File, lang: Lang): ByokResult<SttResult> {
        val pieces = try {
            AdtsChunker.split(audio.readBytes(), MAX_CHUNK_MS)
        } catch (e: IOException) {
            return ByokResult.Fail(ByokError.BadResponse("audio"))
        }
        if (pieces.isEmpty()) return ByokResult.Fail(ByokError.BadResponse("no_audio"))
        val words = mutableListOf<Word>()
        for (piece in pieces) {
            when (val r = send(key, piece.bytes, "talk.aac", AAC, lang)) {
                is ByokResult.Fail -> return r
                is ByokResult.Ok -> words += interpolate(r.value, piece.startMs, piece.durationMs)
            }
        }
        val totalMs = pieces.last().let { it.startMs + it.durationMs }
        return ByokResult.Ok(SttResult(words, PROVIDER, MODEL, totalMs))
    }

    /** A 1 s silent WAV through the sync endpoint (≈ ₹0.01): 2xx works; 400/422 means the key was accepted too. */
    suspend fun check(key: String): KeyCheck = when (val r = send(key, silentWav(), "check.wav", WAV, Lang.HI)) {
        is ByokResult.Ok -> KeyCheck.OK
        is ByokResult.Fail -> when (val e = r.error) {
            ByokError.Offline -> KeyCheck.OFFLINE
            ByokError.Unauthorized, ByokError.Forbidden -> KeyCheck.INVALID
            is ByokError.RateLimited -> KeyCheck.LIMITED
            is ByokError.BadResponse -> if (e.reason == "http_400" || e.reason == "http_422") KeyCheck.OK else KeyCheck.ERROR
            else -> KeyCheck.ERROR
        }
    }

    private suspend fun send(key: String, bytes: ByteArray, name: String, type: okhttp3.MediaType, lang: Lang): ByokResult<SyncResponse> {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody(type))
            .addFormDataPart("model", MODEL)
            .addFormDataPart("mode", "verbatim")
            .addFormDataPart("language_code", if (lang == Lang.HI) "hi-IN" else "en-IN")
            .addFormDataPart("with_timestamps", "true")
            .build()
        val request = Request.Builder().url(base.newBuilder().addPathSegments("speech-to-text").build())
            .header("api-subscription-key", key)
            .post(body)
            .build()
        return try {
            http.newCall(request).await().use { r ->
                if (!r.isSuccessful) return ByokResult.Fail(httpError(r.code, r.header("Retry-After")))
                ByokResult.Ok(json.decodeFromString(SyncResponse.serializer(), r.body.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ByokResult.Fail(ByokError.Offline)
        } catch (e: SerializationException) {
            ByokResult.Fail(ByokError.BadResponse("json"))
        } catch (e: IllegalArgumentException) {
            ByokResult.Fail(ByokError.BadResponse("shape"))
        }
    }

    @Serializable
    data class SyncResponse(val transcript: String = "", val timestamps: Timestamps? = null)

    @Serializable
    data class Timestamps(
        val words: List<String> = emptyList(),
        @SerialName("start_time_seconds") val start: List<Double> = emptyList(),
        @SerialName("end_time_seconds") val end: List<Double> = emptyList(),
    )

    companion object {
        const val BASE = "https://api.sarvam.ai/"
        const val MODEL = "saaras:v3"
        const val PROVIDER = "sarvam"
        const val MAX_CHUNK_MS = 29_000L
        const val CONFIDENCE = 0.9
        private val AAC = "audio/aac".toMediaType()
        private val WAV = "audio/wav".toMediaType()
        private val SPACE = Regex("\\s+")

        /**
         * Spreads each timestamp chunk's words over its time span by character length (+1 per word for the gap), then
         * shifts by [offsetMs]. Without timestamps, the whole transcript is spread over the piece.
         */
        fun interpolate(r: SyncResponse, offsetMs: Long, pieceMs: Long): List<Word> {
            val ts = r.timestamps
            val chunks: List<Triple<String, Double, Double>> =
                if (ts != null && ts.words.isNotEmpty() && ts.words.size == ts.start.size && ts.words.size == ts.end.size) {
                    ts.words.indices.map { Triple(ts.words[it], ts.start[it], ts.end[it]) }
                } else {
                    listOf(Triple(r.transcript, 0.0, pieceMs / 1000.0))
                }
            val out = mutableListOf<Word>()
            for ((text, s, e) in chunks) {
                val tokens = text.trim().split(SPACE).filter { it.isNotEmpty() }
                if (tokens.isEmpty()) continue
                val startMs = offsetMs + (s * 1000).toLong()
                val spanMs = ((e - s) * 1000).toLong().coerceAtLeast(tokens.size.toLong())
                val weights = tokens.map { it.length + 1 }
                val total = weights.sum().toDouble()
                var acc = 0
                for ((i, t) in tokens.withIndex()) {
                    val ws = startMs + (spanMs * acc / total).toLong()
                    acc += weights[i]
                    val we = startMs + (spanMs * (acc - 1) / total).toLong()
                    out += Word(t, ws, maxOf(ws, we), CONFIDENCE)
                }
            }
            return out
        }

        /** 1 s of 16 kHz mono 16-bit silence as a WAV file. */
        fun silentWav(sampleRate: Int = 16_000): ByteArray {
            val dataBytes = sampleRate * 2
            return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
                putShort(2); putShort(16)
                put("data".toByteArray()); putInt(dataBytes)
            }.array()
        }
    }
}

/** Cuts an ADTS AAC stream into pieces of whole frames, each at most a given length (ADTS frames stand alone). */
object AdtsChunker {
    data class Piece(val bytes: ByteArray, val startMs: Long, val durationMs: Long)

    private val RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun split(data: ByteArray, maxMs: Long): List<Piece> {
        val pieces = mutableListOf<Piece>()
        var pos = 0
        var pieceStart = 0
        var pieceSamples = 0L
        var doneSamples = 0L
        var rate = 16_000
        while (pos + 7 <= data.size) {
            val sync = (data[pos].toInt() and 0xFF) == 0xFF && (data[pos + 1].toInt() and 0xF0) == 0xF0
            val len = ((data[pos + 3].toInt() and 0x03) shl 11) or ((data[pos + 4].toInt() and 0xFF) shl 3) or ((data[pos + 5].toInt() and 0xE0) shr 5)
            if (!sync || len < 7 || pos + len > data.size) break
            rate = RATES.getOrElse((data[pos + 2].toInt() shr 2) and 0x0F) { 16_000 }
            val blocks = (data[pos + 6].toInt() and 0x03) + 1
            val frameSamples = 1024L * blocks
            if (pieceSamples > 0 && (pieceSamples + frameSamples) * 1000 / rate > maxMs) {
                pieces += Piece(data.copyOfRange(pieceStart, pos), doneSamples * 1000 / rate, pieceSamples * 1000 / rate)
                doneSamples += pieceSamples
                pieceStart = pos
                pieceSamples = 0
            }
            pieceSamples += frameSamples
            pos += len
        }
        if (pieceSamples > 0) pieces += Piece(data.copyOfRange(pieceStart, pos), doneSamples * 1000 / rate, pieceSamples * 1000 / rate)
        return pieces
    }
}
