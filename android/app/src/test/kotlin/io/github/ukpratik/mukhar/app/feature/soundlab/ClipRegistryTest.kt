package io.github.ukpratik.mukhar.app.feature.soundlab

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.rr
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.vw
import io.github.ukpratik.mukhar.domain.model.Lang
import org.junit.Test

/** Recorded clip if bundled, else a labelled TTS preview (US-070, design review). */
class ClipRegistryTest {
    private val vest = vw.a.words[0]
    private val west = vw.b.words[0]

    @Test
    fun aBundledClipWinsOverTts() {
        val reg = ClipRegistry("pairs", listOf("en_vest.m4a"))
        assertThat(reg.resolve(vest, Lang.EN)).isEqualTo(ClipSource.Recorded("pairs/en_vest.m4a"))
        assertThat(reg.isRecorded(vest)).isTrue()
    }

    @Test
    fun withoutAClipItFallsBackToTtsInThePairsLanguage() {
        val reg = ClipRegistry("pairs", listOf("en_vest.m4a"))
        assertThat(reg.resolve(west, Lang.EN)).isEqualTo(ClipSource.Preview("west", Lang.EN))
        assertThat(reg.isRecorded(west)).isFalse()
        val mor = rr.b.words[1]
        assertThat(ClipRegistry("pairs", emptyList()).resolve(mor, Lang.HI)).isEqualTo(ClipSource.Preview("मोर", Lang.HI))
    }

    @Test
    fun unknownExtensionsAndStrayFilesAreIgnored() {
        val reg = ClipRegistry("pairs", listOf("en_vest.txt", "en_vest", ".gitkeep", "README.md", "en_west.WAV"))
        assertThat(reg.isRecorded(vest)).isFalse()
        assertThat(reg.resolve(west, Lang.EN)).isEqualTo(ClipSource.Recorded("pairs/en_west.WAV"))
    }

    @Test
    fun whenAClipExistsTwiceThePreferredFormatWins() {
        val reg = ClipRegistry("pairs", listOf("en_vest.wav", "en_vest.mp3", "en_vest.m4a"))
        assertThat(reg.resolve(vest, Lang.EN)).isEqualTo(ClipSource.Recorded("pairs/en_vest.m4a"))
    }

    @Test
    fun missingListsTheClipsStillToRecordAndFullyRecordedFollows() {
        val pack = PairPack(1, "pairs", listOf(vw))
        val none = ClipRegistry("pairs", emptyList())
        assertThat(none.missing(pack)).containsExactly("en_vest", "en_vine", "en_west", "en_wine").inOrder()
        assertThat(none.fullyRecorded(vw)).isFalse()
        val all = ClipRegistry("pairs", listOf("en_vest.m4a", "en_vine.m4a", "en_west.ogg", "en_wine.mp3"))
        assertThat(all.missing(pack)).isEmpty()
        assertThat(all.fullyRecorded(vw)).isTrue()
    }

    @Test
    fun uiStateLabelsPreviewsAndHidesPlayWithoutAVoice() {
        val ui = SoundLabUiState(registry = ClipRegistry("pairs", listOf("en_vest.m4a")), voices = mapOf(Lang.EN to false, Lang.HI to true))
        assertThat(ui.isPreview(vest)).isFalse()
        assertThat(ui.isPreview(west)).isTrue()
        // A recorded clip plays without a TTS voice; a preview needs one.
        assertThat(ui.canPlay(vest, Lang.EN)).isTrue()
        assertThat(ui.canPlay(west, Lang.EN)).isFalse()
        assertThat(ui.canPlay(rr.a.words[0], Lang.HI)).isTrue()
        // While TTS is still starting (no entry), a preview stays playable.
        assertThat(SoundLabUiState(registry = ClipRegistry("pairs", emptyList())).canPlay(west, Lang.EN)).isTrue()
    }
}
