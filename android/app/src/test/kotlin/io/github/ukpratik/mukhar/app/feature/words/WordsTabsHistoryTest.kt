package io.github.ukpratik.mukhar.app.feature.words

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** US-075 language tabs and US-077 History on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class WordsTabsHistoryTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 7)
    private val events = mutableListOf<WordBankEvent>()
    private val revised = mutableListOf<Triple<Lang?, LocalDate, LocalDate>>()
    private val gyms = mutableListOf<Lang?>()

    private fun show(state: WordBankUiState) {
        compose.setContent {
            MukharTheme {
                WordBankContent(
                    state,
                    WordBankActions(
                        onEvent = { events += it },
                        onOpenGym = { gyms += it },
                        onRevise = { l, f, t -> revised += Triple(l, f, t) },
                    ),
                )
            }
        }
    }

    private val history = listOf(
        WordRepository.HistoryDay(today, listOf(WordsFixtures.pivotal, WordsFixtures.undocumented)),
        WordRepository.HistoryDay(today.minusDays(1), listOf(WordsFixtures.articulate)),
        WordRepository.HistoryDay(LocalDate.of(2026, 11, 5), listOf(WordsFixtures.concise)),
    )

    @Test
    fun noTabsWithOneLanguage() {
        show(WordBankUiState(loading = false, langs = listOf(Lang.EN), tab = null, words = listOf(WordsFixtures.pivotal)))
        compose.onAllNodesWithTag(TAG_WORDS_TABS).assertCountEquals(0)
    }

    @Test
    fun twoLanguagesShowTabsAndPickingOneIsReported() {
        show(WordBankUiState(loading = false, langs = listOf(Lang.EN, Lang.HI), tab = Lang.EN, words = listOf(WordsFixtures.pivotal), gymDue = 3))
        compose.onNodeWithTag(TAG_WORDS_TABS).assertIsDisplayed()
        compose.onNodeWithTag("words_tab_en").assertIsSelected()
        compose.onNodeWithTag("words_tab_hi").performClick()
        assertThat(events).contains(WordBankEvent.Tab(Lang.HI))
        // The Word Gym opens for the tab's language.
        compose.onNodeWithTag(TAG_GYM_ROW).performClick()
        assertThat(gyms).containsExactly(Lang.EN)
    }

    @Test
    fun historyGroupsByDayWithReviseActions() {
        var state by mutableStateOf(WordBankUiState(loading = false, langs = listOf(Lang.EN, Lang.HI), tab = Lang.EN, history = history, today = today))
        compose.setContent {
            MukharTheme {
                WordBankContent(state, WordBankActions(onEvent = { e -> if (e is WordBankEvent.Section) state = state.copy(section = e.section) }, onRevise = { l, f, t -> revised += Triple(l, f, t) }))
            }
        }
        compose.onNodeWithTag("words_seg_history").performClick()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
        compose.onNodeWithText("Thu 5 Nov").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("word_en:undocumented").performScrollTo().assertIsDisplayed()
        // Rows say when the word comes back.
        compose.onAllNodesWithTag("word_en:pivotal").assertCountEquals(1)

        compose.onNodeWithContentDescription("Revise these 2 words from Today").performScrollTo().performClick()
        compose.onNodeWithTag(TAG_REVISE_WEEK).performScrollTo().performClick()
        assertThat(revised).containsExactly(
            Triple(Lang.EN, today, today),
            Triple(Lang.EN, today.minusDays(6), today),
        ).inOrder()
        compose.onNodeWithText("Revise all from this week (4 words)").assertExists()
    }

    @Test
    fun emptyHistoryAndUsedUpListSaySoKindly() {
        show(WordBankUiState(loading = false, section = WordsSection.HISTORY, langs = listOf(Lang.EN), exhausted = setOf(Lang.EN), today = today))
        compose.onNodeWithTag(TAG_HISTORY_EMPTY).assertIsDisplayed()
        compose.onNodeWithText("You've met every word in Mukhar's English list. New ones arrive with updates.").assertIsDisplayed()
    }
}
