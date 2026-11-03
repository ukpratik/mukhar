package io.github.ukpratik.mukhar.app.feature.plan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S18 at 200% font scale on a small phone (design §10.5): the sticky bar keeps only the note and the CTA, nothing is
 * clipped, and Buy, Close, Restore and Manage stay reachable and at least 48 dp tall.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class FontScalePaywallTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun bigFont() = RuntimeEnvironment.setFontScale(2f)

    private fun show(state: PlanUiState) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                MukharTheme { PlanContent(state, PlanActions()) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun paywallWithTrial() {
        show(PlanFixtures.paywall())
        assertNoClippedText()
        reachable(compose.onNodeWithTag(TAG_BUY), "Buy", inScroll = false)
        reachable(compose.onNodeWithContentDescription("Close"), "Close", inScroll = false)
        reachable(compose.onNodeWithText("Restore purchase"), "Restore")
        compose.onNodeWithText("Monthly").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TAG_TIMELINE).performScrollTo()
    }

    @Test
    fun paywallWhileConfirming() {
        show(PlanFixtures.paywall().copy(confirming = true))
        assertNoClippedText()
        reachable(compose.onNodeWithTag(TAG_BUY), "Buy", inScroll = false)
        compose.onNodeWithText("Confirming…").assertIsDisplayed()
    }

    @Test
    fun planFactsDuringTheTrial() {
        show(PlanFixtures.trialFacts())
        assertNoClippedText()
        reachable(compose.onNodeWithText("Keep"), "Keep")
        reachable(compose.onNodeWithContentDescription("Manage subscription in Google Play"), "Manage (banner)")
        reachable(compose.onNodeWithText("Manage or cancel in Google Play"), "Manage")
    }

    /** [inScroll] false for the sticky bar and the top bar, which never scroll away. */
    private fun reachable(node: SemanticsNodeInteraction, name: String, inScroll: Boolean = true) {
        if (inScroll) node.performScrollTo()
        node.assertIsDisplayed().assertHasClickAction()
        val n = node.fetchSemanticsNode()
        // Nodes scrolled out of view have empty clipped bounds, so take the larger of the touch bounds and the size.
        val height = maxOf(n.touchBoundsInRoot.height, n.size.height.toFloat())
        val min = with(compose.density) { 48.dp.toPx() }
        assertWithMessage("$name height").that(height).isAtLeast(min)
    }

    /** Clipped = cut by maxLines, taller than its box, a line wider than its box, or an ellipsized line. */
    private fun assertNoClippedText() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertThat(nodes).isNotEmpty()
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.forEach { r -> assertWithMessage(describe(node, r)).that(clipped(r)).isFalse() }
        }
    }

    private fun clipped(r: TextLayoutResult): Boolean {
        if (r.multiParagraph.didExceedMaxLines || r.multiParagraph.height > r.size.height + 1) return true
        return (0 until r.lineCount).any { i -> r.getLineRight(i) - r.getLineLeft(i) > r.size.width + 1 || r.isLineEllipsized(i) }
    }

    private fun describe(node: SemanticsNode, r: TextLayoutResult): String =
        "text clipped at 200%: \"${node.config.getOrNull(SemanticsProperties.Text)?.joinToString()}\" " +
            "size=${r.size} content=${r.multiParagraph.width}x${r.multiParagraph.height} lines=${r.lineCount}"
}
