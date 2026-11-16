package io.github.ukpratik.mukhar.data.dictionary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.byok.AnthropicClient
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.FakeMeter
import io.github.ukpratik.mukhar.data.byok.MapKeys
import io.github.ukpratik.mukhar.data.byok.WordExplanation
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.data.local.SynonymRepository
import io.github.ukpratik.mukhar.data.repo.assetCatalog
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
import java.io.File
import java.util.concurrent.TimeUnit

/** D-056 word search: the bundled dictionary, Mukhar's words first, romanised Hindi, English endings, and "Explain with AI". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val server = MockWebServer()
    private val keys = MapKeys()
    private val meter = FakeMeter()
    private val cache = MemCache()

    private fun repo(source: DictionarySource = store) = DictionaryRepository(
        source, assetCatalog(json), SynonymRepository({ File("src/main/assets/$it").readText() }, json), keys,
        AnthropicClient(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(), json, server.url("/")), cache, meter,
    )

    private val store by lazy {
        DictionaryStore(ApplicationProvider.getApplicationContext<Context>(), json) { name -> File("src/main/assets/$name").bufferedReader() }
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun theBundledDictionaryFindsEnglishHindiAndRomanisedHindi() = runBlocking<Unit> {
        val r = repo()
        // English: the exact word first, with Hindi words; a common prefix match follows.
        val happy = r.search("happy")
        assertThat(happy.first().word).isEqualTo("happy")
        assertThat(happy.first().other).contains("ख़ुश")
        assertThat(happy.map { it.word }).contains("happy-go-lucky")
        // While typing: the start of a word, most common first.
        val happ = r.search("happ").map { it.word }
        assertThat(happ.take(3)).containsExactly("happy", "happen", "happening")
        // Endings: "running" finds run; an irregular form from WordNet finds its word.
        assertThat(r.search("running").map { it.word }).contains("run")
        assertThat(r.search("happier").first().word).isEqualTo("happy")
        // Mukhar's own word comes first and carries both meanings.
        val articulate = r.search("articulate").first()
        assertThat(articulate.mukhar).isTrue()
        assertThat(articulate.other).isNotEmpty()
        // Hindi: with or without the nukta, and typed in Latin letters.
        assertThat(r.search("खुश").first().word).isEqualTo("ख़ुश")
        assertThat(r.search("khush").map { it.word }).contains("ख़ुश")
        assertThat(r.search("anjaam").map { it.word }).contains("अंजाम")
        // Inflected Hindi form → its word.
        assertThat(r.search("लड़कों").map { it.word }).contains("लड़का")
        assertThat(r.search("   ")).isEmpty()
        assertThat(r.search("qzxqzx")).isEmpty()

        val page = r.entry(happy.first().id)!!
        assertThat(page.dict!!.senses).isNotEmpty()
        assertThat(page.ipa).isNotNull()
        assertThat(page.better).isNotEmpty() // the synonym book has "happy"
        val saved = page.toLookupWord()!!
        assertThat(saved.meaning).isEqualTo(page.dict!!.senses.first().definition)
        assertThat(saved.gloss).contains("ख़ुश")
        assertThat(r.entry("hi:खुश")?.word).isEqualTo("ख़ुश")
        assertThat(r.entry("en:qzxqzx")).isNull()
    }

    @Test
    fun explainUsesTheOwnKeyOnceThenTheCache() = runBlocking<Unit> {
        val r = repo(EmptySource)
        val entry = LookupEntry.unknown(Lang.EN, "lucid")
        assertThat(r.explain(entry)).isEqualTo(ExplainResult.NoKey)

        keys.keys[ByokProvider.ANTHROPIC] = "sk-ant-SECRET"
        server.enqueue(ok(message(valid)))
        val first = r.explain(entry) as ExplainResult.Ok
        assertThat(first.explanation.meaningHi).isEqualTo("साफ़ और समझने में आसान")
        assertThat(first.explanation.examples).hasSize(2)
        assertThat(first.explanation.synonyms).hasSize(4)
        assertThat(meter.records).isNotEmpty()
        val sent = server.takeRequest()
        val body = json.parseToJsonElement(sent.body!!.utf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("claude-haiku-5-5")
        assertThat(sent.body!!.utf8()).contains("<word>lucid</word>")

        // Cached: no second call.
        assertThat(r.explain(entry)).isEqualTo(first)
        assertThat(server.requestCount).isEqualTo(1)

        // The AI's meaning is what's saved for a word only the AI knows.
        val word = entry.toLookupWord(first.explanation)!!
        assertThat(word.meaning).isEqualTo("Clear and easy to understand.")
        assertThat(word.gloss).isEqualTo("साफ़ और समझने में आसान")
        assertThat(entry.toLookupWord()).isNull()
    }

    @Test
    fun explainFailuresAndThePauseCap() = runBlocking<Unit> {
        keys.keys[ByokProvider.ANTHROPIC] = "k"
        val r = repo(EmptySource)
        meter.over = true
        assertThat(r.explain(LookupEntry.unknown(Lang.EN, "a"))).isEqualTo(ExplainResult.Failed(IdeasFailure.PAUSED))
        meter.over = false
        server.enqueue(MockResponse.Builder().code(401).body("{}").build())
        assertThat(r.explain(LookupEntry.unknown(Lang.EN, "b"))).isEqualTo(ExplainResult.Failed(IdeasFailure.KEY_REJECTED))
        server.enqueue(ok(message("""{"is_word":true,"did_you_mean":"","pos":"","meaning_en":"","meaning_hi":"","pronunciation":"","examples":[],"usage_note":"","synonyms":[]}""")))
        assertThat(r.explain(LookupEntry.unknown(Lang.EN, "c"))).isEqualTo(ExplainResult.Failed(IdeasFailure.BAD_RESPONSE))
    }

    private val valid = """{"is_word":true,"did_you_mean":"","pos":"adjective","meaning_en":"Clear and easy to understand.",
        "meaning_hi":"साफ़ और समझने में आसान","pronunciation":"LOO-sid",
        "examples":[{"sentence":"Her answer was lucid.","translation":"उसका जवाब साफ़ था।"},{"sentence":"A lucid plan.","translation":"एक साफ़ योजना।"},{"sentence":"x","translation":"y"}],
        "usage_note":"Slightly formal.","synonyms":["clear","plain","simple","transparent","coherent"]}"""

    private fun message(text: String) = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("model", JsonPrimitive("claude-haiku-5-5"))
            put("stop_reason", JsonPrimitive("end_turn"))
            put("content", buildJsonArray { add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(text)) }) })
            put("usage", buildJsonObject { put("input_tokens", JsonPrimitive(400)); put("output_tokens", JsonPrimitive(250)) })
        },
    )

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private object EmptySource : DictionarySource {
        override suspend fun exact(keys: List<String>) = emptyList<DictHit>()
        override suspend fun prefix(key: String, loose: Boolean, limit: Int) = emptyList<DictHit>()
    }

    private class MemCache : ExplainCache {
        val map = mutableMapOf<String, WordExplanation>()
        override suspend fun get(id: String) = map[id]
        override suspend fun put(id: String, explanation: WordExplanation) {
            map[id] = explanation
        }
    }
}
