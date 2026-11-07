package io.github.ukpratik.mukhar.data.ideas

import io.github.ukpratik.mukhar.data.byok.AnthropicClient
import io.github.ukpratik.mukhar.data.byok.ByokError
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.ByokResult
import io.github.ukpratik.mukhar.data.byok.IdeasRequest
import io.github.ukpratik.mukhar.data.byok.KeySource
import io.github.ukpratik.mukhar.data.byok.SpeakingIdeas
import io.github.ukpratik.mukhar.data.byok.UsageMeter
import io.github.ukpratik.mukhar.data.util.MukharLog
import io.github.ukpratik.mukhar.domain.ideas.GuideTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Why AI ideas couldn't be written; the sheet shows the free guide with a one-line note (US-078). */
enum class IdeasFailure { OFFLINE, KEY_REJECTED, REFUSED, BUSY, BAD_RESPONSE, PAUSED }

sealed interface IdeasResult {
    data class Ok(val ideas: SpeakingIdeas) : IdeasResult
    data object NoKey : IdeasResult
    data class Failed(val reason: IdeasFailure) : IdeasResult
}

/** Ideas cached per prompt and track ("Regenerate" overwrites). A seam for tests. */
interface IdeasCache {
    suspend fun get(promptId: String, track: GuideTrack): SpeakingIdeas?
    suspend fun put(promptId: String, track: GuideTrack, ideas: SpeakingIdeas)
}

/**
 * `files/ideas/<prompt>_<track>.json`. Not in Auto Backup (the backup rules include only Room and settings) and
 * wiped by "Delete all my data" (the folder is in the wiper's list).
 */
class FileIdeasCache(private val dir: File, private val json: Json) : IdeasCache {
    override suspend fun get(promptId: String, track: GuideTrack): SpeakingIdeas? = withContext(Dispatchers.IO) {
        val f = file(promptId, track)
        if (!f.exists()) return@withContext null
        try {
            json.decodeFromString(SpeakingIdeas.serializer(), f.readText())
        } catch (e: SerializationException) {
            f.delete()
            null
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun put(promptId: String, track: GuideTrack, ideas: SpeakingIdeas) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            file(promptId, track).writeText(json.encodeToString(SpeakingIdeas.serializer(), ideas))
        } catch (e: IOException) {
            MukharLog.i("ideas: cache write failed")
        }
    }

    private fun file(promptId: String, track: GuideTrack) = File(dir, "${promptId.replace(Regex("[^A-Za-z0-9_-]"), "_")}_${track.key}.json")

    companion object {
        const val DIR = "ideas"
    }
}

/**
 * AI speaking ideas with the user's own Anthropic key (US-078 b, ADR-008 BYOK, D-046). Each [generate] is one
 * Messages call (≈ ₹0.10 on the user's key, logged in this month's usage); results are cached per prompt and track.
 * The "pause my keys" soft cap applies here too. Nothing here is sent anywhere but Anthropic.
 */
class SpeakingIdeasRepository(
    private val keys: KeySource,
    private val client: AnthropicClient,
    private val cache: IdeasCache,
    private val usage: UsageMeter,
) {
    suspend fun hasKey(): Boolean = keys.key(ByokProvider.ANTHROPIC) != null

    suspend fun cached(promptId: String, track: GuideTrack): SpeakingIdeas? = cache.get(promptId, track)

    suspend fun generate(request: IdeasRequest): IdeasResult {
        val key = keys.key(ByokProvider.ANTHROPIC) ?: return IdeasResult.NoKey
        if (usage.overSoftCap()) return IdeasResult.Failed(IdeasFailure.PAUSED)
        return when (val r = client.speakingIdeas(key, request)) {
            is ByokResult.Ok -> {
                usage.record(inputTokens = r.value.usage.inputTokens, outputTokens = r.value.usage.outputTokens)
                cache.put(request.promptId, request.track, r.value.ideas)
                IdeasResult.Ok(r.value.ideas)
            }
            is ByokResult.Fail -> {
                MukharLog.i("ideas: anthropic failed (${r.error::class.simpleName})")
                IdeasResult.Failed(r.error.toFailure())
            }
        }
    }

    private fun ByokError.toFailure(): IdeasFailure = when (this) {
        ByokError.Offline -> IdeasFailure.OFFLINE
        ByokError.Unauthorized, ByokError.Forbidden, ByokError.ModelUnavailable -> IdeasFailure.KEY_REJECTED
        ByokError.Refused -> IdeasFailure.REFUSED
        is ByokError.RateLimited, is ByokError.Server -> IdeasFailure.BUSY
        ByokError.Truncated, is ByokError.BadResponse -> IdeasFailure.BAD_RESPONSE
    }
}
