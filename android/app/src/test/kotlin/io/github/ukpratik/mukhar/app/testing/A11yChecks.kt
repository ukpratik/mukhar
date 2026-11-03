package io.github.ukpratik.mukhar.app.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/**
 * NFR-008 (TC-263) checks over the merged semantics tree, used instead of ATF: Compose's
 * `enableAccessibilityChecks()` (ATF) never reports under Robolectric (D-036), so these are the same rules as plain
 * assertions:
 *  - every clickable, toggleable or selectable node has a label (text, content description or state) and a touch
 *    area of at least 48 × 48 dp (Compose's minimum-interactive padding counts, as it does for TalkBack);
 *  - no laid-out text overflows its box (clipped or overlapping text at large font scales).
 * Nodes that are scrolled out of the viewport are measured too (their layout is real, only their position is off).
 */
object A11yChecks {
    private const val TOUCH_DP = 48f
    private const val TOLERANCE_DP = 0.5f

    fun SemanticsNodeInteractionsProvider.assertAccessible(checkText: Boolean = true) {
        val problems = mutableListOf<String>()
        onAllNodes(isRoot()).fetchSemanticsNodes().forEach { root -> visit(root, problems, checkText) }
        check(problems.isEmpty()) { "Accessibility problems (${problems.size}):\n" + problems.joinToString("\n") }
    }

    private fun visit(node: SemanticsNode, problems: MutableList<String>, checkText: Boolean) {
        val c = node.config
        val interactive = SemanticsActions.OnClick in c || SemanticsProperties.ToggleableState in c ||
            SemanticsProperties.Selected in c
        val disabled = SemanticsProperties.Disabled in c
        val hidden = SemanticsProperties.InvisibleToUser in c
        if (interactive && !disabled && !hidden) {
            val label = listOfNotNull(
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
                c.getOrNull(SemanticsProperties.EditableText)?.text,
                c.getOrNull(SemanticsProperties.StateDescription),
            ).joinToString(" ").trim()
            if (label.isEmpty()) problems += "unlabelled ${describe(node)}"
            val density = node.layoutInfo.density
            val bounds = node.touchBoundsInRoot
            // Unclipped size (a node scrolled out of view has empty clipped bounds), or the expanded touch area.
            val w = with(density) { maxOf(bounds.width, node.size.width.toFloat()).toDp() }
            val h = with(density) { maxOf(bounds.height, node.size.height.toFloat()).toDp() }
            if (w < (TOUCH_DP - TOLERANCE_DP).dp || h < (TOUCH_DP - TOLERANCE_DP).dp) {
                problems += "touch target ${w.value.toInt()}×${h.value.toInt()} dp < 48 dp: ${describe(node, label)}"
            }
        }
        if (checkText) {
            c.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { get ->
                val results = mutableListOf<TextLayoutResult>()
                if (get(results)) {
                    results.filter(::clipped).forEach {
                        problems += "clipped text \"${it.layoutInput.text.text.take(60)}\" (${describe(node)})"
                    }
                }
            }
        }
        node.children.forEach { visit(it, problems, checkText) }
    }

    /**
     * Text that doesn't fit: lines taller than the box, a line wider than the box, or a line cut with an ellipsis.
     * (`didOverflowWidth` alone is unreliable here: the semantics re-layout reports the paragraph at max width.)
     */
    private fun clipped(r: TextLayoutResult): Boolean {
        val tooTall = r.multiParagraph.height > r.size.height + 1
        val tooWide = (0 until r.lineCount).any { r.getLineRight(it) - r.getLineLeft(it) > r.size.width + 1 }
        val cut = (0 until r.lineCount).any { r.isLineEllipsized(it) } ||
            (r.lineCount == r.layoutInput.maxLines && r.didOverflowHeight)
        return tooTall || tooWide || cut
    }

    private fun describe(node: SemanticsNode, label: String = "") =
        "node #${node.id}${if (label.isNotEmpty()) " \"${label.take(60)}\"" else ""} " +
            "tag=${node.config.getOrNull(SemanticsProperties.TestTag)} role=${node.config.getOrNull(SemanticsProperties.Role)}"
}
