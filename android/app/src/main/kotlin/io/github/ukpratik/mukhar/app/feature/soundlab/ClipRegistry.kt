package io.github.ukpratik.mukhar.app.feature.soundlab

import io.github.ukpratik.mukhar.domain.model.Lang

/** Where a word's audio comes from (US-070: recorded clips; TTS only as a labelled stand-in until they exist). */
sealed interface ClipSource {
    /** A recorded clip bundled in the APK (`assets/pairs/<id>.<ext>`). */
    data class Recorded(val assetPath: String) : ClipSource

    /** No recording yet: the platform TTS reads [text]. The UI shows a "Preview voice" label (design review). */
    data class Preview(val text: String, val lang: Lang) : ClipSource
}

/**
 * Maps clip ids to recorded clips when the file is bundled, else to a TTS preview. Pure: the asset listing is passed
 * in, so the fallback is unit-tested without Android.
 */
class ClipRegistry(private val clipsDir: String, assetFiles: Collection<String>) {
    // id → file name; the first extension in [EXTENSIONS] order wins if a clip exists twice.
    private val byId: Map<String, String> = assetFiles
        .mapNotNull { name ->
            val dot = name.lastIndexOf('.')
            if (dot <= 0) return@mapNotNull null
            val ext = name.substring(dot + 1).lowercase()
            if (ext !in EXTENSIONS) null else Triple(name.substring(0, dot), name, EXTENSIONS.indexOf(ext))
        }
        .groupBy { it.first }
        .mapValues { (_, v) -> v.minBy { it.third }.second }

    fun resolve(word: PairWord, lang: Lang): ClipSource =
        byId[word.id]?.let { ClipSource.Recorded("$clipsDir/$it") } ?: ClipSource.Preview(word.text, lang)

    fun isRecorded(word: PairWord): Boolean = word.id in byId

    /** True when every word of [pair] has a recording (no "Preview voice" label anywhere in it). */
    fun fullyRecorded(pair: MinimalPair): Boolean = pair.words.all(::isRecorded)

    /** Clip ids still to record, in pack order (product/06-build/content-notes.md). */
    fun missing(pack: PairPack): List<String> = pack.pairs.flatMap { it.words }.map { it.id }.filterNot { it in byId }

    companion object {
        /** Formats Android's MediaPlayer plays from an uncompressed asset (aapt doesn't compress these). */
        val EXTENSIONS = listOf("m4a", "ogg", "mp3", "aac", "wav")
    }
}
