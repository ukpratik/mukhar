package io.github.ukpratik.mukhar.app.feature.words.lookup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.dictionary.LookupEntry
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.data.repo.LookupStatus
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D-056 look-up: the search screen (recent, results, no match) and the word page (meanings, save, star, AI). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LookupContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val queries = mutableListOf<String>()
    private val unknown = mutableListOf<Pair<Lang, String>>()
    private val lookedUp = mutableListOf<Pair<Lang, String>>()
    private var added = 0
    private val stars = mutableListOf<Boolean>()
    private var explained = 0
    private var addKey = 0

    private fun search(state: LookupUiState) = compose.setContent {
        MukharTheme {
            LookupContent(
                state,
                LookupActions(onQuery = { queries += it }, onOpen = { opened += it.id }, onExplainUnknown = { l, w -> unknown += l to w }),
                autoFocus = false,
            )
        }
    }

    private fun page(state: LookupEntryUiState) = compose.setContent {
        MukharTheme {
            LookupEntryContent(
                state,
                LookupEntryActions(
                    onAdd = { added++ }, onStar = { stars += it }, onExplain = { explained++ }, onAddKey = { addKey++ },
                    onLookUp = { l, w -> lookedUp += l to w },
                ),
            )
        }
    }

    @Test
    fun emptyQueryShowsTheHowToThenRecentWords() {
        search(LookupUiState(preparing = false))
        compose.onNodeWithTag(TAG_LOOKUP_EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(TAG_LOOKUP_FIELD).performTextInput("lucid")
        assertThat(queries).containsExactly("lucid")
    }

    @Test
    fun recentLookUpsOpenTheirPage() {
        search(LookupUiState(preparing = false, recent = listOf(LookupFixtures.happyHit)))
        compose.onNodeWithText("Recent", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag("lookup_hit_en:happy").performClick()
        assertThat(opened).containsExactly("en:happy")
    }

    @Test
    fun resultsShowMeaningOtherLanguageAndMukharBadge() {
        search(
            LookupUiState(
                query = "art", searched = "art", preparing = false,
                results = listOf(LookupFixtures.articulateHit, LookupFixtures.happyHit, LookupFixtures.khushHit),
            ),
        )
        // Each row is one TalkBack item: word, part of speech, meaning, the other language, and the badge.
        compose.onNodeWithContentDescription("articulate", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("One of Mukhar's words", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("ख़ुश, आनंदित, सुखी", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("lookup_hit_hi:खुश").performClick()
        assertThat(opened).containsExactly("hi:खुश")
    }

    @Test
    fun noMatchOffersTheAiOnlyWithAKey() {
        search(LookupUiState(query = "zeitgeist", searched = "zeitgeist", preparing = false, hasKey = false))
        compose.onNodeWithTag(TAG_LOOKUP_NO_MATCH).assertIsDisplayed()
        compose.onAllNodesWithTag(TAG_LOOKUP_EXPLAIN).assertCountEquals(0)
    }

    @Test
    fun noMatchWithAKeyExplainsTheTypedWord() {
        search(LookupUiState(query = "zeitgeist ", searched = "zeitgeist", preparing = false, hasKey = true))
        compose.onNodeWithTag(TAG_LOOKUP_EXPLAIN).performClick()
        assertThat(unknown).containsExactly(Lang.EN to "zeitgeist")
    }

    @Test
    fun aDictionaryWordShowsMeaningsHindiWordsAndStrongerWords() {
        page(LookupEntryUiState(loading = false, entry = LookupFixtures.happy, hasKey = true))
        compose.onNodeWithTag(TAG_ENTRY_WORD).assertIsDisplayed()
        compose.onNodeWithText("/ˈhæpi/").assertIsDisplayed()
        compose.onNodeWithText("enjoying or showing or marked by joy or pleasure").assertIsDisplayed()
        compose.onNodeWithText("Also: felicitous").assertIsDisplayed()
        compose.onNodeWithText("Forms: happier, happiest").assertIsDisplayed()
        compose.onNodeWithText("सुखी").performScrollTo().performClick()
        assertThat(lookedUp).containsExactly(Lang.HI to "सुखी")
        compose.onNodeWithTag(TAG_ENTRY_STRONGER).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("delighted").assertIsDisplayed()
        compose.onNodeWithText("Dictionary: Open English WordNet (CC BY 4.0)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_ENTRY_ADD).performClick()
        assertThat(added).isEqualTo(1)
        compose.onNodeWithTag(TAG_ENTRY_STAR).performClick()
        assertThat(stars).containsExactly(true)
    }

    @Test
    fun aMukharWordShowsItsCardWithBothMeanings() {
        page(LookupEntryUiState(loading = false, entry = LookupFixtures.articulate))
        compose.onNodeWithTag(TAG_ENTRY_MUKHAR).assertIsDisplayed()
        compose.onNodeWithText("Mukhar word").assertIsDisplayed()
        compose.onNodeWithText("स्पष्ट रूप से कहना").assertIsDisplayed()
        compose.onAllNodesWithTag(TAG_ENTRY_MEANINGS).assertCountEquals(0)
    }

    @Test
    fun aSavedWordShowsInMyWordsAndTheStarCanBeRemoved() {
        page(LookupEntryUiState(loading = false, entry = LookupFixtures.happy, status = LookupStatus(saved = true, starred = true)))
        compose.onNodeWithTag(TAG_ENTRY_SAVED).assertIsDisplayed()
        compose.onAllNodesWithTag(TAG_ENTRY_ADD).assertCountEquals(0)
        compose.onNodeWithContentDescription("Remove the star").performClick()
        assertThat(stars).containsExactly(false)
    }

    @Test
    fun withoutAKeyTheAiLinksToSettings() {
        page(LookupEntryUiState(loading = false, entry = LookupFixtures.khush, hasKey = false))
        compose.onNodeWithText("In English", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Dictionary: Wiktionary (CC BY-SA 4.0)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add a key").performScrollTo().performClick()
        assertThat(addKey).isEqualTo(1)
        compose.onAllNodesWithTag(TAG_ENTRY_AI_BUTTON).assertCountEquals(0)
    }

    @Test
    fun withAKeyExplainAndAFailureOffersRetry() {
        page(LookupEntryUiState(loading = false, entry = LookupFixtures.happy, hasKey = true, ai = AiUi.Failed(IdeasFailure.OFFLINE)))
        compose.onNodeWithText("No internet", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertThat(explained).isEqualTo(1)
    }

    @Test
    fun theAiAnswerShowsBothMeaningsAndExamples() {
        page(LookupEntryUiState(loading = false, entry = LookupFixtures.happy, hasKey = true, ai = AiUi.Done(LookupFixtures.lucidAi)))
        compose.onNodeWithTag(TAG_ENTRY_AI).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("साफ़ और समझने में आसान").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Say it: LOO-sid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("“Her answer was lucid.”").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("coherent").performScrollTo().performClick()
        assertThat(lookedUp).containsExactly(Lang.EN to "coherent")
    }

    @Test
    fun aWordOnlyTheAiKnowsCanBeSavedOnlyAfterItExplains() {
        val unknownWord = LookupEntry.unknown(Lang.EN, "zeitgeist")
        page(LookupEntryUiState(loading = false, entry = unknownWord, hasKey = true))
        compose.onNodeWithTag(TAG_ENTRY_ADD).assertIsNotEnabled()
        compose.onNodeWithTag(TAG_ENTRY_AI_BUTTON).performClick()
        assertThat(explained).isEqualTo(1)
    }

    @Test
    fun notFound() {
        page(LookupEntryUiState(loading = false, entry = null))
        compose.onNodeWithTag(TAG_ENTRY_NOT_FOUND).assertIsDisplayed()
    }
}
