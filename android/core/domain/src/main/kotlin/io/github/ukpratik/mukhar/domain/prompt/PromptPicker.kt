package io.github.ukpratik.mukhar.domain.prompt

import io.github.ukpratik.mukhar.domain.model.Lang
import kotlin.random.Random

enum class Difficulty(val key: String) {
    EASY("easy"), MEDIUM("medium"), HARD("hard");

    companion object {
        fun fromKey(key: String): Difficulty = entries.first { it.key == key }
    }
}

/** Goal tracks (S02, S08). Keys are stored in `talk_session.track`. */
enum class Track(val key: String) {
    PROFESSIONAL("professional"), INTERVIEWS("interviews"), EVERYDAY("everyday"), PUBLIC_SPEAKING("public_speaking");

    companion object {
        fun fromKey(key: String): Track = entries.firstOrNull { it.key == key } ?: EVERYDAY
    }
}

/** One talk prompt from `assets/prompts.json` (US-010). [prep] has the 4 PREP lines when a structure hint applies. */
data class Prompt(
    val id: String,
    val lang: Lang,
    val tracks: Set<Track>,
    val difficulty: Difficulty,
    val text: String,
    val translit: String?,
    val prep: List<String>,
    val tip: String?,
)

/**
 * Shuffle with no repeat of the last 30 shown (US-010). Unlimited: when every prompt in the pool was shown recently,
 * the least recently shown one (other than the current) comes back. Prompts the user marked as done (US-079) never
 * come back while any other prompt is left in the pool.
 */
object PromptPicker {
    const val NO_REPEAT = 30

    /** [recent] is most-recent-first prompt ids from `prompt_history`. */
    fun next(
        pool: List<Prompt>,
        recent: List<String>,
        currentId: String?,
        random: Random = Random.Default,
        done: Set<String> = emptySet(),
    ): Prompt? {
        if (pool.isEmpty()) return null
        val open = pool.filter { it.id !in done }
        if (open.isNotEmpty() && open.size < pool.size) return next(open, recent, currentId, random)
        val blocked = recent.take(NO_REPEAT).toSet() + listOfNotNull(currentId)
        val fresh = pool.filter { it.id !in blocked }
        if (fresh.isNotEmpty()) return fresh[random.nextInt(fresh.size)]
        val others = pool.filter { it.id != currentId }.ifEmpty { return pool.first() }
        val lastSeen = others.associateWith { p -> recent.indexOf(p.id).let { if (it < 0) Int.MAX_VALUE else it } }
        return others.maxBy { lastSeen.getValue(it) }
    }

    fun pool(all: List<Prompt>, lang: Lang, track: Track): List<Prompt> =
        all.filter { it.lang == lang && track in it.tracks }
}
