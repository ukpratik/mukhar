package com.vaak.app.feature.progress

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.domain.progress.TrendRange
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** NFR-008 on S16: each then-and-now row is one sentence, and the screen reflows at 200 % text on a small phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class ProgressA11yTest {
    @get:Rule
    val compose = createComposeRule()
    private val now = Instant.parse("2026-11-06T14:00:00Z")
    private val state = ProgressMapper.map(ProgressFixtures.many, null, TrendRange.TWELVE, EntitlementSnapshot.FREE, now, ProgressFixtures.zone, talksEnabled = true)

    @Test
    fun compareRowsReadBothValuesAndTheChange() {
        compose.setContent { VaakTheme { ProgressContent(state, ProgressActions()) } }
        val cd = compose.onNodeWithTag("compare_pace").performScrollTo().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertThat(cd).matches("Pace: baseline \\d+ wpm, latest \\d+ wpm\\. .+")
        val fillers = compose.onNodeWithTag("compare_fillers").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertThat(fillers).matches("Fillers: baseline [\\d.]+ /min, latest [\\d.]+ /min\\. .+")
    }

    @Test
    fun atTwiceTheTextSizeTilesStackAndNothingIsClipped() {
        RuntimeEnvironment.setFontScale(2f)
        var scale = 0f
        compose.setContent {
            scale = LocalDensity.current.fontScale
            VaakTheme { ProgressContent(state, ProgressActions()) }
        }
        compose.waitForIdle()
        assertThat(scale).isEqualTo(2f)
        // One column: the second total sits below the first, not beside it.
        val totals = compose.onNodeWithTag(TAG_TOTALS).fetchSemanticsNode()
        assertThat(totals.children.map { it.boundsInRoot.left }.distinct()).hasSize(1)
        compose.onNodeWithText("Then and now").performScrollTo()
        compose.onNodeWithTag("compare_variety").performScrollTo()
        assertNoClippedText()
    }

    private fun assertNoClippedText() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        assertThat(nodes).isNotEmpty()
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.forEach { r ->
                val text = r.layoutInput.text.text
                assertWithMessage("height of “$text”").that(r.multiParagraph.height).isAtMost(r.size.height + 1f)
                for (i in 0 until r.lineCount) {
                    assertWithMessage("line $i of “$text”").that(r.getLineRight(i) - r.getLineLeft(i)).isAtMost(r.size.width + 1f)
                    assertWithMessage("ellipsis in “$text”").that(r.isLineEllipsized(i)).isFalse()
                }
            }
        }
    }
}
