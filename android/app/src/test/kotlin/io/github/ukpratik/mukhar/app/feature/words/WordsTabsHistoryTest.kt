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

/** US-075 language tabs, US-081 Done (was US-077 History), Starred and Discarded on Robolectric. */
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
    fun doneGroupsByDayWithReviseActions() {
        var state by mutableStateOf(WordBankUiState(loading = false, langs = listOf(Lang.EN, Lang.HI), tab = Lang.EN, done = history, today = today))
        compose.setContent {
            MukharTheme {
                WordBankContent(state, WordBankActions(onEvent = { e -> if (e is WordBankEvent.Section) state = state.copy(section = e.section) }, onRevise = { l, f, t -> revised += Triple(l, f, t) }))
            }
        }
        compose.onNodeWithTag("words_seg_done").performClick()
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
    fun emptyDoneSaysWhatGoesThere() {
        show(WordBankUiState(loading = false, section = WordsSection.DONE, langs = listOf(Lang.EN), today = today))
        compose.onNodeWithTag(TAG_HISTORY_EMPTY).assertIsDisplayed()
    }

    @Test
    fun starredListsWordsWithPractiseAndUnstar() {
        val starred = mutableListOf<Pair<String, Boolean>>()
        val practised = mutableListOf<Lang?>()
        compose.setContent {
            MukharTheme {
                WordBankContent(
                    WordBankUiState(loading = false, section = WordsSection.STARRED, langs = listOf(Lang.EN), starred = listOf(WordsFixtures.articulate), today = today),
                    WordBankActions(onStar = { id, on -> starred += id to on }, onPractiseStarred = { practised += it }),
                )
            }
        }
        compose.onNodeWithText("Practise 1 starred word").performClick()
        assertThat(practised).containsExactly(null)
        compose.onNodeWithContentDescription("Unstar articulate").performClick()
        assertThat(starred).containsExactly("en:articulate" to false)
    }

    @Test
    fun emptyStarredExplainsTheStar() {
        show(WordBankUiState(loading = false, section = WordsSection.STARRED, langs = listOf(Lang.EN), today = today))
        compose.onNodeWithTag(TAG_STARRED_EMPTY).assertIsDisplayed()
    }

    @Test
    fun discardedWordsCanBeBroughtBack() {
        val back = mutableListOf<String>()
        var state by mutableStateOf(
            WordBankUiState(loading = false, section = WordsSection.DONE, langs = listOf(Lang.EN), discarded = listOf(DeckFixtures.lucid), today = today),
        )
        compose.setContent {
            MukharTheme {
                WordBankContent(
                    state,
                    WordBankActions(
                        onEvent = { e -> if (e is WordBankEvent.ToggleDiscarded) state = state.copy(showDiscarded = !state.showDiscarded) },
                        onBringBack = { back += it },
                    ),
                )
            }
        }
        compose.onNodeWithText("1 discarded word").assertIsDisplayed()
        compose.onNodeWithTag(TAG_DISCARDED_TOGGLE).performClick()
        compose.onNodeWithContentDescription("Bring back lucid to the new words").performScrollTo().performClick()
        assertThat(back).containsExactly("en:lucid")
    }

    @Test
    fun theNewWordsCardOpensTheDeckForTheTab() {
        val opened = mutableListOf<Lang?>()
        compose.setContent {
            MukharTheme {
                WordBankContent(
                    WordBankUiState(loading = false, langs = listOf(Lang.EN, Lang.HI), tab = Lang.HI, deck = DeckFixtures.state(), today = today),
                    WordBankActions(onOpenDeck = { opened += it }),
                )
            }
        }
        compose.onNodeWithTag(io.github.ukpratik.mukhar.app.feature.words.deck.TAG_DECK_ENTRY).performClick()
        assertThat(opened).containsExactly(Lang.HI)
    }
}
