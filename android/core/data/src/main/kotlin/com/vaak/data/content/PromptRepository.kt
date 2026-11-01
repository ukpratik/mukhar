package com.vaak.data.content

import android.content.Context
import com.vaak.data.db.HabitDao
import com.vaak.data.db.PromptHistoryEntity
import com.vaak.domain.Clock
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Difficulty
import com.vaak.domain.prompt.Prompt
import com.vaak.domain.prompt.PromptPicker
import com.vaak.domain.prompt.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class PromptFileDto(val version: Int, val prompts: List<PromptDto>)

@Serializable
internal data class PromptDto(
    val id: String,
    val lang: String,
    val tracks: List<String>,
    val difficulty: String,
    val text: String,
    val translit: String? = null,
    val prep: List<String> = emptyList(),
    val tip: String? = null,
)

/** Bundled talk prompts (`assets/prompts.json`, US-010) with the no-repeat-of-last-30 shuffle. */
class PromptRepository(
    private val load: suspend () -> String,
    private val habitDao: HabitDao,
    private val clock: Clock,
    private val json: Json,
) {
    constructor(context: Context, habitDao: HabitDao, clock: Clock, json: Json) : this(
        load = { withContext(Dispatchers.IO) { context.assets.open(ASSET).bufferedReader().use { it.readText() } } },
        habitDao = habitDao,
        clock = clock,
        json = json,
    )

    private val mutex = Mutex()
    private var cache: List<Prompt>? = null

    suspend fun all(): List<Prompt> = mutex.withLock {
        cache ?: parse(json, load()).also { cache = it }
    }

    suspend fun byId(id: String): Prompt? = all().firstOrNull { it.id == id }

    /** A prompt for [lang] and [track] that wasn't among the last 30 shown, recorded as shown. */
    suspend fun next(lang: Lang, track: Track, currentId: String?): Prompt? {
        val pool = PromptPicker.pool(all(), lang, track).ifEmpty { all().filter { it.lang == lang } }
        val recent = habitDao.recentPromptIds(PromptPicker.NO_REPEAT)
        val prompt = PromptPicker.next(pool, recent, currentId) ?: return null
        habitDao.insertPromptHistory(PromptHistoryEntity(promptId = prompt.id, shownAt = clock.now().toEpochMilli()))
        return prompt
    }

    companion object {
        const val ASSET = "prompts.json"

        fun parse(json: Json, text: String): List<Prompt> = json.decodeFromString<PromptFileDto>(text).prompts.map { p ->
            Prompt(
                id = p.id,
                lang = Lang.fromCode(p.lang),
                tracks = p.tracks.map(Track::fromKey).toSet(),
                difficulty = Difficulty.fromKey(p.difficulty),
                text = p.text,
                translit = p.translit,
                prep = p.prep,
                tip = p.tip,
            )
        }
    }
}
