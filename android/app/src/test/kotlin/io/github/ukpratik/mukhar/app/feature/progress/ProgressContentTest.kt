package io.github.ukpratik.mukhar.app.feature.progress

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.billing.EntitlementSnapshot
import io.github.ukpratik.mukhar.domain.progress.TrendRange
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** S16 states on Robolectric: empty (< 2 talks), trends with the range switch, insight, then and now. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ProgressContentTest {
    @get:Rule
    val compose = createComposeRule()
    private val now = Instant.parse("2026-11-06T14:00:00Z")

    private fun state(data: io.github.ukpratik.mukhar.data.repo.ProgressData, range: TrendRange = TrendRange.TWELVE) =
        ProgressMapper.map(data, null, range, EntitlementSnapshot.FREE, now, ProgressFixtures.zone, talksEnabled = true)

    @Test
    fun emptyStateSaysWhatUnlocksTrends() {
        var talk = 0
        compose.setContent { MukharTheme { ProgressContent(state(ProgressFixtures.one), ProgressActions(onTalk = { talk++ })) } }
        compose.onNodeWithText("Progress").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.onNodeWithText("Your trends start at talk 2").assertExists()
        compose.onNodeWithText("talk").assertExists() // "1 talk" total
        compose.onNodeWithText("words learned").assertExists()
        compose.onAllNodes(hasText("Comfortable pace")).assertCountEquals(0)
        compose.onNodeWithText("Record your starting point").performScrollTo()
        compose.onNodeWithText("Do today's talk").performClick()
        assertThat(talk).isEqualTo(1)
    }

    @Test
    fun trendsShowFourSparklinesWithPlainWordDeltas() {
        var range: TrendRange? = null
        compose.setContent { MukharTheme { ProgressContent(state(ProgressFixtures.many), ProgressActions(onRange = { range = it })) } }
        compose.onNodeWithTag(TAG_TRENDS).assertExists()
        compose.onNodeWithContentDescription("Comfortable pace: 100 % of talks. Up from 0%", substring = true).assertExists()
        compose.onNodeWithContentDescription("Fillers: 2.0 /min. Down from 6.0 · lower is better").assertExists()
        compose.onNodeWithContentDescription("Word variety: 0.70. Up from 0.60").assertExists()
        compose.onNodeWithContentDescription("Talks a week", substring = true).assertExists()
        compose.onNodeWithText("4 weeks").performClick()
        assertThat(range).isEqualTo(TrendRange.FOUR)
        compose.onNodeWithText("Then and now").performScrollTo()
        compose.onNodeWithContentDescription("Now in your comfortable band", substring = true).performScrollTo() // one sentence per row
        compose.onNodeWithText("Audio not kept").performScrollTo()
        compose.onNodeWithText("Free plan").performScrollTo()
    }
}
