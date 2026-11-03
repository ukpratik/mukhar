package io.github.ukpratik.mukhar.app.feature.soundlab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.rr
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.vw
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** S22 states on Robolectric, light and dark (US-070, US-074). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SoundLabContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<SoundLabAction>()
    private var backs = 0
    private val machine = SoundLabMachine(Random(3))
    private val loaded = machine.reduce(SoundLabState(), LabEvent.Loaded(listOf(vw, rr)))
    private val noClips = ClipRegistry("pairs", emptyList())

    private fun show(state: SoundLabUiState, dark: Boolean = false) {
        compose.setContent { MukharTheme(darkTheme = dark) { SoundLabContent(state, { actions += it }, { backs++ }) } }
    }

    private fun SoundLabState.on(vararg e: LabEvent) = e.fold(this) { s, ev -> machine.reduce(s, ev) }

    @Test
    fun pairListGroupsByLanguageAndShowsPractisedQuietly() {
        show(SoundLabUiState(lab = loaded.copy(practised = setOf("hi-rra-ra")), registry = noClips))
        compose.onNodeWithTag(TAG_SOUNDLAB_LIST).assertIsDisplayed()
        compose.onNodeWithText("2 minutes a pair. Headphones help.").assertIsDisplayed()
        compose.onNodeWithText("Some pairs use a preview voice until our recorded clips arrive.").assertIsDisplayed()
        compose.onNodeWithContentDescription("v / w: vest · west. Common for Hindi speakers").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("ड़ / र: पड़ी · परी. Flap vs. tap. Practised").performScrollTo().assertIsDisplayed()
        assertThat(actions).containsExactly(SoundLabAction.Lab(LabEvent.Open("en-v-w")))
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun listenShowsBothWordsTipsAndThePreviewVoiceLabel() {
        show(SoundLabUiState(lab = loaded.on(LabEvent.Open("en-v-w")), registry = noClips, voices = mapOf(Lang.EN to true)))
        compose.onNodeWithText("Hear the difference").assertIsDisplayed()
        compose.onNodeWithTag(TAG_SOUNDLAB_PREVIEW).assertIsDisplayed()
        compose.onNodeWithText("Preview voice").assertIsDisplayed()
        compose.onNodeWithText("v: top teeth on your lower lip.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play vest").performClick()
        compose.onNodeWithContentDescription("Play wine").performScrollTo().performClick()
        compose.onNodeWithText("Test my ear").performScrollTo().performClick()
        assertThat(actions).containsExactly(
            SoundLabAction.Play(vw.a.words[0]),
            SoundLabAction.Play(vw.b.words[1]),
            SoundLabAction.Lab(LabEvent.TestMyEar),
        ).inOrder()
        // Back inside a pair returns to the list, not out of Sound Lab.
        compose.onNodeWithContentDescription("Back to pairs").performClick()
        assertThat(actions.last()).isEqualTo(SoundLabAction.Lab(LabEvent.Back))
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun noRecordedClipAndNoVoiceSaysHowToGetOne() {
        show(SoundLabUiState(lab = loaded.on(LabEvent.Open("hi-rra-ra")), registry = noClips, voices = mapOf(Lang.HI to false)))
        compose.onNodeWithText("Add a Hindi text-to-speech voice in your phone's settings to hear the preview clips.").assertIsDisplayed()
    }

    @Test
    fun recordedClipsHaveNoPreviewLabel() {
        val all = ClipRegistry("pairs", vw.words.map { "${it.id}.m4a" })
        show(SoundLabUiState(lab = loaded.on(LabEvent.Open("en-v-w")), registry = all))
        compose.onNodeWithTag(TAG_SOUNDLAB_PREVIEW).assertDoesNotExist()
        compose.onNodeWithText("Preview voice").assertDoesNotExist()
    }

    @Test
    fun aMissedPickIsRevealedKindlyWithRolesInWords() {
        val pick = loaded.on(LabEvent.Open("en-v-w"), LabEvent.TestMyEar)
        val round = pick.session!!.current!!
        val other = round.choices.toList().first { it != round.word }
        show(SoundLabUiState(lab = pick.on(LabEvent.Pick(other.id)), registry = noClips))
        compose.onNodeWithText("Clip 1 of 4 · play again").assertIsDisplayed()
        compose.onNodeWithTag(TAG_SOUNDLAB_REVEAL).assertIsDisplayed()
        compose.onNodeWithText("That one was “${round.word.text}”").assertIsDisplayed()
        compose.onNodeWithText("Listen for the rounded lips.").assertIsDisplayed()
        compose.onNodeWithText("you picked").assertIsDisplayed()
        compose.onNodeWithText("this one").assertIsDisplayed()
        // Never "wrong", no score.
        assertThat(compose.onAllNodesWithText("wrong", substring = true, ignoreCase = true).fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodesWithText("score", substring = true, ignoreCase = true).fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithText("Hear both").performClick()
        compose.onNodeWithText("Next clip").performClick()
        assertThat(actions).containsExactly(SoundLabAction.HearBoth, SoundLabAction.Lab(LabEvent.NextClip)).inOrder()
    }

    @Test
    fun aRightPickSaysYesAndTheLastClipLeadsToSay() {
        var st = loaded.on(LabEvent.Open("en-v-w"), LabEvent.TestMyEar)
        repeat(3) { st = st.on(LabEvent.Pick(st.session!!.current!!.word.id), LabEvent.NextClip) }
        st = st.on(LabEvent.Pick(st.session!!.current!!.word.id))
        show(SoundLabUiState(lab = st, registry = noClips))
        compose.onNodeWithText("Yes, that one was “${st.session!!.current!!.word.text}”").assertIsDisplayed()
        compose.onNodeWithText("you picked · this one").assertIsDisplayed()
        compose.onNodeWithText("Now say it").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sayStepComparesModelAndTakeInDarkMode() {
        var st = loaded.on(LabEvent.Open("en-v-w"), LabEvent.TestMyEar)
        while (st.session!!.step == LabStep.PICK) st = st.on(LabEvent.Pick(st.session!!.current!!.word.id), LabEvent.NextClip)
        st = st.on(LabEvent.TakeReady("/cache/take.aac", 1_000))
        show(SoundLabUiState(lab = st, registry = noClips), dark = true)
        compose.onNodeWithText("Say “wine”, then “vine”").assertIsDisplayed()
        compose.onNodeWithText("Compare yourself with the model. Only you hear this; it isn't saved.").assertIsDisplayed()
        compose.onNodeWithText("Model · wine").assertIsDisplayed()
        compose.onAllNodesWithText("Preview voice").fetchSemanticsNodes().let { assertThat(it).isNotEmpty() }
        compose.onNodeWithText("You · wine").assertIsDisplayed()
        compose.onNodeWithText("0:01 · just now").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play You · wine").performClick()
        compose.onNodeWithContentDescription("Record again").performClick()
        compose.onNodeWithText("Done with this pair").performScrollTo().performClick()
        assertThat(actions).containsExactly(SoundLabAction.PlayMine, SoundLabAction.StartRecord, SoundLabAction.Lab(LabEvent.Done)).inOrder()
    }

    @Test
    fun recordingShowsAStopControlAndMicDeniedExplains() {
        var st = loaded.on(LabEvent.Open("hi-rra-ra"), LabEvent.TestMyEar)
        while (st.session!!.step == LabStep.PICK) st = st.on(LabEvent.Pick(st.session!!.current!!.word.id), LabEvent.NextClip)
        show(SoundLabUiState(lab = st.on(LabEvent.MicDenied), registry = noClips))
        compose.onNodeWithText("Say “मोड़”, then “मोर”").assertIsDisplayed()
        compose.onNodeWithText("Allow microphone").performScrollTo().performClick()
        assertThat(actions).containsExactly(SoundLabAction.StartRecord)
    }

    @Test
    fun practiceEntryCardOpensSoundLab() {
        var opened = 0
        compose.setContent { MukharTheme { SoundLabEntryCard(onOpen = { opened++ }) } }
        compose.onNodeWithTag(TAG_SOUNDLAB_ENTRY).assertIsDisplayed().performClick()
        assertThat(opened).isEqualTo(1)
    }
}
