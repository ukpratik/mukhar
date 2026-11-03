package io.github.ukpratik.mukhar.app.feature.words

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewActions
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewContent
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewUiState
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** NFR-008: Word Gym at 200 % text on a small phone. The grades go 2 × 2 and no label or interval truncates. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class ReviewFontScaleTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: ReviewUiState) {
        RuntimeEnvironment.setFontScale(2f)
        var scale = 0f
        compose.setContent {
            scale = LocalDensity.current.fontScale
            MukharTheme { ReviewContent(state, ReviewActions()) }
        }
        compose.waitForIdle()
        assertThat(scale).isEqualTo(2f)
    }

    @Test
    fun gradesGoTwoByTwoWithoutClipping() {
        show(WordsFixtures.back)
        val again = compose.onNodeWithTag("grade_again").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val hard = compose.onNodeWithTag("grade_hard").fetchSemanticsNode().boundsInRoot
        val good = compose.onNodeWithTag("grade_good").fetchSemanticsNode().boundsInRoot
        assertThat(hard.top).isEqualTo(again.top) // Again and Hard share the first row
        assertThat(good.top).isAtLeast(again.bottom) // Good and Easy below
        assertNoClippedText()
    }

    @Test
    fun cardFrontFitsWithoutClipping() {
        show(WordsFixtures.front)
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
