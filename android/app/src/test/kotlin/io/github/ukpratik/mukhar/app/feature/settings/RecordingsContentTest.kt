package io.github.ukpratik.mukhar.app.feature.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.repo.RecordingItem
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-080 Settings → Recordings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, mb: Long, pending: Boolean = false, awaiting: Boolean = false) = RecordingRowUi(
        RecordingItem(id, 1_793_590_200_000L, "Asia/Kolkata", Lang.EN, Track.PROFESSIONAL, "en-002", "talk", 180_000, mb * 1024 * 1024, "/r/$id.aac", pending, awaiting),
        "Tell us about a project you're proud of.",
    )

    @Test
    fun showsUsageLimitAndRowsWithBadges() {
        val state = RecordingsUiState(loading = false, rows = listOf(row("b", 1, awaiting = true), row("a", 2, pending = true)), totalBytes = 3L * 1024 * 1024, capMb = 500)
        compose.setContent { MukharTheme { RecordingsContent(state, RecordingsActions()) } }
        compose.onNodeWithText("3.0 MB of 500 MB used").assertExists()
        compose.onNodeWithText("Transcript can still be written").assertExists()
        compose.onNodeWithTag(TAG_RECORDINGS_LIST).performScrollToNode(hasText("Waiting for analysis"))
        compose.onNodeWithText("Waiting for analysis").assertExists()
    }

    @Test
    fun changingTheLimitAndDeletingAll() {
        var cap = 0
        var all = 0
        val state = RecordingsUiState(loading = false, rows = listOf(row("a", 2)), totalBytes = 2L * 1024 * 1024)
        compose.setContent { MukharTheme { RecordingsContent(state, RecordingsActions(onCap = { cap = it }, onDeleteAll = { all++ })) } }
        compose.onNodeWithTag("${TAG_RECORDINGS_CAP}1000").performClick()
        assertThat(cap).isEqualTo(1000)
        compose.onNodeWithTag(TAG_RECORDINGS_DELETE_ALL).performScrollTo().performClick()
        compose.onNodeWithTag(TAG_RECORDINGS_CONFIRM_ALL).performClick()
        assertThat(all).isEqualTo(1)
    }

    @Test
    fun deletingOneAsksFirst() {
        var deleted: String? = null
        val state = RecordingsUiState(loading = false, rows = listOf(row("a", 2, pending = true)), totalBytes = 2L * 1024 * 1024)
        compose.setContent { MukharTheme { RecordingsContent(state, RecordingsActions(onDelete = { deleted = it })) } }
        compose.onNodeWithTag("${TAG_RECORDINGS_DELETE}a").performClick()
        compose.onNodeWithText("This talk hasn't been analysed yet. Without the recording it can't be.").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertThat(deleted).isEqualTo("a")
    }

    @Test
    fun emptyState() {
        compose.setContent { MukharTheme { RecordingsContent(RecordingsUiState(loading = false), RecordingsActions()) } }
        compose.onNodeWithText("No recordings on this phone").assertExists()
    }
}
