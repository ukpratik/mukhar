package io.github.ukpratik.mukhar.app.feature.talk

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordContent
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordEvent
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordPhase
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordUiState
import io.github.ukpratik.mukhar.app.feature.talk.record.StorageSheet
import io.github.ukpratik.mukhar.data.audio.MicCheck
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Difficulty
import io.github.ukpratik.mukhar.domain.prompt.Prompt
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S09 Recording, every state (design final/s09-recording.html). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<RecordEvent>()
    private val prompt = Prompt(
        "en-002", Lang.EN, setOf(Track.PROFESSIONAL), Difficulty.MEDIUM,
        "Tell us about a project you're proud of, and what you'd do differently next time.", null, emptyList(),
        "Try: what it was → what you did → the result → one lesson.",
    )
    private val base = RecordUiState(phase = RecordPhase.READY, prompt = prompt, lang = Lang.EN, track = Track.PROFESSIONAL, capMs = 180_000)

    private fun show(state: RecordUiState) {
        compose.mainClock.autoAdvance = false // the recording dot and waveform loop forever
        compose.setContent { MukharTheme(darkTheme = false) { RecordContent(state, { events += it }) } }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun readyWithAQuietRoom() {
        show(base.copy(micCheck = MicCheck(-60.0, noisy = false)))
        compose.onNodeWithText("Ready").assertExists()
        compose.onNodeWithText("ENGLISH · PROFESSIONAL").assertExists()
        compose.onNodeWithText("Try: what it was → what you did → the result → one lesson.").assertExists()
        compose.onNodeWithText("Mic sounds good").assertExists()
        compose.onNodeWithText("30 seconds to think first").assertExists()
        compose.onNodeWithText("Private by default. Audio is deleted after analysis.").assertExists()
        compose.onNodeWithContentDescription("Start recording. Up to 3:00").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(RecordEvent.Start)
    }

    @Test
    fun noiseWarningNeverBlocks() {
        show(base.copy(micCheck = MicCheck(-30.0, noisy = true)))
        compose.onNodeWithText("It's noisy — results may be less accurate").assertExists()
        compose.onNodeWithText("Lots of background sound").assertExists()
        compose.onNodeWithText("Check again").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Record anyway").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(RecordEvent.CheckAgain, RecordEvent.Start).inOrder()
    }

    @Test
    fun thinkTime() {
        show(base.copy(phase = RecordPhase.THINK, thinkLeft = 18))
        compose.onNodeWithText("Think time").assertExists()
        compose.onNodeWithText("0:18").assertExists()
        compose.onNodeWithText("to think").assertExists()
        compose.onNodeWithText("Start now").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(RecordEvent.StartNow)
    }

    @Test
    fun recording() {
        show(base.copy(phase = RecordPhase.RECORDING, elapsedMs = 84_000, pausesLeft = 2, waveform = List(26) { 0.3f }))
        compose.onNodeWithText("Recording").assertExists()
        compose.onNodeWithText("1:24").assertExists()
        compose.onNodeWithText("of 3:00 · take your time").assertExists()
        compose.onNodeWithContentDescription("Pause recording. 2 pauses left").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Stop and get feedback").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Close. Asks before discarding").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(RecordEvent.Pause, RecordEvent.Stop, RecordEvent.Close).inOrder()
    }

    @Test
    fun pausedByTheAppGoingAway() {
        show(base.copy(phase = RecordPhase.PAUSED, elapsedMs = 84_000, pausesLeft = 1, autoPaused = true))
        compose.onNodeWithText("Paused").assertExists()
        compose.onNodeWithText("Paused while you were away").assertExists()
        compose.onNodeWithText("of 3:00 · paused").assertExists()
        compose.onNodeWithText("1 pause left").assertExists()
        compose.onNodeWithContentDescription("Resume recording").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(RecordEvent.Resume)
    }

    @Test
    fun noPauseButtonOnceBothAreUsed() {
        show(base.copy(phase = RecordPhase.RECORDING, elapsedMs = 10_000, pausesLeft = 0))
        compose.onNodeWithContentDescription("Pause recording. 0 pauses left").assertDoesNotExist()
    }

    @Test
    fun couldntHearMuch() {
        show(base.copy(phase = RecordPhase.TOO_SHORT, tooShortSpeechS = 6))
        compose.onNodeWithText("We couldn't hear much — try again?").assertExists()
        compose.onNodeWithText("We caught about 6 seconds of speech. Feedback needs at least 10, and 30 seconds counts for your streak.").assertExists()
        compose.onNodeWithText("Hold the phone about a hand's length away.").assertExists()
        compose.onNodeWithText("Try again").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).containsExactly(RecordEvent.TryAgain)
    }

    @Test
    fun lowStorageWarnsButAllowsRecording() {
        show(base.copy(storageSheet = StorageSheet.WARN, availableMb = 38, capMs = 120_000))
        compose.onNodeWithText("Your phone is almost full").assertExists()
        compose.onNodeWithText("About 38 MB is free", substring = true).assertExists()
        compose.onNodeWithText("Record anyway").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).contains(RecordEvent.RecordAnyway)
    }

    @Test
    fun veryLowStorageBlocks() {
        show(base.copy(storageSheet = StorageSheet.BLOCK, availableMb = 6))
        compose.onNodeWithText("About 6 MB is free, which isn't enough to save a talk safely. Free up a little space, then come back.").assertExists()
        compose.onNodeWithText("Open storage settings").assertExists()
        compose.onNodeWithText("Record anyway").assertDoesNotExist()
    }

    @Test
    fun microphoneDenied() {
        show(base.copy(phase = RecordPhase.PERMISSION, permissionDenied = true))
        compose.onNodeWithText("Mukhar needs the microphone to hear your talk").assertExists()
        compose.onNodeWithText("Allow microphone").assertExists()
    }

    @Test
    fun retakeIsLabelled() {
        show(base.copy(isRetake = true, capMs = 135_000))
        compose.onNodeWithText("RE-TAKE · ENGLISH · PROFESSIONAL").assertExists()
        compose.onNodeWithContentDescription("Start recording. Up to 2:15").assertExists()
    }
}
