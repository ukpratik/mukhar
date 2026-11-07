package io.github.ukpratik.mukhar.data.byok

import io.github.ukpratik.mukhar.data.work.FeedbackRequest
import io.github.ukpratik.mukhar.domain.feedback.BetterVersion
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.feedback.Glow
import io.github.ukpratik.mukhar.domain.feedback.Grow
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.Mistake
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class LlmUsage(val inputTokens: Long, val outputTokens: Long)

data class LlmFeedback(val feedback: Feedback, val model: String, val usage: LlmUsage)

data class LlmSentence(val result: String, val corrected: String, val example: String, val note: String, val usage: LlmUsage)

/**
 * Anthropic Messages API, straight from the phone with the user's key (ADR-008, LLD §4.3): `claude-haiku-5-5`,
 * structured JSON via `output_config.format` (json_schema) at effort `low`, the cached system prompt, no
 * `temperature`. Haiku 5.5 has no server-side refusal fallback, so `stop_reason: "refusal"` (and `max_tokens`)
 * is a failure the caller turns into the offline coach. No browser-CORS header is needed on Android.
 */
class AnthropicClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val base: HttpUrl = BASE.toHttpUrl(),
) {
    suspend fun feedback(key: String, request: FeedbackRequest): ByokResult<LlmFeedback> {
        val body = body(FeedbackPrompt.SYSTEM_PROMPT, FeedbackPrompt.userMessage(request), FeedbackPrompt.FEEDBACK_SCHEMA, MAX_TOKENS, cacheSystem = true)
        return message(key, body) { out, model, usage ->
            ByokResult.Ok(LlmFeedback(parseFeedback(out), model, usage))
        }
    }

    suspend fun checkSentence(key: String, lang: String, target: String, meaning: String, wordFound: Boolean, transcript: String): ByokResult<LlmSentence> {
        val user = FeedbackPrompt.sentenceMessage(lang, target, meaning, wordFound, transcript)
        val body = body(FeedbackPrompt.SENTENCE_SYSTEM_PROMPT, user, FeedbackPrompt.SENTENCE_SCHEMA, SENTENCE_MAX_TOKENS, cacheSystem = false)
        return message(key, body) { out, _, usage ->
            val o = out.jsonObject
            val result = o.str("result").takeIf { it in FeedbackPrompt.SENTENCE_RESULTS } ?: throw IllegalArgumentException("result")
            ByokResult.Ok(LlmSentence(result, o.str("corrected"), o.str("example"), o.str("note"), usage))
        }
    }

    /** `GET /v1/models/claude-haiku-5-5`: 200 works, 401 invalid, 404 the key can't use the model. Free. */
    suspend fun check(key: String): KeyCheck {
        val request = Request.Builder().url(base.newBuilder().addPathSegments("v1/models/${FeedbackPrompt.MODEL}").build())
            .headers(key).get().build()
        return try {
            http.newCall(request).await().use { r ->
                when (r.code) {
                    in 200..299 -> KeyCheck.OK
                    401 -> KeyCheck.INVALID
                    403, 404 -> KeyCheck.MODEL_UNAVAILABLE
                    else -> KeyCheck.ERROR
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            KeyCheck.OFFLINE
        }
    }

    private fun body(system: String, user: String, schema: JsonObject, maxTokens: Int, cacheSystem: Boolean): String = buildJsonObject {
        put("model", FeedbackPrompt.MODEL)
        put("max_tokens", maxTokens)
        put(
            "system",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", system)
                        if (cacheSystem) put("cache_control", buildJsonObject { put("type", "ephemeral") })
                    },
                )
            },
        )
        put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", user) }) })
        put(
            "output_config",
            buildJsonObject {
                put("effort", "low")
                put("format", buildJsonObject { put("type", "json_schema"); put("schema", schema) })
            },
        )
    }.toString()

    private fun Request.Builder.headers(key: String) = header("x-api-key", key)
        .header("anthropic-version", API_VERSION)

    private suspend fun <T> message(key: String, body: String, parse: (JsonElement, String, LlmUsage) -> ByokResult<T>): ByokResult<T> {
        val request = Request.Builder().url(base.newBuilder().addPathSegments("v1/messages").build())
            .headers(key)
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            http.newCall(request).await().use { r ->
                if (!r.isSuccessful) return ByokResult.Fail(httpError(if (r.code == OVERLOADED) 503 else r.code, r.header("retry-after")))
                val msg = json.parseToJsonElement(r.body.string()).jsonObject
                when (msg["stop_reason"]?.jsonPrimitive?.contentOrNull) {
                    "refusal" -> return ByokResult.Fail(ByokError.Refused)
                    "max_tokens" -> return ByokResult.Fail(ByokError.Truncated)
                }
                // Thinking blocks (empty by default) come first; the answer is the first text block.
                val text = msg["content"]?.jsonArray?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: return ByokResult.Fail(ByokError.BadResponse("no_text"))
                val usage = msg["usage"]?.jsonObject
                val tokens = LlmUsage(
                    inputTokens = listOf("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens")
                        .sumOf { usage?.get(it)?.jsonPrimitive?.longOrNull ?: 0L },
                    outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.longOrNull ?: 0L,
                )
                val model = msg["model"]?.jsonPrimitive?.contentOrNull ?: FeedbackPrompt.MODEL
                parse(json.parseToJsonElement(text), model, tokens)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ByokResult.Fail(ByokError.Offline)
        } catch (e: SerializationException) {
            ByokResult.Fail(ByokError.BadResponse("json"))
        } catch (e: IllegalArgumentException) {
            ByokResult.Fail(ByokError.BadResponse("schema"))
        }
    }

    companion object {
        const val BASE = "https://api.anthropic.com/"
        const val API_VERSION = "2023-06-01"
        const val MAX_TOKENS = 2000
        const val SENTENCE_MAX_TOKENS = 600
        private const val OVERLOADED = 529
        private val JSON = "application/json".toMediaType()

        /**
         * Strict structural parse of the model's JSON (mirrors `parseFeedback` in `worker/src/llm/schema.ts`): any
         * missing field or unexpected enum value throws, and the caller falls back to the offline coach.
         */
        fun parseFeedback(v: JsonElement): Feedback {
            val o = v as? JsonObject ?: throw IllegalArgumentException("feedback")
            val g = o["glow"] as? JsonObject ?: throw IllegalArgumentException("glow")
            val r = o["grow"] as? JsonObject ?: throw IllegalArgumentException("grow")
            return Feedback(
                summary = o.str("summary"),
                glow = Glow(g.int("start"), g.int("end"), g.str("quote"), g.str("note")),
                grow = Grow(r.int("start"), r.int("end"), r.str("quote"), GrowIssue.fromKey(r.oneOf("issue", FeedbackPrompt.ISSUES)), r.str("why"), r.str("how")),
                betterVersions = o.arr("better_versions").filterIsInstance<JsonObject>().map { b ->
                    BetterVersion(
                        b.int("start"), b.int("end"), b.str("quote"), b.str("rewrite"),
                        b.arr("upgraded_words").filterIsInstance<JsonObject>().map { w ->
                            UpgradedWord(w.str("word"), w.str("meaning"), Register.fromKey(w.oneOf("register", FeedbackPrompt.REGISTERS)))
                        },
                    )
                },
                mistakes = o.arr("mistakes").filterIsInstance<JsonObject>().map { m -> Mistake(m.int("start"), m.int("end"), m.str("quote"), m.str("fix")) },
                safety = Safety.fromKey(o.oneOf("safety", FeedbackPrompt.SAFETY)),
            )
        }

        private fun JsonObject.str(k: String): String =
            (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException(k)

        private fun JsonObject.int(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: throw IllegalArgumentException(k)

        private fun JsonObject.arr(k: String): JsonArray = this[k] as? JsonArray ?: throw IllegalArgumentException(k)

        private fun JsonObject.oneOf(k: String, allowed: List<String>): String = str(k).also { require(it in allowed) { k } }
    }
}
