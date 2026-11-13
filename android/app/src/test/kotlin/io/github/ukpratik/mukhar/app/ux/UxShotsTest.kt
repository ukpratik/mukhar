package io.github.ukpratik.mukhar.app.ux

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.ukpratik.mukhar.app.feature.today.TodayContent
import io.github.ukpratik.mukhar.app.feature.today.TodayActions
import io.github.ukpratik.mukhar.app.feature.today.TodayFixtures
import io.github.ukpratik.mukhar.app.feature.words.DeckFixtures
import io.github.ukpratik.mukhar.app.feature.words.WordBankActions
import io.github.ukpratik.mukhar.app.feature.words.WordBankContent
import io.github.ukpratik.mukhar.app.feature.words.WordBankUiState
import io.github.ukpratik.mukhar.app.feature.words.WordsFixtures
import io.github.ukpratik.mukhar.app.feature.words.WordsSection
import io.github.ukpratik.mukhar.app.feature.words.revise.RevisionActions
import io.github.ukpratik.mukhar.app.feature.words.revise.RevisionContent
import io.github.ukpratik.mukhar.app.feature.words.revise.RevisionUiState
import io.github.ukpratik.mukhar.data.repo.RevisionSummary
import org.robolectric.RuntimeEnvironment
import io.github.ukpratik.mukhar.app.feature.words.deck.DeckActions
import io.github.ukpratik.mukhar.app.feature.words.deck.DeckContent
import io.github.ukpratik.mukhar.app.feature.words.deck.DeckUiState
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Design-review renders (not an assertion test): set MUKHAR_SHOTS to a folder and run it to write PNGs of key screens
 * at a typical phone size. Skipped otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class UxShotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("MUKHAR_SHOTS")

    private fun shot(name: String, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        compose.setContent { MukharTheme { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out!!).mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun deckWithSnackbar() = shot("deck_snackbar") {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { host.showSnackbar("Got it · lucid", "Undo", duration = SnackbarDuration.Indefinite) }
        DeckContent(DeckUiState(false, DeckFixtures.state().copy(showHint = true), voices = mapOf(Lang.EN to true)), DeckActions(), snackbar = host)
    }

    @Test
    fun deckSmallPhone() {
        RuntimeEnvironment.setQualifiers("w360dp-h640dp-xhdpi")
        shot("deck_small") {
            DeckContent(DeckUiState(false, DeckFixtures.state().copy(practisedToday = 6), voices = mapOf(Lang.EN to true)), DeckActions())
        }
    }

    @Test
    fun deckHindi() = shot("deck_hindi") {
        DeckContent(DeckUiState(false, DeckFixtures.state(cards = listOf(DeckFixtures.sateek, DeckFixtures.lucid)), voices = mapOf(Lang.HI to true)), DeckActions())
    }

    private val base = WordBankUiState(
        loading = false, langs = listOf(Lang.EN, Lang.HI), tab = Lang.EN, deck = DeckFixtures.state().copy(later = 2),
        revision = RevisionSummary(6, 4, 15), starred = listOf(WordsFixtures.articulate),
        words = listOf(WordsFixtures.pivotal, WordsFixtures.articulate, WordsFixtures.concise),
    )

    @Test
    fun wordsLearn() = shot("words_learn") { WordBankContent(base, WordBankActions()) }

    @Test
    fun wordsRevise() = shot("words_revise") { WordBankContent(base.copy(section = WordsSection.REVISE), WordBankActions()) }

    @Test
    fun wordsMine() = shot("words_mine") { WordBankContent(base.copy(section = WordsSection.MY_WORDS), WordBankActions()) }

    @Test
    fun revisionPrompt() = shot("revision_prompt") {
        RevisionContent(RevisionUiState(loading = false, cards = listOf(WordsFixtures.articulateCard), voices = mapOf(Lang.EN to true)), RevisionActions())
    }

    @Test
    fun revisionAnswer() = shot("revision_answer") {
        RevisionContent(RevisionUiState(loading = false, cards = listOf(WordsFixtures.articulateCard), revealed = true, countsToday = true, voices = mapOf(Lang.EN to true)), RevisionActions())
    }

    @Test
    fun today() = shot("today") {
        TodayContent(TodayFixtures.midDay.copy(deck = DeckFixtures.state()), TodayActions())
    }
}
