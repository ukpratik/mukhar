package io.github.ukpratik.mukhar.data.local

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.byok.AnthropicClient
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.DeepgramClient
import io.github.ukpratik.mukhar.data.byok.FakeMeter
import io.github.ukpratik.mukhar.data.byok.MapKeys
import io.github.ukpratik.mukhar.data.byok.SarvamClient
import io.github.ukpratik.mukhar.data.byok.feedbackRequest
import io.github.ukpratik.mukhar.data.byok.testJson
import io.github.ukpratik.mukhar.data.byok.words
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.util.IdGenerator
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.ByokAnalysisPipeline
import io.github.ukpratik.mukhar.data.work.FeedbackRequest
import io.github.ukpratik.mukhar.data.work.FeedbackResult
import io.github.ukpratik.mukhar.data.work.PipelineRouter
import io.github.ukpratik.mukhar.data.work.ReportRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckResult
import io.github.ukpratik.mukhar.data.work.TranscribeRequest
import io.github.ukpratik.mukhar.data.work.TranscribeResult
import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.coach.RuleCoach
import io.github.ukpratik.mukhar.domain.coach.SynonymBook
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.model.Word
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** PipelineRouter routing, the on-device pipeline, and own-key fallbacks (ADR-008). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalPipelinesTest {
    private val server = MockWebServer()
    private val routes = mutableMapOf<String, MockResponse>()
    private val audio = File.createTempFile("talk", ".aac").apply { writeBytes(ByteArray(32) { 1 }); deleteOnExit() }
    private var n = 0
    private val ids = IdGenerator { "id-${++n}" }

    /** An offline engine that's "installed" (what 2B provides) or not. */
    private class FakeTranscriber(var ready: Boolean, val words: List<Word>) : LocalTranscriber {
        var calls = 0
        override suspend fun transcribe(audio: File, lang: Lang, durationMs: Long): Outcome<List<Word>> {
            calls++
            return if (ready) Outcome.Ok(words) else Outcome.Err(AppError.EngineUnavailable)
        }
        override fun isReady(lang: Lang) = ready
        override fun modelId(lang: Lang) = "small-test"
    }

    private val talk = words("So um I led a team of five people and we shipped the app in three months")
    private val transcriber = FakeTranscriber(ready = false, words = talk)
    private val synonyms = SynonymSource { SynonymBook.empty(it) }
    private val local get() = LocalAnalysisPipeline(transcriber, synonyms, ids)
    private val keys = MapKeys()
    private val meter = FakeMeter()

    private fun byok(): ByokAnalysisPipeline {
        val http = OkHttpClient()
        val base = server.url("/")
        return ByokAnalysisPipeline(
            keys, DeepgramClient(http, testJson, base), SarvamClient(http, testJson, base), AnthropicClient(http, testJson, base),
            local, transcriber, meter, ids,
        )
    }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = routes[request.url.encodedPath] ?: MockResponse(code = 404)
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private val request = TranscribeRequest("c-1", "talk", null, Lang.EN, 62_000, queued = false)

    private val deepgramOk = MockResponse(
        code = 200,
        body = """{"metadata":{"duration":60.0},"results":{"channels":[{"alternatives":[{"words":[
            {"word":"hello","start":0.1,"end":0.4,"confidence":0.9},{"word":"world","start":0.5,"end":0.9,"confidence":0.9}]}]}]}}""",
    )

    private fun claude(text: String) = MockResponse(
        code = 200,
        body = """{"model":"claude-haiku-5-5","stop_reason":"end_turn","content":[{"type":"text","text":${testJson.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(text))}}],
            "usage":{"input_tokens":1000,"output_tokens":200}}""",
    )

    private val grounded = """{"summary":"Clear.","glow":{"start":11,"end":16,"quote":"shipped the app in three months","note":"Result."},
        "grow":{"start":0,"end":1,"quote":"So um","issue":"filler","why":"w","how":"h"},"better_versions":[],"mistakes":[],"safety":"none"}"""

    // ---- Router ----

    private class Recording(val name: String, val log: MutableList<String>) : AnalysisPipeline {
        override suspend fun transcribe(request: TranscribeRequest, audio: File): Outcome<TranscribeResult> {
            log += "$name.transcribe"; return Outcome.Err(AppError.Offline)
        }
        override suspend fun feedback(request: FeedbackRequest): Outcome<FeedbackResult> {
            log += "$name.feedback"; return Outcome.Err(AppError.Offline)
        }
        override suspend fun report(request: ReportRequest): Outcome<Unit> {
            log += "$name.report"; return Outcome.Ok(Unit)
        }
        override suspend fun checkSentence(request: SentenceCheckRequest, audio: File): Outcome<SentenceCheckResult> {
            log += "$name.check"; return Outcome.Err(AppError.Offline)
        }
    }

    @Test
    fun routerPicksThePipelinePerCallAndCloudRunsOnThePhone() = runBlocking<Unit> {
        val log = mutableListOf<String>()
        var mode = AnalysisMode.ON_DEVICE
        val router = PipelineRouter({ mode }, Recording("local", log), Recording("byok", log), Recording("cloud", log))
        router.transcribe(request, audio)
        mode = AnalysisMode.OWN_KEYS
        router.feedback(feedbackRequest(talk))
        router.checkSentence(SentenceCheckRequest("s", Lang.EN, "lead", "guide", 3_000), audio)
        mode = AnalysisMode.CLOUD // stored somehow: Mukhar Cloud is off in R1
        router.report(ReportRequest("x", "wrong", false, Lang.EN, null, emptyList(), emptySet(), null, null))
        assertThat(log).containsExactly("local.transcribe", "byok.feedback", "byok.check", "local.report").inOrder()
    }

    // ---- On this phone ----

    @Test
    fun localWithoutAModelSaysEngineUnavailable() = runBlocking<Unit> {
        assertThat(local.transcribe(request, audio)).isEqualTo(Outcome.Err(AppError.EngineUnavailable))
        assertThat(UnavailableLocalTranscriber.isReady(Lang.HI)).isFalse()
        assertThat(UnavailableLocalTranscriber.transcribe(audio, Lang.HI, 1)).isEqualTo(Outcome.Err(AppError.EngineUnavailable))
    }

    @Test
    fun localMakesItsOwnSessionIdAndRuleCoachNotes() = runBlocking<Unit> {
        transcriber.ready = true
        val t = (local.transcribe(request, audio) as Outcome.Ok).value
        assertThat(t.serverSessionId).isEqualTo("local-id-1")
        assertThat(t.provider).isEqualTo("vosk")
        assertThat(t.model).isEqualTo("small-test")
        val f = (local.feedback(feedbackRequest(talk, provider = "vosk")) as Outcome.Ok).value
        assertThat(f.model).isEqualTo(RuleCoach.MODEL)
        assertThat(f.validatedOnDevice).isTrue()
        assertThat(f.feedback.betterVersions).isEmpty()
        assertThat(f.feedback.mistakes).isEmpty()
        assertThat(local.report(ReportRequest("local-id-1", "wrong", true, Lang.EN, null, talk, emptySet(), f.feedback, null))).isEqualTo(Outcome.Ok(Unit))
    }

    @Test
    fun localSentenceCheckIsWordFoundAndFiveWords() = runBlocking<Unit> {
        val req = SentenceCheckRequest("s", Lang.EN, "shipped", "sent out", 3_000)
        transcriber.ready = true
        assertThat((local.checkSentence(req, audio) as Outcome.Ok).value.result).isEqualTo("ok")
        assertThat(LocalAnalysisPipeline.sentenceVerdict(req, listOf("we", "shipped", "it")).result).isEqualTo("no")
        val missing = LocalAnalysisPipeline.sentenceVerdict(req, "we sent the app out today".split(" "))
        assertThat(missing.wordFound).isFalse()
        assertThat(missing.result).isEqualTo("no")
        assertThat(missing.corrected).isEmpty()
    }

    // ---- Own keys ----

    @Test
    fun noKeysAtAllRunsOnThePhone() = runBlocking<Unit> {
        transcriber.ready = true
        val t = (byok().transcribe(request, audio) as Outcome.Ok).value
        assertThat(t.provider).isEqualTo("vosk")
        val f = (byok().feedback(feedbackRequest(talk, provider = "vosk")) as Outcome.Ok).value
        assertThat(f.model).isEqualTo(RuleCoach.MODEL)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun sttKeyOnlyUsesVendorWordsAndTheOfflineCoach() = runBlocking<Unit> {
        keys.keys[ByokProvider.DEEPGRAM] = "dg"
        routes["/v1/listen"] = deepgramOk
        val p = byok()
        val t = (p.transcribe(request, audio) as Outcome.Ok).value
        assertThat(t.provider).isEqualTo("deepgram")
        assertThat(t.words.map { it.w }).containsExactly("hello", "world")
        assertThat(meter.records.single()).containsExactly(1L, 60_000L, 0L, 0L, 0L).inOrder()
        val f = (p.feedback(feedbackRequest(talk, id = t.serverSessionId)) as Outcome.Ok).value
        assertThat(f.model).isEqualTo(RuleCoach.MODEL)
        assertThat(f.feedback.notes).isEmpty()
    }

    @Test
    fun hindiPrefersSarvamWhenBothSttKeysExist() = runBlocking<Unit> {
        keys.keys[ByokProvider.DEEPGRAM] = "dg"
        keys.keys[ByokProvider.SARVAM] = "sv"
        routes["/speech-to-text"] = MockResponse(code = 200, body = """{"transcript":"नमस्ते दोस्तों"}""")
        val t = (byok().transcribe(request.copy(lang = Lang.HI), File.createTempFile("hindi", ".aac").apply {
            writeBytes(io.github.ukpratik.mukhar.data.audio.AdtsWriter.header(2) + byteArrayOf(1, 2))
        }) as Outcome.Ok).value
        assertThat(t.provider).isEqualTo("sarvam")
        assertThat(meter.records.single()[2]).isGreaterThan(0L)
    }

    @Test
    fun sttFailureFallsBackToTheOfflineEngineWithANote() = runBlocking<Unit> {
        keys.keys[ByokProvider.DEEPGRAM] = "dg"
        routes["/v1/listen"] = MockResponse(code = 401)
        transcriber.ready = true
        val p = byok()
        val t = (p.transcribe(request, audio) as Outcome.Ok).value
        assertThat(t.provider).isEqualTo("vosk")
        val f = (p.feedback(feedbackRequest(talk, provider = "vosk", id = t.serverSessionId)) as Outcome.Ok).value
        assertThat(f.feedback.notes).containsExactly(AnalysisNote.STT_KEY_REJECTED)

        routes["/v1/listen"] = MockResponse(code = 503)
        val t2 = (p.transcribe(request, audio) as Outcome.Ok).value
        val f2 = (p.feedback(feedbackRequest(talk, provider = "vosk", id = t2.serverSessionId)) as Outcome.Ok).value
        assertThat(f2.feedback.notes).containsExactly(AnalysisNote.STT_FALLBACK)
    }

    @Test
    fun sttFailureWithoutAnOfflineEngineKeepsTheTalkSaved() = runBlocking<Unit> {
        keys.keys[ByokProvider.DEEPGRAM] = "dg"
        routes["/v1/listen"] = MockResponse(code = 401)
        assertThat(byok().transcribe(request, audio)).isEqualTo(Outcome.Err(AppError.EngineUnavailable))
        routes["/v1/listen"] = MockResponse(code = 429)
        assertThat((byok().transcribe(request, audio) as Outcome.Err).error).isInstanceOf(AppError.Busy::class.java)
        server.close()
        assertThat(byok().transcribe(request, audio)).isEqualTo(Outcome.Err(AppError.Offline))
    }

    @Test
    fun llmKeyGivesGroundedClaudeNotesPlusOfflineWordUpgrades() = runBlocking<Unit> {
        keys.keys[ByokProvider.ANTHROPIC] = "sk"
        routes["/v1/messages"] = claude(grounded)
        val f = (byok().feedback(feedbackRequest(talk, provider = "deepgram")) as Outcome.Ok).value
        assertThat(f.model).isEqualTo("claude-haiku-5-5")
        assertThat(f.validatedOnDevice).isTrue()
        assertThat(f.feedback.glow!!.quote).isEqualTo("shipped the app in three months")
        assertThat(f.feedback.notes).isEmpty()
        assertThat(meter.records.single()).containsExactly(1L, 0L, 0L, 1000L, 200L).inOrder()
    }

    @Test
    fun llmOnOfflineWordsSaysSo() = runBlocking<Unit> {
        keys.keys[ByokProvider.ANTHROPIC] = "sk"
        routes["/v1/messages"] = claude(grounded)
        val f = (byok().feedback(feedbackRequest(talk, provider = "vosk")) as Outcome.Ok).value
        assertThat(f.feedback.notes).containsExactly(AnalysisNote.OFFLINE_TRANSCRIPT)
    }

    @Test
    fun ungroundedClaudeNotesAreReplacedByTheOfflineCoach() = runBlocking<Unit> {
        keys.keys[ByokProvider.ANTHROPIC] = "sk"
        routes["/v1/messages"] = claude(grounded.replace("shipped the app in three months", "built a rocket").replace("So um", "nothing here"))
        val f = (byok().feedback(feedbackRequest(talk)) as Outcome.Ok).value
        assertThat(f.fallbackGlow).isTrue()
        assertThat(f.feedback.glow).isNotNull()
        assertThat(f.feedback.glow!!.start).isEqualTo(-1) // metrics-based, from the offline coach
        assertThat(f.feedback.notes).contains(AnalysisNote.LLM_UNGROUNDED)
    }

    @Test
    fun anyClaudeFailureFallsBackToTheOfflineCoachWithANote() = runBlocking<Unit> {
        keys.keys[ByokProvider.ANTHROPIC] = "sk"
        val cases = listOf(
            MockResponse(code = 401) to AnalysisNote.LLM_KEY_REJECTED,
            MockResponse(code = 429) to AnalysisNote.LLM_FALLBACK,
            MockResponse(code = 200, body = """{"stop_reason":"refusal","content":[]}""") to AnalysisNote.LLM_FALLBACK,
            claude("not json") to AnalysisNote.LLM_FALLBACK,
        )
        for ((response, note) in cases) {
            routes["/v1/messages"] = response
            val f = (byok().feedback(feedbackRequest(talk)) as Outcome.Ok).value
            assertThat(f.model).isEqualTo(RuleCoach.MODEL)
            assertThat(f.feedback.notes).containsExactly(note)
        }
    }

    @Test
    fun theSoftCapKeepsTalksOnThePhone() = runBlocking<Unit> {
        keys.keys[ByokProvider.DEEPGRAM] = "dg"
        keys.keys[ByokProvider.ANTHROPIC] = "sk"
        meter.over = true
        transcriber.ready = true
        val p = byok()
        val t = (p.transcribe(request, audio) as Outcome.Ok).value
        val f = (p.feedback(feedbackRequest(talk, provider = "vosk", id = t.serverSessionId)) as Outcome.Ok).value
        assertThat(f.model).isEqualTo(RuleCoach.MODEL)
        assertThat(f.feedback.notes).containsExactly(AnalysisNote.SOFT_CAP_REACHED)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun ownKeySentenceCheckUsesClaudeWhenThereIsAKey() = runBlocking<Unit> {
        keys.keys[ByokProvider.DEEPGRAM] = "dg"
        keys.keys[ByokProvider.ANTHROPIC] = "sk"
        routes["/v1/listen"] = deepgramOk
        routes["/v1/messages"] = claude("""{"result":"almost","corrected":"c","example":"e","note":"n"}""")
        val r = (byok().checkSentence(SentenceCheckRequest("s", Lang.EN, "hello", "greeting", 3_000), audio) as Outcome.Ok).value
        assertThat(r.wordFound).isTrue()
        assertThat(r.result).isEqualTo("almost")
        assertThat(r.note).isEqualTo("n")
    }
}
