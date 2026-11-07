package io.github.ukpratik.mukhar.app.feature.talk

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.talk.ideas.AiIdeasUi
import io.github.ukpratik.mukhar.app.feature.talk.ideas.IdeasContent
import io.github.ukpratik.mukhar.app.feature.talk.ideas.IdeasEvent
import io.github.ukpratik.mukhar.app.feature.talk.ideas.IdeasUiState
import io.github.ukpratik.mukhar.app.feature.talk.ideas.TAG_IDEAS_FAILED
import io.github.ukpratik.mukhar.app.feature.talk.ideas.TAG_IDEAS_GUIDE
import io.github.ukpratik.mukhar.app.feature.talk.ideas.TAG_IDEAS_LOCKED
import io.github.ukpratik.mukhar.app.feature.talk.ideas.TAG_IDEAS_WRITE
import io.github.ukpratik.mukhar.app.feature.talk.ideas.TAG_NEED_IDEAS
import io.github.ukpratik.mukhar.app.feature.talk.prompt.PromptContent
import io.github.ukpratik.mukhar.app.feature.talk.prompt.PromptUiState
import io.github.ukpratik.mukhar.data.byok.IdeaStep
import io.github.ukpratik.mukhar.data.byok.IdeaWord
import io.github.ukpratik.mukhar.data.byok.SpeakingIdeas
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.domain.ideas.GuideTrack
import io.github.ukpratik.mukhar.domain.ideas.SpeakingGuideBuilder
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

/** US-078: the "Need ideas?" sheet in each state, and its entry on the prompt screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class IdeasSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val prompt = Prompt(
        "en-010", Lang.EN, setOf(Track.INTERVIEWS), Difficulty.MEDIUM, "Tell us about a time you disagreed with your manager.", null,
        emptyList(), "Try: what happened → what you did → the result.",
    )
    private val events = mutableListOf<IdeasEvent>()
    private var keys = 0

    private fun state(ai: AiIdeasUi, track: GuideTrack = GuideTrack.INTERVIEWS) = IdeasUiState(
        prompt = prompt, tracks = listOf(GuideTrack.INTERVIEWS, GuideTrack.CONTENT_CREATION), track = track,
        guide = SpeakingGuideBuilder.build(prompt, track, listOf("candid", "pivotal")), ai = ai,
    )

    private fun show(s: IdeasUiState, openKeys: Boolean = true) {
        compose.setContent { MukharTheme { IdeasContent(s, { events += it }, if (openKeys) ({ keys++ }) else null) } }
    }

    @Test
    fun withoutAKeyTheFreeGuideShowsAndAILineIsLocked() {
        show(state(AiIdeasUi.Locked))
        compose.onNodeWithText("Ideas to get you started. Say it your own way.").assertIsDisplayed()
        compose.onNodeWithTag(TAG_IDEAS_GUIDE).assertIsDisplayed()
        compose.onNodeWithText("Situation → Task → Action → Result").assertIsDisplayed()
        compose.onNodeWithText("Try: what happened → what you did → the result.").performScrollTo()
        compose.onNodeWithText("candid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Full AI ideas: add your Anthropic key in Settings, or wait for Mukhar Cloud or offline AI (coming).")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_IDEAS_LOCKED).performClick()
        assertThat(keys).isEqualTo(1)
        compose.onNodeWithTag(TAG_IDEAS_WRITE).assertDoesNotExist()
    }

    @Test
    fun contentCreationStyleIsHookFirstAndPickingAStyleIsReported() {
        show(state(AiIdeasUi.Locked, GuideTrack.CONTENT_CREATION))
        compose.onNodeWithText("Hook (3 s) → Story → Takeaway → Call to action").assertIsDisplayed()
        compose.onNodeWithTag("ideas_track_interviews").performClick()
        assertThat(events).containsExactly(IdeasEvent.SelectTrack(GuideTrack.INTERVIEWS))
    }

    @Test
    fun withAKeyWriteShowsTheCostAndSendsAnEvent() {
        show(state(AiIdeasUi.Ready))
        compose.onNodeWithText("≈ ₹0.10 on your key").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_IDEAS_WRITE).performScrollTo().performClick()
        assertThat(events).containsExactly(IdeasEvent.Write)
    }

    @Test
    fun aiIdeasShowAnglesOutlineOpeningAndRegenerate() {
        val ideas = SpeakingIdeas(
            listOf("Disagree with data", "Disagree in private", "What you learnt"),
            listOf(IdeaStep("Situation", "A launch date"), IdeaStep("Task", "Protect quality"), IdeaStep("Action", "Brought numbers"), IdeaStep("Result", "Moved a week")),
            "My manager wanted to launch on Friday. I thought we needed a week.",
            listOf("I see it differently", "the data showed", "we agreed to", "in hindsight", "what I'd keep"),
            listOf(IdeaWord("candid", "honest and direct")),
        )
        show(state(AiIdeasUi.Shown(ideas)))
        compose.onNodeWithText("• Disagree in private").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3. Action: Brought numbers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("“My manager wanted to launch on Friday. I thought we needed a week.”").performScrollTo()
        compose.onNodeWithText("in hindsight").performScrollTo()
        compose.onNodeWithText("Regenerate").performScrollTo().performClick()
        assertThat(events).containsExactly(IdeasEvent.Write)
    }

    @Test
    fun aFailureKeepsTheGuideWithAOneLineNote() {
        show(state(AiIdeasUi.Failed(IdeasFailure.OFFLINE)))
        compose.onNodeWithTag(TAG_IDEAS_GUIDE).assertIsDisplayed()
        compose.onNodeWithTag(TAG_IDEAS_FAILED).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No internet, so here's the free guide above.").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }

    @Test
    fun whileRecordingTheLockedLineIsPlainText() {
        show(state(AiIdeasUi.Locked), openKeys = false)
        compose.onNodeWithTag(TAG_IDEAS_LOCKED).performScrollTo().performClick()
        assertThat(keys).isEqualTo(0)
    }

    @Test
    fun thePromptCardOffersIdeas() {
        var opened = 0
        compose.setContent {
            MukharTheme {
                PromptContent(PromptUiState(prompt = prompt, track = Track.INTERVIEWS, tracks = listOf(Track.INTERVIEWS)), {}, {}, onNeedIdeas = { opened++ })
            }
        }
        compose.onNodeWithContentDescription("Need ideas? Opens a speaking guide for this prompt").performScrollTo().performClick()
        compose.onNodeWithTag(TAG_NEED_IDEAS).assertIsDisplayed()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun recordingOffersAOneTapPeek() {
        var peeks = 0
        compose.setContent {
            MukharTheme {
                io.github.ukpratik.mukhar.app.feature.talk.record.RecordContent(
                    io.github.ukpratik.mukhar.app.feature.talk.record.RecordUiState(
                        phase = io.github.ukpratik.mukhar.app.feature.talk.record.RecordPhase.RECORDING, prompt = prompt, track = Track.INTERVIEWS,
                    ),
                    onEvent = {},
                    onPeekIdeas = { peeks++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Peek at ideas for this prompt").performScrollTo().performClick()
        assertThat(peeks).isEqualTo(1)
    }
}
