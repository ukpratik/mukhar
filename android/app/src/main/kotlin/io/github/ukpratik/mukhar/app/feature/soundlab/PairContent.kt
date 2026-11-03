package io.github.ukpratik.mukhar.app.feature.soundlab

import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `assets/pairs.json` (US-070, S22): minimal pairs and the clip id of every word. */
@Serializable
data class PairPack(
    val version: Int,
    @SerialName("clips_dir") val clipsDir: String = "pairs",
    val pairs: List<MinimalPair>,
)

@Serializable
data class MinimalPair(
    val id: String,
    @SerialName("lang") val langCode: String,
    /** "v / w", "ड़ / र". */
    val label: String,
    /** One line under the label in the pair list. */
    val hint: String,
    @SerialName("listen_hint") val listenHint: String,
    @SerialName("reveal_hint") val revealHint: String,
    @SerialName("self_check") val selfCheck: String,
    /** Word ids to say, in order; the first is the model the user compares with. */
    val say: List<String>,
    val a: PairSide,
    val b: PairSide,
) {
    val lang: Lang get() = Lang.fromCode(langCode)

    /** Word pairs lined up by index: (vest, west), (vine, wine). */
    val wordPairs: List<Pair<PairWord, PairWord>> get() = a.words.zip(b.words)

    val words: List<PairWord> get() = a.words + b.words

    fun word(id: String): PairWord? = words.firstOrNull { it.id == id }

    fun side(of: PairWord): Side = if (a.words.any { it.id == of.id }) Side.A else Side.B

    /** "vest · west" for the list row. */
    val example: String get() = wordPairs.firstOrNull()?.let { (x, y) -> "${x.text} · ${y.text}" }.orEmpty()
}

@Serializable
data class PairSide(
    /** The sound: "v", "ड़". */
    val sound: String,
    /** A one-line mouth tip (Hindi tips in Devanagari). */
    val tip: String,
    val words: List<PairWord>,
)

@Serializable
data class PairWord(
    /** Clip id: the recording is `assets/<clips_dir>/<id>.<ext>`. */
    val id: String,
    val text: String,
    val translit: String? = null,
    val gloss: String? = null,
)

enum class Side { A, B }

object PairParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): PairPack = json.decodeFromString(PairPack.serializer(), text)
}
