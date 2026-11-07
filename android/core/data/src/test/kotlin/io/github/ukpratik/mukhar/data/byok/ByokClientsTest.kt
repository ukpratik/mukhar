package io.github.ukpratik.mukhar.data.byok

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Deepgram, Sarvam and Anthropic clients against a local mock server: request shape, parsing, errors, key checks. */
class ByokClientsTest {
    private val server = MockWebServer()
    private val logs = mutableListOf<String>()
    private lateinit var http: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        http = OkHttpClient.Builder().addInterceptor(SafeLoggingInterceptor({ logs += it }, logHeaders = true))
            .readTimeout(5, TimeUnit.SECONDS).build()
    }

    @After
    fun tearDown() = server.close()

    private fun ok(body: String, headers: Map<String, String> = emptyMap()) = MockResponse.Builder().code(200).body(body)
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()

    private fun status(code: Int, body: String = "{}", retryAfter: String? = null) = MockResponse.Builder().code(code).body(body)
        .apply { if (retryAfter != null) addHeader("Retry-After", retryAfter) }.build()

    private fun audio(bytes: ByteArray = ByteArray(64) { 1 }) = File.createTempFile("talk", ".aac").apply { writeBytes(bytes); deleteOnExit() }

    // ---- Deepgram ----

    private val deepgram get() = DeepgramClient(http, testJson, server.url("/"))

    @Test
    fun deepgramSendsTokenAuthAndRawAacAndParsesWords() = runBlocking<Unit> {
        server.enqueue(
            ok(
                """{"metadata":{"duration":12.5},"results":{"channels":[{"alternatives":[{"transcript":"so um I led",
                "words":[{"word":"so","start":0.42,"end":0.61,"confidence":0.93,"punctuated_word":"So"},
                {"word":"um","start":0.7,"end":0.9,"confidence":0.41},{"word":"led","start":1.0,"end":1.2,"confidence":1.2}]}]}]}}""",
            ),
        )
        val r = deepgram.transcribe("dg-SECRET-key", audio(), Lang.EN) as ByokResult.Ok
        assertThat(r.value.words.map { it.w }).containsExactly("So", "um", "led").inOrder()
        assertThat(r.value.words[0].startMs).isEqualTo(420)
        assertThat(r.value.words[0].endMs).isEqualTo(610)
        assertThat(r.value.words[2].conf).isEqualTo(1.0)
        assertThat(r.value.audioMs).isEqualTo(12_500)
        assertThat(r.value.provider).isEqualTo("deepgram")

        val req = server.takeRequest()
        assertThat(req.url.encodedPath).isEqualTo("/v1/listen")
        assertThat(req.url.queryParameter("model")).isEqualTo("nova-3")
        assertThat(req.url.queryParameter("language")).isEqualTo("en-IN")
        assertThat(req.url.queryParameter("filler_words")).isEqualTo("true")
        assertThat(req.url.queryParameter("punctuate")).isEqualTo("true")
        assertThat(req.headers["Authorization"]).isEqualTo("Token dg-SECRET-key")
        assertThat(req.headers["Content-Type"]).startsWith("audio/aac")
        assertThat(req.body!!.size).isEqualTo(64)
    }

    @Test
    fun deepgramLanguageCodes() {
        assertThat(DeepgramClient.language(Lang.HI, codeMixed = false)).isEqualTo("hi")
        assertThat(DeepgramClient.language(Lang.HI, codeMixed = true)).isEqualTo("multi")
        assertThat(DeepgramClient.language(Lang.EN, codeMixed = false)).isEqualTo("en-IN")
    }

    @Test
    fun deepgramErrorsMap() = runBlocking<Unit> {
        server.enqueue(status(401))
        server.enqueue(status(429, retryAfter = "30"))
        server.enqueue(status(503))
        server.enqueue(ok("""{"results":{"channels":[]}}"""))
        server.enqueue(ok("not json"))
        assertThat((deepgram.transcribe("k", audio(), Lang.EN) as ByokResult.Fail).error).isEqualTo(ByokError.Unauthorized)
        assertThat((deepgram.transcribe("k", audio(), Lang.EN) as ByokResult.Fail).error).isEqualTo(ByokError.RateLimited(30))
        assertThat((deepgram.transcribe("k", audio(), Lang.EN) as ByokResult.Fail).error).isEqualTo(ByokError.Server(503))
        assertThat((deepgram.transcribe("k", audio(), Lang.EN) as ByokResult.Fail).error).isInstanceOf(ByokError.BadResponse::class.java)
        assertThat((deepgram.transcribe("k", audio(), Lang.EN) as ByokResult.Fail).error).isInstanceOf(ByokError.BadResponse::class.java)
    }

    @Test
    fun deepgramOfflineIsOffline() = runBlocking<Unit> {
        server.close()
        assertThat((deepgram.transcribe("k", audio(), Lang.EN) as ByokResult.Fail).error).isEqualTo(ByokError.Offline)
        assertThat(deepgram.check("k")).isEqualTo(KeyCheck.OFFLINE)
    }

    @Test
    fun deepgramKeyCheck() = runBlocking<Unit> {
        server.enqueue(ok("""{"projects":[]}"""))
        server.enqueue(status(401))
        server.enqueue(status(403))
        server.enqueue(status(500))
        assertThat(deepgram.check("k")).isEqualTo(KeyCheck.OK)
        assertThat(deepgram.check("k")).isEqualTo(KeyCheck.INVALID)
        assertThat(deepgram.check("k")).isEqualTo(KeyCheck.LIMITED)
        assertThat(deepgram.check("k")).isEqualTo(KeyCheck.ERROR)
        val req = server.takeRequest()
        assertThat(req.method).isEqualTo("GET")
        assertThat(req.url.encodedPath).isEqualTo("/v1/projects")
        assertThat(KeyCheck.LIMITED.usable).isTrue()
    }

    // ---- Sarvam ----

    private val sarvam get() = SarvamClient(http, testJson, server.url("/"))

    /** [frames] ADTS frames of 16 kHz mono AAC-LC (64 ms each) with tiny payloads. */
    private fun adts(frames: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        repeat(frames) {
            out.write(io.github.ukpratik.mukhar.data.audio.AdtsWriter.header(4))
            out.write(byteArrayOf(1, 2, 3, 4))
        }
        return out.toByteArray()
    }

    @Test
    fun adtsChunkerCutsAtFrameBoundaries() {
        val data = adts(1000) // 64 s
        val pieces = AdtsChunker.split(data, 29_000)
        assertThat(pieces).hasSize(3)
        assertThat(pieces.sumOf { it.bytes.size }).isEqualTo(data.size)
        assertThat(pieces.all { it.durationMs <= 29_000 }).isTrue()
        assertThat(pieces[1].startMs).isEqualTo(pieces[0].durationMs)
        assertThat(pieces.last().let { it.startMs + it.durationMs }).isEqualTo(64_000)
        // Every piece starts with an ADTS sync word.
        assertThat(pieces.all { (it.bytes[0].toInt() and 0xFF) == 0xFF }).isTrue()
    }

    @Test
    fun sarvamSendsChunksWithTheSubscriptionKeyAndInterpolatesTimes() = runBlocking<Unit> {
        val first = """{"transcript":"तो आज मैं","timestamps":{"words":["तो आज","मैं"],"start_time_seconds":[0.0,2.0],"end_time_seconds":[1.5,3.0]}}"""
        val second = """{"transcript":"बताऊंगी","timestamps":{"words":["बताऊंगी"],"start_time_seconds":[1.0],"end_time_seconds":[2.0]}}"""
        server.enqueue(ok(first))
        server.enqueue(ok(second))
        val r = sarvam.transcribe("sv-SECRET", audio(adts(600)), Lang.HI) as ByokResult.Ok // 38.4 s → 2 pieces
        val ws = r.value.words
        assertThat(ws.map { it.w }).containsExactly("तो", "आज", "मैं", "बताऊंगी").inOrder()
        assertThat(ws[0].startMs).isEqualTo(0)
        assertThat(ws[1].startMs).isGreaterThan(ws[0].startMs)
        assertThat(ws[1].endMs).isAtMost(1_500)
        assertThat(ws[2].startMs).isEqualTo(2_000)
        val pieceOne = AdtsChunker.split(adts(600), SarvamClient.MAX_CHUNK_MS)[0].durationMs
        assertThat(ws[3].startMs).isEqualTo(pieceOne + 1_000)
        assertThat(ws.all { it.conf == SarvamClient.CONFIDENCE }).isTrue()
        assertThat(r.value.provider).isEqualTo("sarvam")

        val req = server.takeRequest()
        assertThat(req.url.encodedPath).isEqualTo("/speech-to-text")
        assertThat(req.headers["api-subscription-key"]).isEqualTo("sv-SECRET")
        val body = req.body!!.utf8()
        assertThat(body).contains("name=\"model\"")
        assertThat(body).contains("saaras:v3")
        assertThat(body).contains("verbatim")
        assertThat(body).contains("hi-IN")
        assertThat(body).contains("name=\"with_timestamps\"")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun sarvamWithoutTimestampsSpreadsTheTranscriptOverThePiece() {
        val ws = SarvamClient.interpolate(SarvamClient.SyncResponse("एक दो तीन", null), 10_000, 3_000)
        assertThat(ws).hasSize(3)
        assertThat(ws.first().startMs).isEqualTo(10_000)
        assertThat(ws.last().endMs).isAtMost(13_000)
    }

    @Test
    fun sarvamErrorsAndKeyCheck() = runBlocking<Unit> {
        server.enqueue(status(403))
        assertThat((sarvam.transcribe("k", audio(adts(10)), Lang.HI) as ByokResult.Fail).error).isEqualTo(ByokError.Forbidden)
        server.enqueue(ok("""{"transcript":""}"""))
        server.enqueue(status(403))
        server.enqueue(status(429))
        server.enqueue(status(400))
        assertThat(sarvam.check("k")).isEqualTo(KeyCheck.OK)
        assertThat(sarvam.check("k")).isEqualTo(KeyCheck.INVALID)
        assertThat(sarvam.check("k")).isEqualTo(KeyCheck.LIMITED)
        assertThat(sarvam.check("k")).isEqualTo(KeyCheck.OK)
        server.takeRequest()
        val check = server.takeRequest()
        assertThat(check.body!!.utf8()).contains("filename=\"check.wav\"")
        assertThat(SarvamClient.silentWav().size).isEqualTo(44 + 32_000)
    }

    // ---- Anthropic ----

    private val anthropic get() = AnthropicClient(http, testJson, server.url("/"))

    private val talk = words("So um I led a team of five people and we shipped the app in three months")

    private fun message(text: String, stop: String = "end_turn") = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("model", JsonPrimitive("claude-haiku-5-5"))
            put("stop_reason", JsonPrimitive(stop))
            put(
                "content",
                buildJsonArray {
                    add(buildJsonObject { put("type", JsonPrimitive("thinking")); put("thinking", JsonPrimitive("")) })
                    add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(text)) })
                },
            )
            put("usage", buildJsonObject {
                put("input_tokens", JsonPrimitive(1200))
                put("cache_read_input_tokens", JsonPrimitive(800))
                put("output_tokens", JsonPrimitive(300))
            })
        },
    )

    private val validFeedback = """{"summary":"Clear story.","glow":{"start":11,"end":16,"quote":"shipped the app in three months","note":"A real result."},
        "grow":{"start":0,"end":1,"quote":"So um","issue":"filler","why":"Fillers.","how":"Pause."},
        "better_versions":[{"start":2,"end":8,"quote":"I led a team of five people","rewrite":"I spearheaded a team of five engineers",
        "upgraded_words":[{"word":"spearheaded","meaning":"led from the front","register":"power"}]}],"mistakes":[],"safety":"none"}"""

    @Test
    fun anthropicRequestShapeAndParsing() = runBlocking<Unit> {
        server.enqueue(ok(message(validFeedback)))
        val r = anthropic.feedback("sk-ant-SECRET", feedbackRequest(talk)) as ByokResult.Ok
        val f = r.value.feedback
        assertThat(f.glow!!.quote).isEqualTo("shipped the app in three months")
        assertThat(f.grow!!.issue).isEqualTo(GrowIssue.FILLER)
        assertThat(f.betterVersions.single().upgradedWords.single().register).isEqualTo(Register.POWER)
        assertThat(f.safety).isEqualTo(Safety.NONE)
        assertThat(r.value.usage).isEqualTo(LlmUsage(2000, 300))

        val req = server.takeRequest()
        assertThat(req.url.encodedPath).isEqualTo("/v1/messages")
        assertThat(req.headers["x-api-key"]).isEqualTo("sk-ant-SECRET")
        assertThat(req.headers["anthropic-version"]).isEqualTo("2023-06-01")
        assertThat(req.headers["anthropic-dangerous-direct-browser-access"]).isNull()
        val body = testJson.parseToJsonElement(req.body!!.utf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("claude-haiku-5-5")
        assertThat(body).doesNotContainKey("temperature")
        val oc = body["output_config"]!!.jsonObject
        assertThat(oc["effort"]!!.jsonPrimitive.content).isEqualTo("low")
        assertThat(oc["format"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        assertThat(oc["format"]!!.jsonObject["schema"]).isEqualTo(FeedbackPrompt.FEEDBACK_SCHEMA)
        val system = body["system"]!!.jsonArray[0].jsonObject
        assertThat(system["text"]!!.jsonPrimitive.content).startsWith("You are Mukhar")
        assertThat(system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("ephemeral")
        val user = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertThat(user).contains("lang: en")
        assertThat(user).contains("<transcript>[0]So [1]um [2]I")
        assertThat(user).contains("\"wpm_band\"")
    }

    @Test
    fun anthropicStopReasonsAndErrors() = runBlocking<Unit> {
        server.enqueue(ok(message("{}", stop = "refusal")))
        server.enqueue(ok(message(validFeedback.take(40), stop = "max_tokens")))
        server.enqueue(ok(message("""{"summary":"x"}""")))
        server.enqueue(ok(message(validFeedback.replace("\"filler\"", "\"accent\""))))
        server.enqueue(status(401))
        server.enqueue(status(429, retryAfter = "12"))
        server.enqueue(status(529))
        val req = feedbackRequest(talk)
        assertThat((anthropic.feedback("k", req) as ByokResult.Fail).error).isEqualTo(ByokError.Refused)
        assertThat((anthropic.feedback("k", req) as ByokResult.Fail).error).isEqualTo(ByokError.Truncated)
        assertThat((anthropic.feedback("k", req) as ByokResult.Fail).error).isInstanceOf(ByokError.BadResponse::class.java)
        assertThat((anthropic.feedback("k", req) as ByokResult.Fail).error).isInstanceOf(ByokError.BadResponse::class.java)
        assertThat((anthropic.feedback("k", req) as ByokResult.Fail).error).isEqualTo(ByokError.Unauthorized)
        assertThat((anthropic.feedback("k", req) as ByokResult.Fail).error).isEqualTo(ByokError.RateLimited(12))
        assertThat((anthropic.feedback("k", req) as ByokResult.Fail).error).isEqualTo(ByokError.Server(503))
    }

    @Test
    fun anthropicKeyCheck() = runBlocking<Unit> {
        server.enqueue(ok("""{"id":"claude-haiku-5-5"}"""))
        server.enqueue(status(401))
        server.enqueue(status(404))
        assertThat(anthropic.check("k")).isEqualTo(KeyCheck.OK)
        assertThat(anthropic.check("k")).isEqualTo(KeyCheck.INVALID)
        assertThat(anthropic.check("k")).isEqualTo(KeyCheck.MODEL_UNAVAILABLE)
        val req = server.takeRequest()
        assertThat(req.method).isEqualTo("GET")
        assertThat(req.url.encodedPath).isEqualTo("/v1/models/claude-haiku-5-5")
        assertThat(req.headers["anthropic-version"]).isEqualTo("2023-06-01")
    }

    @Test
    fun anthropicSentenceCheck() = runBlocking<Unit> {
        server.enqueue(ok(message("""{"result":"almost","corrected":"I will elaborate.","example":"Let me elaborate.","note":"Close!"}""")))
        val r = anthropic.checkSentence("k", "en", "elaborate", "explain in detail", true, "I will elaborate on it") as ByokResult.Ok
        assertThat(r.value.result).isEqualTo("almost")
        val body = testJson.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(body["output_config"]!!.jsonObject["format"]!!.jsonObject["schema"]).isEqualTo(FeedbackPrompt.SENTENCE_SCHEMA)
    }

    // ---- Logs never carry keys ----

    @Test
    fun noKeyOrAuthHeaderValueReachesTheLogs() = runBlocking<Unit> {
        val secrets = listOf("dg-SECRET-0001", "sv-SECRET-0002", "sk-ant-SECRET-0003")
        server.enqueue(ok("""{"results":{"channels":[{"alternatives":[{"words":[]}]}]}}"""))
        server.enqueue(ok("""{"transcript":""}"""))
        server.enqueue(ok(message(validFeedback)))
        server.enqueue(status(401))
        deepgram.transcribe(secrets[0], audio(), Lang.EN)
        sarvam.check(secrets[1])
        anthropic.feedback(secrets[2], feedbackRequest(talk))
        anthropic.check(secrets[2])
        assertThat(logs).isNotEmpty()
        for (line in logs) {
            for (s in secrets) assertThat(line).doesNotContain(s)
            assertThat(line).doesNotContain("Token ")
        }
        // Header names are logged (redacted) so the test proves redaction ran, not that nothing was logged.
        assertThat(logs.joinToString("\n")).contains("Authorization: ${Redaction.MASK}")
        assertThat(logs.joinToString("\n")).contains("x-api-key: ${Redaction.MASK}")
        assertThat(logs.joinToString("\n")).contains("api-subscription-key: ${Redaction.MASK}")
    }

    @Test
    fun redactionMasksEverySensitiveHeaderWhateverTheCase() {
        val h = okhttp3.Headers.headersOf("AUTHORIZATION", "Token abc", "X-Api-Key", "xyz", "Api-Subscription-Key", "s1", "anthropic-version", "2023-06-01")
        val out = Redaction.headers(h)
        assertThat(out).doesNotContain("abc")
        assertThat(out).doesNotContain("xyz")
        assertThat(out).doesNotContain("s1")
        assertThat(out).contains("anthropic-version: 2023-06-01")
    }
}
