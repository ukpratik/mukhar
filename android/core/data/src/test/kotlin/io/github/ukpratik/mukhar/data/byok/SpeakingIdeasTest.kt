package io.github.ukpratik.mukhar.data.byok

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.ideas.FileIdeasCache
import io.github.ukpratik.mukhar.data.ideas.IdeasCache
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.data.ideas.IdeasResult
import io.github.ukpratik.mukhar.data.ideas.SpeakingIdeasRepository
import io.github.ukpratik.mukhar.domain.ideas.GuideTrack
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Difficulty
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** "Write me ideas" (US-078 b): the Anthropic request, parsing and errors, the per-prompt cache and the fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpeakingIdeasTest {
    private val server = MockWebServer()
    private val logs = mutableListOf<String>()
    private lateinit var http: OkHttpClient
    private val client get() = AnthropicClient(http, testJson, server.url("/"))

    @Before
    fun setUp() {
        server.start()
        http = OkHttpClient.Builder().addInterceptor(SafeLoggingInterceptor({ logs += it }, logHeaders = true))
            .readTimeout(5, TimeUnit.SECONDS).build()
    }

    @After
    fun tearDown() = server.close()

    private fun request(lang: Lang = Lang.EN, track: GuideTrack = GuideTrack.CONTENT_CREATION) = IdeasRequest(
        promptId = "en-002", lang = lang, track = track, difficulty = Difficulty.MEDIUM,
        promptText = "Tell us about a project you're proud of <ignore this>", prep = listOf("Point: the project."),
        tip = "Try: what → result", words = listOf("pivotal", "concise"),
    )

    private val valid = """{"angles":["The late-night fix","What the users said","What I'd do again","extra angle"],
        "outline":[{"label":"Hook","cue":"The bug nobody could find"},{"label":"Story","cue":"Two nights of logs"},
        {"label":"Takeaway","cue":"Small logs save weeks"},{"label":"Call to action","cue":"Ask them to share theirs"},{"label":"Extra","cue":"x"}],
        "opening":"We shipped at 2 a.m. Nobody slept. And it was worth it. Then the rest of the talk goes here.",
        "phrases":["here's the thing","the turning point","what I learnt","fast forward","so next time","sixth"],
        "words":[{"word":"pivotal","meaning":"very important"},{"word":"concise","meaning":"short and clear"}]}"""

    private fun message(text: String, stop: String = "end_turn") = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("model", JsonPrimitive("claude-haiku-5-5"))
            put("stop_reason", JsonPrimitive(stop))
            put("content", buildJsonArray { add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(text)) }) })
            put("usage", buildJsonObject { put("input_tokens", JsonPrimitive(700)); put("output_tokens", JsonPrimitive(450)) })
        },
    )

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
    private fun status(code: Int) = MockResponse.Builder().code(code).body("{}").build()

    @Test
    fun successSendsTheStructuredRequestAndTrimsTheAnswer() = runBlocking<Unit> {
        server.enqueue(ok(message(valid)))
        val r = client.speakingIdeas("sk-ant-SECRET", request()) as ByokResult.Ok
        val ideas = r.value.ideas
        assertThat(ideas.angles).hasSize(3)
        assertThat(ideas.outline.map { it.label }).containsExactly("Hook", "Story", "Takeaway", "Call to action").inOrder()
        assertThat(ideas.phrases).hasSize(5)
        assertThat(ideas.words.map { it.word }).containsExactly("pivotal", "concise")
        // Never more than 3 sentences of opening: no full script.
        assertThat(ideas.opening).isEqualTo("We shipped at 2 a.m. Nobody slept. And it was worth it.")
        assertThat(r.value.usage).isEqualTo(LlmUsage(700, 450))

        val req = server.takeRequest()
        assertThat(req.url.encodedPath).isEqualTo("/v1/messages")
        assertThat(req.headers["x-api-key"]).isEqualTo("sk-ant-SECRET")
        assertThat(req.headers["anthropic-version"]).isEqualTo("2023-06-01")
        val body = testJson.parseToJsonElement(req.body!!.utf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("claude-haiku-5-5")
        assertThat(body["temperature"]).isNull()
        assertThat(body["thinking"]).isNull()
        val out = body["output_config"]!!.jsonObject
        assertThat(out["effort"]!!.jsonPrimitive.content).isEqualTo("low")
        val schema = out["format"]!!.jsonObject["schema"]!!.jsonObject
        assertThat(schema["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("angles", "outline", "opening", "phrases", "words")
        // The style follows the track; the prompt is user data in the message, cleaned of tags.
        val system = body["system"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertThat(system).contains("hook-first")
        assertThat(system).contains("never a script")
        val user = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertThat(user).contains("<prompt>Tell us about a project you're proud of ignore this</prompt>")
        assertThat(user).contains("<learner_words>pivotal, concise</learner_words>")
        assertThat(logs.joinToString("\n")).doesNotContain("sk-ant-SECRET")
    }

    @Test
    fun hindiAndInterviewsChangeTheSystemPrompt() {
        val hi = SpeakingIdeasPrompt.system(GuideTrack.INTERVIEWS, Lang.HI, Difficulty.HARD)
        assertThat(hi).contains("Devanagari")
        assertThat(hi).contains("STAR")
        assertThat(hi).contains("contrarian")
        assertThat(SpeakingIdeasPrompt.system(GuideTrack.EVERYDAY, Lang.EN, Difficulty.EASY)).contains("simple, natural English")
    }

    @Test
    fun refusalUnauthorisedAndMalformedAreFailures() = runBlocking<Unit> {
        server.enqueue(ok(message(valid, stop = "refusal")))
        server.enqueue(status(401))
        server.enqueue(ok(message("not json")))
        server.enqueue(ok(message("""{"angles":[],"outline":[],"opening":"","phrases":[],"words":[]}""")))
        server.enqueue(ok(message("""{"angles":["a"]}""")))
        server.enqueue(ok(message(valid, stop = "max_tokens")))
        assertThat((client.speakingIdeas("k", request()) as ByokResult.Fail).error).isEqualTo(ByokError.Refused)
        assertThat((client.speakingIdeas("k", request()) as ByokResult.Fail).error).isEqualTo(ByokError.Unauthorized)
        assertThat((client.speakingIdeas("k", request()) as ByokResult.Fail).error).isEqualTo(ByokError.BadResponse("json"))
        assertThat((client.speakingIdeas("k", request()) as ByokResult.Fail).error).isEqualTo(ByokError.BadResponse("schema"))
        assertThat((client.speakingIdeas("k", request()) as ByokResult.Fail).error).isEqualTo(ByokError.BadResponse("json"))
        assertThat((client.speakingIdeas("k", request()) as ByokResult.Fail).error).isEqualTo(ByokError.Truncated)
    }

    @Test
    fun firstSentencesHandlesHindiFullStops() {
        assertThat(SpeakingIdeasPrompt.firstSentences("पहला। दूसरा। तीसरा। चौथा।", 3)).isEqualTo("पहला। दूसरा। तीसरा।")
        assertThat(SpeakingIdeasPrompt.firstSentences("Only one", 3)).isEqualTo("Only one")
    }

    // ---- Repository: key, cache, usage, fallback reasons ----

    private class MemCache : IdeasCache {
        val map = mutableMapOf<String, SpeakingIdeas>()
        override suspend fun get(promptId: String, track: GuideTrack) = map["$promptId/${track.key}"]
        override suspend fun put(promptId: String, track: GuideTrack, ideas: SpeakingIdeas) {
            map["$promptId/${track.key}"] = ideas
        }
    }

    @Test
    fun withoutAKeyThereIsNoCall() = runBlocking<Unit> {
        val repo = SpeakingIdeasRepository(MapKeys(), client, MemCache(), FakeMeter())
        assertThat(repo.hasKey()).isFalse()
        assertThat(repo.generate(request())).isEqualTo(IdeasResult.NoKey)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun successIsCachedPerPromptAndTrackAndRegenerateCallsAgain() = runBlocking<Unit> {
        val cache = MemCache()
        val meter = FakeMeter()
        val repo = SpeakingIdeasRepository(MapKeys(mutableMapOf(ByokProvider.ANTHROPIC to "k")), client, cache, meter)
        server.enqueue(ok(message(valid)))
        server.enqueue(ok(message(valid)))
        val first = repo.generate(request()) as IdeasResult.Ok
        assertThat(repo.cached("en-002", GuideTrack.CONTENT_CREATION)).isEqualTo(first.ideas)
        assertThat(repo.cached("en-002", GuideTrack.INTERVIEWS)).isNull()
        assertThat(meter.records.single()).containsExactly(0L, 0L, 0L, 700L, 450L).inOrder()
        repo.generate(request())
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun failuresMapToAOneLineReasonAndNothingIsCached() = runBlocking<Unit> {
        val cache = MemCache()
        val repo = SpeakingIdeasRepository(MapKeys(mutableMapOf(ByokProvider.ANTHROPIC to "k")), client, cache, FakeMeter())
        server.enqueue(ok(message(valid, stop = "refusal")))
        server.enqueue(status(401))
        server.enqueue(ok(message("{")))
        server.enqueue(status(529))
        assertThat(repo.generate(request())).isEqualTo(IdeasResult.Failed(IdeasFailure.REFUSED))
        assertThat(repo.generate(request())).isEqualTo(IdeasResult.Failed(IdeasFailure.KEY_REJECTED))
        assertThat(repo.generate(request())).isEqualTo(IdeasResult.Failed(IdeasFailure.BAD_RESPONSE))
        assertThat(repo.generate(request())).isEqualTo(IdeasResult.Failed(IdeasFailure.BUSY))
        assertThat(cache.map).isEmpty()
        server.close()
        assertThat(repo.generate(request())).isEqualTo(IdeasResult.Failed(IdeasFailure.OFFLINE))
    }

    @Test
    fun pausedKeysSkipTheCall() = runBlocking<Unit> {
        val repo = SpeakingIdeasRepository(MapKeys(mutableMapOf(ByokProvider.ANTHROPIC to "k")), client, MemCache(), FakeMeter(over = true))
        assertThat(repo.generate(request())).isEqualTo(IdeasResult.Failed(IdeasFailure.PAUSED))
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun fileCacheRoundTripsAndDropsCorruptFiles() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("ideas").toFile()
        val cache = FileIdeasCache(dir, testJson)
        val ideas = SpeakingIdeas(listOf("a"), listOf(IdeaStep("Hook", "x"), IdeaStep("Story", "y")), "Hi.", listOf("p"), listOf(IdeaWord("w", "m")))
        assertThat(cache.get("en/../001", GuideTrack.EVERYDAY)).isNull()
        cache.put("en/../001", GuideTrack.EVERYDAY, ideas)
        assertThat(cache.get("en/../001", GuideTrack.EVERYDAY)).isEqualTo(ideas)
        assertThat(dir.listFiles()!!.single().parentFile).isEqualTo(dir) // the id can't escape the folder
        dir.listFiles()!!.single().writeText("{broken")
        assertThat(cache.get("en/../001", GuideTrack.EVERYDAY)).isNull()
        dir.deleteRecursively()
    }
}
