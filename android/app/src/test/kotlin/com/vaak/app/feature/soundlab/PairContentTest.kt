package com.vaak.app.feature.soundlab

import com.google.common.truth.Truth.assertThat
import com.vaak.domain.model.Lang
import org.junit.Test
import java.io.File

/** The shipped `assets/pairs.json` (US-070): the PRD's pairs, valid clip ids, and the record list in content-notes. */
class PairContentTest {
    private val pack = SoundLabFixtures.shippedPack()

    @Test
    fun coversThePrdPairs() {
        val labels = pack.pairs.map { it.label }
        assertThat(labels).containsAtLeast("v / w", "th / t", "s / sh", "z / j", "i / ee", "ड़ / र", "ट / त", "आ / आँ")
        assertThat(pack.pairs.count { it.lang == Lang.EN }).isAtLeast(5)
        assertThat(pack.pairs.count { it.lang == Lang.HI }).isAtLeast(3)
    }

    @Test
    fun everyPairIsWellFormed() {
        val ids = pack.pairs.flatMap { p -> p.words.map { it.id } }
        assertThat(ids).containsNoDuplicates()
        assertThat(pack.pairs.map { it.id }).containsNoDuplicates()
        for (p in pack.pairs) {
            assertThat(p.a.words).isNotEmpty()
            assertThat(p.a.words.size).isEqualTo(p.b.words.size)
            assertThat(p.say).hasSize(2)
            p.say.forEach { assertThat(p.word(it)).isNotNull() }
            assertThat(listOf(p.hint, p.listenHint, p.revealHint, p.selfCheck, p.a.tip, p.b.tip).all { it.isNotBlank() }).isTrue()
            for (w in p.words) {
                assertThat(w.id).matches("[a-z0-9_]+")
                assertThat(w.id).startsWith(p.langCode + "_")
                if (p.lang == Lang.HI) {
                    assertThat(w.text.any { it in 'ऀ'..'ॿ' }).isTrue()
                    assertThat(w.translit).isNotEmpty()
                    assertThat(w.gloss).isNotEmpty()
                }
            }
        }
    }

    @Test
    fun noClipsAreBundledYetSoEveryWordIsAPreviewAndListedInContentNotes() {
        val bundled = File("src/main/assets/pairs").list()?.filterNot { it.startsWith(".") }.orEmpty()
        val reg = ClipRegistry(pack.clipsDir, bundled)
        val missing = reg.missing(pack)
        // Every clip still to record is named in product/06-build/content-notes.md (the Q-011 list).
        val notes = File("../../product/06-build/content-notes.md").readText()
        missing.forEach { assertThat(notes).contains("`$it`") }
    }
}
