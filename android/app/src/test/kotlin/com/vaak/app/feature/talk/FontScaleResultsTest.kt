package com.vaak.app.feature.talk

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.app.feature.talk.report.ReportActions
import com.vaak.app.feature.talk.report.ReportContent
import com.vaak.app.feature.talk.report.ReportUiState
import com.vaak.data.repo.TalkStatus
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NFR-008 / design-system §10.5: S11 Results at 200% font scale on a small phone. No text may clip or truncate, and
 * the key actions stay reachable with ≥ 48 dp targets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleResultsTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = ReportActions(onBack = {}, onSayAgain = {}, onRecordAgain = {}, onSettings = {})

    private fun show(state: ReportUiState, fontScale: Float = 2f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                VaakTheme(darkTheme = false) { ReportContent(state, {}, actions) }
            }
        }
        compose.waitForIdle()
    }

    private fun ready(lang: Lang) = TalkFixtures.report(lang = lang)

    private fun noFeedback(lang: Lang) =
        TalkFixtures.report(lang = lang, status = TalkStatus.FEEDBACK_FAILED, withFeedback = false, errorCode = "LLM_FAILED")

    /** Every text whose laid-out lines don't fit its node, or that was ellipsized. */
    private fun clippedTexts(): List<String> {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertWithMessage("text nodes laid out").that(nodes.size).isGreaterThan(20)
        return nodes.mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val r = results.firstOrNull() ?: return@mapNotNull null
            val width = r.size.width
            val height = r.size.height
            val clipped = r.multiParagraph.height > height + 1 ||
                (0 until r.lineCount).any { r.getLineRight(it) - r.getLineLeft(it) > width + 1 || r.isLineEllipsized(it) }
            if (clipped) r.layoutInput.text.text else null
        }
    }

    private fun assertNoClipping() {
        assertWithMessage("texts clipped or truncated at 200%").that(clippedTexts()).isEmpty()
    }

    /** Touch size: nodes scrolled out of view have empty clipped bounds, so take the larger of the two. */
    private fun SemanticsNodeInteraction.assertTargetAtLeast48(): SemanticsNodeInteraction {
        val node = fetchSemanticsNode()
        val min = with(compose.density) { 48.dp.toPx() } - 0.5f
        val h = maxOf(node.touchBoundsInRoot.height, node.size.height.toFloat())
        val w = maxOf(node.touchBoundsInRoot.width, node.size.width.toFloat())
        assertWithMessage("height of ${node.config}").that(h).isAtLeast(min)
        assertWithMessage("width of ${node.config}").that(w).isAtLeast(min)
        return this
    }

    private fun assertScrolledCta(node: SemanticsNodeInteraction) {
        node.performScrollTo().assertHasClickAction().assertTargetAtLeast48()
    }

    private fun assertReadyCtas() {
        compose.onNodeWithText("Say it again", substring = true).assertHasClickAction().assertTargetAtLeast48()
        compose.onNodeWithText("Done").assertHasClickAction().assertTargetAtLeast48()
        compose.onNodeWithContentDescription("Report this feedback").assertHasClickAction().assertTargetAtLeast48()
        assertScrolledCta(compose.onNodeWithContentDescription("About Pace"))
        assertScrolledCta(compose.onAllNodesWithText("Save line")[0])
        assertScrolledCta(compose.onNodeWithText("Something off? Report this feedback"))
    }

    @Test
    fun englishResultsFitAt200Percent() {
        show(ready(Lang.EN))
        assertNoClipping()
        assertReadyCtas()
    }

    @Test
    fun hindiResultsFitAt200Percent() {
        show(ready(Lang.HI))
        assertNoClipping()
        assertReadyCtas()
    }

    @Test
    fun englishNoFeedbackFitsAt200Percent() {
        show(noFeedback(Lang.EN))
        assertNoClipping()
        assertScrolledCta(compose.onNodeWithText("Try again"))
        assertScrolledCta(compose.onNodeWithContentDescription("About Pace"))
    }

    @Test
    fun hindiNoFeedbackFitsAt200Percent() {
        show(noFeedback(Lang.HI))
        assertNoClipping()
        assertScrolledCta(compose.onNodeWithText("Try again"))
    }

    private fun assertEveryClickableAtLeast48() {
        val count = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertWithMessage("clickable nodes").that(count).isGreaterThan(5)
        for (i in 0 until count) {
            val node = compose.onAllNodes(hasClickAction())[i]
            // Partly scrolled-out nodes have clipped touch bounds; the sticky CTA bar has nothing to scroll.
            runCatching { node.performScrollTo() }
            node.assertTargetAtLeast48()
        }
    }

    @Test
    fun everyInteractiveNodeInResultsIsAtLeast48dp() {
        show(ready(Lang.EN), fontScale = 1f)
        assertEveryClickableAtLeast48()
    }

    @Test
    fun everyInteractiveNodeInHindiResultsIsAtLeast48dpAt200Percent() {
        show(ready(Lang.HI))
        assertEveryClickableAtLeast48()
    }
}
