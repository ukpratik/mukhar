package io.github.ukpratik.mukhar.app.feature.soundlab

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.rr
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabFixtures.vw
import io.github.ukpratik.mukhar.app.testing.waitFor
import io.github.ukpratik.mukhar.data.audio.Recorder
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.tts.Speaker
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * The "Practised" chip survives a restart: finished pair ids live in the `settings` DataStore (only the ids: no
 * audio, no score), and Delete all my data wipes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SoundLabPractisedTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("soundlab-test").toFile()
    private val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })

    private class Pairs : PairSource {
        override suspend fun load() = LoadedPairs(PairPack(1, pairs = listOf(vw, rr)), ClipRegistry("pairs", emptyList()))
    }

    private class SilentPlayer : ClipPlayer {
        override val playing: StateFlow<String?> = MutableStateFlow(null)
        override fun play(key: String, source: ClipSource) = Unit
        override fun playFile(key: String, path: String) = Unit
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class NoVoice : Speaker {
        override val voices: StateFlow<Map<Lang, Boolean>> = MutableStateFlow(emptyMap())
        override fun warmUp() = Unit
        override fun speak(text: String, lang: Lang) = Unit
    }

    private fun vm() = SoundLabViewModel(
        source = Pairs(),
        player = SilentPlayer(),
        recorder = Recorder({ error("no mic in this test") }, { error("no sink in this test") }, Dispatchers.IO),
        files = SoundLabFiles(File(dir, "takes")),
        settings = settings,
        speaker = NoVoice(),
    )

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }
        dir.deleteRecursively()
    }

    @Test
    fun theStoredSetShowsTheChipAndDoneAddsToIt() {
        runBlocking { settings.markSoundLabPractised("hi-rra-ra") }
        val vm = vm()
        waitFor { !vm.state.value.lab.loading && "hi-rra-ra" in vm.state.value.lab.practised }
        val shown = vm.state.value
        compose.setContent { MukharTheme { SoundLabContent(shown, {}, {}) } }
        compose.onNodeWithContentDescription("ड़ / र: पड़ी · परी. Flap vs. tap. Practised").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("v / w: vest · west. Common for Hindi speakers").assertIsDisplayed()

        vm.onAction(SoundLabAction.Lab(LabEvent.Open("en-v-w")))
        vm.onAction(SoundLabAction.Lab(LabEvent.Done))
        waitFor { runBlocking { settings.soundLabPractised.first() } == setOf("hi-rra-ra", "en-v-w") }
        assertThat(vm.state.value.lab.practised).containsExactly("hi-rra-ra", "en-v-w")

        // A fresh ViewModel (a new visit, or after a restart) reads the same set.
        val again = vm()
        waitFor { again.state.value.lab.practised == setOf("hi-rra-ra", "en-v-w") }
    }

    @Test
    fun deleteMyDataClearsPractised() {
        runBlocking {
            settings.markSoundLabPractised("en-v-w")
            settings.clearAll()
        }
        val vm = vm()
        waitFor { !vm.state.value.lab.loading }
        assertThat(vm.state.value.lab.practised).isEmpty()
    }

    @Test
    fun theStepPillIsLiveAndThePaneIsNamed() {
        val vm = vm()
        waitFor { !vm.state.value.lab.loading }
        vm.onAction(SoundLabAction.Lab(LabEvent.Open("en-v-w")))
        val shown = vm.state.value
        compose.setContent { MukharTheme { SoundLabContent(shown, {}, {}) } }
        compose.onNodeWithContentDescription("Listen, current step").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "v / w")).assertExists()
    }

    @Test
    fun recordingAnnouncesOnceAndOffersStopRecording() {
        var st = vm().let { v ->
            waitFor { !v.state.value.lab.loading }
            v.state.value.lab
        }
        val machine = SoundLabMachine()
        st = machine.reduce(st, LabEvent.Open("en-v-w"))
        st = machine.reduce(st, LabEvent.TestMyEar)
        while (st.session!!.step == LabStep.PICK) {
            st = machine.reduce(machine.reduce(st, LabEvent.Pick(st.session!!.current!!.word.id)), LabEvent.NextClip)
        }
        st = machine.reduce(machine.reduce(st, LabEvent.RecordStarted), LabEvent.RecordProgress(1_200))
        compose.setContent { MukharTheme { SoundLabContent(SoundLabUiState(lab = st), {}, {}) } }
        compose.onNodeWithContentDescription("Stop recording").assertExists()
        compose.onNodeWithTag(TAG_SOUNDLAB_STATUS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Recording started")))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }
}
