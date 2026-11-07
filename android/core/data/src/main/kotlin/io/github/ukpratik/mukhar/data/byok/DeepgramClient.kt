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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/** Words from a vendor STT call, plus what to log for the cost estimate. */
data class SttResult(val words: List<Word>, val provider: String, val model: String, val audioMs: Long)

/**
 * Deepgram pre-recorded STT, called straight from the phone with the user's key (ADR-008):
 * `POST /v1/listen?model=nova-3&language=…&filler_words=true&punctuate=true`, `Authorization: Token <key>`, the raw
 * AAC (ADTS) recording as the body. Per-word start/end (seconds) and confidence map 1:1 to [Word].
 * `filler_words` only works for English; Hindi fillers come from our lexicon.
 */
class DeepgramClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val base: HttpUrl = BASE.toHttpUrl(),
) {
    suspend fun transcribe(key: String, audio: File, lang: Lang, codeMixed: Boolean = false): ByokResult<SttResult> {
        val url = base.newBuilder().addPathSegments("v1/listen")
            .addQueryParameter("model", MODEL)
            .addQueryParameter("language", language(lang, codeMixed))
            .addQueryParameter("filler_words", "true")
            .addQueryParameter("punctuate", "true")
            .build()
        val request = Request.Builder().url(url)
            .header("Authorization", "Token $key")
            .post(audio.asRequestBody(AAC))
            .build()
        return call(request) { body ->
            val r = json.decodeFromString(ListenResponse.serializer(), body)
            val alt = r.results?.channels?.firstOrNull()?.alternatives?.firstOrNull()
                ?: return@call ByokResult.Fail(ByokError.BadResponse("no_alternatives"))
            val words = alt.words.map { w ->
                val start = (w.start * 1000).toLong().coerceAtLeast(0)
                Word(w.punctuated?.ifBlank { null } ?: w.word, start, maxOf(start, (w.end * 1000).toLong()), w.confidence.coerceIn(0.0, 1.0))
            }
            val ms = r.metadata?.duration?.let { (it * 1000).toLong() } ?: words.lastOrNull()?.endMs ?: 0
            ByokResult.Ok(SttResult(words, PROVIDER, MODEL, ms))
        }
    }

    /** `GET /v1/projects`: 200 works, 401 invalid, 403 = a key without project scope (treated as working). */
    suspend fun check(key: String): KeyCheck {
        val request = Request.Builder().url(base.newBuilder().addPathSegments("v1/projects").build())
            .header("Authorization", "Token $key").get().build()
        return try {
            http.newCall(request).await().use { r ->
                when (r.code) {
                    in 200..299 -> KeyCheck.OK
                    401 -> KeyCheck.INVALID
                    403 -> KeyCheck.LIMITED
                    else -> KeyCheck.ERROR
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            KeyCheck.OFFLINE
        }
    }

    private suspend fun <T> call(request: Request, parse: (String) -> ByokResult<T>): ByokResult<T> = try {
        http.newCall(request).await().use { r ->
            if (!r.isSuccessful) return ByokResult.Fail(httpError(r.code, r.header("Retry-After")))
            parse(r.body.string())
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

    @Serializable
    private data class ListenResponse(val metadata: Metadata? = null, val results: Results? = null)

    @Serializable
    private data class Metadata(val duration: Double? = null)

    @Serializable
    private data class Results(val channels: List<Channel> = emptyList())

    @Serializable
    private data class Channel(val alternatives: List<Alternative> = emptyList())

    @Serializable
    private data class Alternative(val transcript: String = "", val words: List<DgWord> = emptyList())

    @Serializable
    private data class DgWord(
        val word: String,
        val start: Double,
        val end: Double,
        val confidence: Double = 0.0,
        @SerialName("punctuated_word") val punctuated: String? = null,
    )

    companion object {
        const val BASE = "https://api.deepgram.com/"
        const val MODEL = "nova-3"
        const val PROVIDER = "deepgram"
        private val AAC = "audio/aac".toMediaType()

        /** `en-IN` for English, `hi` for Hindi, `multi` for code-mixed Hinglish. */
        fun language(lang: Lang, codeMixed: Boolean): String = when {
            codeMixed -> "multi"
            lang == Lang.HI -> "hi"
            else -> "en-IN"
        }
    }
}
