package io.github.ukpratik.mukhar.app.feature.settings

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.data.prefs.ReminderSettings
import io.github.ukpratik.mukhar.data.prefs.Routine
import io.github.ukpratik.mukhar.data.prefs.Settings
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.math.max

/**
 * NFR-008 / design-system §10.5: Settings at 200 % font size on a small phone. No text is clipped or truncated, and
 * the Delete all my data CTAs stay reachable and at least 48 dp. The scale is set on the configuration (not a
 * LocalDensity override) because the sheet and dialogs open their own windows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class FontScaleSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings = Settings(
        firstName = "Ananya", langs = setOf(Lang.EN, Lang.HI), tracks = setOf(GoalTrack.INTERVIEWS, GoalTrack.PROFESSIONAL),
        reminder = ReminderSettings(LocalTime.of(20, 30), Routine.DINNER), onboardingDone = true,
    )
    private val base = SettingsUiState(loading = false, settings = settings, talkCount = 46, wordCount = 128)

    private fun show(state: SettingsUiState) {
        RuntimeEnvironment.setFontScale(2f)
        var scale = 0f
        compose.setContent {
            scale = LocalDensity.current.fontScale
            MukharTheme { SettingsContent(state, SettingsActions()) }
        }
        compose.waitForIdle()
        assertThat(scale).isEqualTo(2f)
    }

    @Test
    fun mainListHasNoClippedText() {
        show(base)
        listOf("New words a day", "Weekly goal", "Stutter-friendly mode", "On this phone", "Mukhar Cloud · coming soon", "Privacy policy").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        // Above 150 % the segmented choices are a radio list, so "7 days" never truncates.
        compose.onNodeWithText("7 days").performScrollTo().assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithTag(TAG_DELETE).performScrollTo().assertIsDisplayed())
        assertNoClippedText()
    }

    @Test
    fun deleteSheetScrollsToItsCtas() {
        show(base.copy(delete = DeleteStage.CONFIRM))
        compose.onNodeWithText("Delete all your data?").assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithTag(TAG_DELETE_CHECK).performScrollTo().assertIsDisplayed())
        assertTouchTarget(compose.onNodeWithTag(TAG_DELETE_CONFIRM).performScrollTo().assertIsDisplayed())
        assertTouchTarget(compose.onNodeWithTag(TAG_DELETE_KEEP).performScrollTo().assertIsDisplayed())
        assertNoClippedText()
    }

    @Test
    fun deletingSaysSoPolitely() {
        show(base.copy(delete = DeleteStage.DELETING, deleteUnderstood = true))
        compose.onNodeWithTag(TAG_DELETING).performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.LiveRegion))
        assertNoClippedText()
    }

    @Test
    fun deletedScreenKeepsStartFreshReachable() {
        show(base.copy(delete = DeleteStage.DONE))
        compose.onNodeWithText("Your data is deleted").performScrollTo().assertIsDisplayed()
        assertTouchTarget(compose.onNodeWithTag(TAG_START_FRESH).assertIsDisplayed())
        assertNoClippedText()
    }

    /** Max of the touch bounds and the node size: a node scrolled out of view has empty clipped bounds. */
    private fun assertTouchTarget(node: SemanticsNodeInteraction) {
        val n = node.fetchSemanticsNode()
        val minPx = with(compose.density) { 48f * density } // 48 dp, independent of the font scale
        val w = max(n.touchBoundsInRoot.width, n.size.width.toFloat())
        val h = max(n.touchBoundsInRoot.height, n.size.height.toFloat())
        assertWithMessage("touch target ${w}x$h px").that(minOf(w, h)).isAtLeast(minPx - 0.5f)
    }

    /**
     * Clipped = the text is taller than its box, a line is wider than its box, or a line is ellipsised.
     * (TextLayoutResult.didOverflowWidth isn't used: the semantics re-layout reports wrap-content text at max width.)
     */
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
