package io.github.ukpratik.mukhar.data.net

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.work.FeedbackRequest
import io.github.ukpratik.mukhar.data.work.RemoteAnalysisPipeline
import io.github.ukpratik.mukhar.data.work.ReportRequest
import io.github.ukpratik.mukhar.data.work.TranscribeRequest
import io.github.ukpratik.mukhar.domain.metrics.MetricsCalculator
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

/** The client side of the Worker API v1 contract (LLD §3), against a local mock server. */
class RemoteAnalysisPipelineTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
    private lateinit var pipeline: RemoteAnalysisPipeline

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(HeadersInterceptor({ 12 }) { "id-token" to "session-token" }).build()
        val api = Retrofit.Builder().baseUrl(server.url("/v1/")).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(MukharApi::class.java)
        pipeline = RemoteAnalysisPipeline(api, ErrorMapper(json))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun transcribeSendsMultipartMetaAndAudioAndParsesWords() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"session_id":"srv-1","provider":"deepgram","model":"nova-3","duration_ms":117350,
                    "words":[{"w":"तो","s":420,"e":610,"c":0.93},{"w":"hi","s":700,"e":900,"c":0.4}],
                    "quota":{"day":"2026-11-02","talks_used":1,"talks_limit":1},"request_id":"r"}""",
            ),
        )
        val audio = File.createTempFile("talk", ".aac").apply { writeBytes(ByteArray(64) { 1 }) }
        val r = pipeline.transcribe(TranscribeRequest("c-1", "talk", null, Lang.HI, 117_400, queued = false), audio)
        val ok = (r as Outcome.Ok).value
        assertThat(ok.serverSessionId).isEqualTo("srv-1")
        assertThat(ok.words).containsExactly(Word("तो", 420, 610, 0.93), Word("hi", 700, 900, 0.4)).inOrder()

        val req = server.takeRequest()
        assertThat(req.url.encodedPath).isEqualTo("/v1/transcribe")
        assertThat(req.headers["Authorization"]).isEqualTo("Bearer id-token")
        assertThat(req.headers["X-Mukhar-Session"]).isEqualTo("session-token")
        assertThat(req.headers["X-App-Version"]).isEqualTo("12")
        assertThat(req.headers["X-Request-Id"]).isNotEmpty()
        val body = req.body!!.utf8()
        assertThat(body).contains("name=\"meta\"")
        assertThat(body).contains("\"client_session_id\":\"c-1\"")
        assertThat(body).contains("\"parent_session_id\":null")
        assertThat(body).contains("\"duration_ms\":117400")
        assertThat(body).contains("name=\"audio\"; filename=\"talk.aac\"")
        assertThat(body).contains("Content-Type: audio/aac")
    }

    @Test
    fun feedbackSendsIndexedTokensWithLowConfidenceMarked() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"prompt_version":"fb-2026-11-01a","model":"claude-haiku-5-5","feedback":{"summary":"Good.",
                    "glow":{"start":0,"end":1,"quote":"So I","note":"n"},"grow":null,"better_versions":[],"mistakes":[],"safety":"none"},
                    "dropped":1,"fallback":{"glow":false,"grow":true},"safety":"none","request_id":"r"}""",
            ),
        )
        val words = listOf(Word("So", 0, 200, 0.9), Word("I", 300, 400, 0.95), Word("Zuora", 500, 900, 0.3))
        val metrics = MetricsCalculator.compute(words, Lang.EN)
        val r = pipeline.feedback(FeedbackRequest("srv-1", Lang.EN, "interviews", "Tell me about…", false, words, setOf(2), metrics, 118_000))
        val ok = (r as Outcome.Ok).value
        assertThat(ok.fallbackGrow).isTrue()
        assertThat(ok.feedback.glow!!.quote).isEqualTo("So I")
        assertThat(ok.feedback.grow).isNull()

        val sent = json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(sent["session_id"]!!.jsonPrimitive.content).isEqualTo("srv-1")
        val tokens = sent["tokens"]!!.jsonArray
        assertThat(tokens[2].jsonObject["lc"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(tokens[0].jsonObject["i"]!!.jsonPrimitive.content).isEqualTo("0")
        assertThat(sent["metrics"]!!.jsonObject["speech_s"]!!.jsonPrimitive.content).isEqualTo("118")
        assertThat(sent["stutter_friendly"]!!.jsonPrimitive.content).isEqualTo("false")
    }

    @Test
    fun errorsBecomeAppErrors() = runTest {
        server.enqueue(MockResponse(code = 502, body = """{"error":{"code":"LLM_FAILED","message":"x"},"request_id":"r"}"""))
        val words = listOf(Word("So", 0, 200, 0.9))
        val r = pipeline.feedback(FeedbackRequest("srv-1", Lang.EN, "t", "p", false, words, emptySet(), MetricsCalculator.compute(words, Lang.EN), 1_000))
        assertThat((r as Outcome.Err).error).isEqualTo(AppError.Upstream(Stage.LLM, "LLM_FAILED"))
    }

    @Test
    fun reportSendsTheTranscriptOnlyWithConsent() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"report_id":"rep-1"}"""))
        server.enqueue(MockResponse(code = 200, body = """{"report_id":"rep-2"}"""))
        val words = listOf(Word("So", 0, 200, 0.9))
        pipeline.report(ReportRequest("srv-1", "wrong_advice", false, Lang.EN, "fb-1", words, emptySet(), null, "note"))
        pipeline.report(ReportRequest("srv-1", "wrong_advice", true, Lang.EN, "fb-1", words, emptySet(), null, "note"))
        val without = json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(without["consent"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(without["payload"].toString()).isEqualTo("null")
        val with = json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(with["payload"]!!.jsonObject["tokens"]!!.jsonArray).hasSize(1)
    }
}
