package com.vaak.app.feature.words

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.vaak.data.tts.SpeakerUi
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S14 states 1 to 4 on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WordBankContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<WordBankEvent>()
    private val opened = mutableListOf<String>()
    private var talks = 0

    private fun show(state: WordBankUiState) {
        compose.setContent {
            VaakTheme { WordBankContent(state, WordBankActions(onEvent = { events += it }, onOpenWord = { opened += it }, onTalk = { talks++ })) }
        }
    }

    private val list = WordBankUiState(
        loading = false,
        words = listOf(WordsFixtures.pivotal, WordsFixtures.articulate, WordsFixtures.undocumented, WordsFixtures.streamline, WordsFixtures.concise),
        trackChips = listOf("interviews", "professional"),
        gymDue = 8,
    )

    @Test
    fun emptySaysWhatWillAppearAndNeverBlames() {
        show(WordBankUiState(loading = false, trackChips = listOf("interviews", "professional")))
        compose.onNodeWithTag(TAG_WORDS_EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Your words will live here").assertIsDisplayed()
        compose.onNodeWithText(
            "Words you add from your talks, plus 3 picks a day for Interviews and Professional. The first ones arrive tomorrow morning.",
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Review: nothing due yet").assertIsDisplayed()
        compose.onNodeWithText("Do today's talk").performClick()
        assertThat(talks).isEqualTo(1)
    }

    @Test
    fun listShowsTalkWordsWithStatusesAndTheGymRow() {
        show(list)
        compose.onNodeWithContentDescription("Word Gym: 8 due, about 2 minutes. Start review").assertIsDisplayed()
        compose.onNodeWithText("From your talks").assertIsDisplayed()
        compose.onNodeWithText("Interviews").assertIsDisplayed()
        compose.onNodeWithText("5 in total").assertIsDisplayed()
        compose.onNodeWithContentDescription("pivotal, adj. · power. Of central importance.. Learning").performScrollTo()
        compose.onNodeWithContentDescription("undocumented, adj. · formal. Not written down anywhere.. New").performScrollTo()
        compose.onNodeWithContentDescription("streamline, verb · power. Make a process simpler and faster.. Saved for 8 Nov").performScrollTo()
        compose.onNodeWithContentDescription("concise, adj. · formal. Short and clear, with no extra words.. Known").performScrollTo().performClick()
        assertThat(opened).containsExactly("en:concise")
    }

    @Test
    fun filtersNarrowTheList() {
        show(list.copy(source = SourceFilter.TALKS))
        compose.onNodeWithText("4 from your talks · 5 in total").assertIsDisplayed()
        compose.onAllNodes(hasText("concise")).assertCountEquals(0)
        compose.onNodeWithText("Curated").performClick()
        compose.onNodeWithText("Interviews").performClick()
        assertThat(events).containsExactly(WordBankEvent.Source(SourceFilter.CURATED), WordBankEvent.ToggleTrack("interviews")).inOrder()
    }

    @Test
    fun savedLinesSegment() {
        show(list.copy(section = WordsSection.LINES))
        compose.onNodeWithText("No saved lines yet").assertIsDisplayed()
    }

    @Test
    fun wordDetailHasTtsMeaningExampleAndSource() {
        compose.setContent {
            VaakTheme { WordDetailContent(WordDetailUiState(false, WordsFixtures.pivotal, SpeakerUi(true, false), WordsFixtures.at), WordDetailActions()) }
        }
        compose.onNodeWithContentDescription("Hear pivotal, Indian English voice").assertIsDisplayed()
        compose.onNodeWithText("“The client call was pivotal: it decided our whole quarter.”").performScrollTo()
        compose.onNodeWithText("See line").performScrollTo()
        compose.onNodeWithText("In 4 days").performScrollTo()
        compose.onNodeWithText("Practise in a sentence").performScrollTo()
        compose.onAllNodesWithTag(TAG_VOICE_TIP).assertCountEquals(0)
    }

    @Test
    fun noHindiVoiceHidesTheSpeakerAndShowsTheTipOnce() {
        var opened = 0
        compose.setContent {
            VaakTheme {
                WordDetailContent(
                    WordDetailUiState(false, WordsFixtures.sateek, SpeakerUi(showSpeaker = false, showVoiceTip = true), WordsFixtures.at),
                    WordDetailActions(onOpenVoiceSettings = { opened++ }),
                )
            }
        }
        compose.onAllNodesWithTag(TAG_SPEAKER).assertCountEquals(0)
        compose.onNodeWithText("Add a Hindi voice to hear words").assertIsDisplayed()
        compose.onNodeWithText("sateek · adjective").assertIsDisplayed()
        compose.onNodeWithText("“I need the exact figures for this report.”").performScrollTo()
        compose.onNodeWithText("Open voice settings").performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun lineDiffMarksRemovedAndAddedWords() {
        val (said, instead) = LineDiff.diff("Let's discuss about the budget", "Let's discuss the budget")
        assertThat(said.filter { it.kind == LineDiff.Kind.REMOVED }.map { it.text }).containsExactly("about")
        assertThat(instead.none { it.kind == LineDiff.Kind.REMOVED }).isTrue()
        val (_, corrected) = LineDiff.diff("in tomorrow meeting", "in tomorrow's meeting")
        assertThat(corrected.filter { it.kind == LineDiff.Kind.ADDED }.map { it.text }).containsExactly("tomorrow's")
    }
}
