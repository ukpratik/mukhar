package io.github.ukpratik.mukhar.app.feature.share

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.progress.ProgressFixtures
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S21 (US-071): the preview is the image; it holds metrics, streak and the app name, never transcript text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ShareContentTest {
    @get:Rule
    val compose = createComposeRule()

    /** What the user said in their latest talk. None of it may reach the card. */
    private val transcript = listOf("basically", "manager", "Umm so")

    private fun state(name: Boolean = false) = ShareViewModel.applyToggles(
        ShareUiState(loading = false, base = ShareViewModel.modelOf(ProgressFixtures.many), streak = 19, firstName = "Ananya", addName = name),
    )

    @Test
    fun previewShowsMetricsStreakAndAppNameButNoTranscript() {
        val s = state()
        compose.setContent { MukharTheme { ShareContent(s, ShareActions()) } }
        compose.onNodeWithContentDescription("4 weeks of speaking practice", substring = true).assertExists()
        compose.onNodeWithContentDescription("Fillers a minute: from 5.8 to 2.0", substring = true).assertExists()
        compose.onNodeWithContentDescription("19-day streak", substring = true).assertExists()
        compose.onNodeWithContentDescription("Mukhar. speaking coach", substring = true).assertExists()
        compose.onNodeWithText("Show my streak").assertExists()
        compose.onNodeWithText("Share").assertExists()
        transcript.forEach { word ->
            compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription(word, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }

    @Test
    fun cardTextIsOnlyNumbersDatesAndLabels() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val text = ShareCardText.of(state(name = true).model!!, res)
        assertThat(text.headline).isEqualTo("Ananya's 4 weeks of speaking practice")
        assertThat(text.eyebrow).isEqualTo("12 Oct → 6 Nov")
        assertThat(text.streak).isEqualTo("19-day streak")
        assertThat(text.all().joinToString(" ")).doesNotContainMatch("(?i)basically|manager|umm")
        val hidden = ShareCardText.of(state().model!!.copy(streak = null), res)
        assertThat(hidden.streak).isNull()
    }

    @Test
    fun hindiPracticeGetsADevanagariCard() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val model = state().model!!.copy(lang = Lang.HI, firstName = "रोहन", weeks = 6)
        val text = ShareCardText.of(model, res)
        assertThat(text.headline).isEqualTo("रोहन की 6 हफ़्तों की प्रगति")
        assertThat(text.rows.first().first).isEqualTo("भराव शब्द / मिनट")
    }

    @Test
    fun nothingToShareWithoutABaseline() {
        compose.setContent { MukharTheme { ShareContent(ShareUiState(loading = false), ShareActions()) } }
        compose.onNodeWithText("Nothing to share yet").assertExists()
    }
}
