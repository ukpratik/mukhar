package io.github.ukpratik.mukhar.app.feature.words.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height kept free between the card and the actions for [CompactSnackbarHost], so a toast never covers a button. */
val SnackbarSlot: Dp = 56.dp

/** A small one-line toast with an action (D-055): it sits in [SnackbarSlot], above the actions, never over them. */
@Composable
fun CompactSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier.testTag(TAG_COMPACT_SNACKBAR)) { data -> CompactSnackbar(data) }
}

@Composable
private fun CompactSnackbar(data: SnackbarData) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 3.dp,
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).heightIn(min = 44.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 10.dp),
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(label, color = MaterialTheme.colorScheme.inversePrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

enum class RoundStyle { OUTLINED, TONAL, PRIMARY }

/**
 * A round action with its label under it (Not for me · Later · Star · Got it; Again · Knew it). The whole column is
 * one TalkBack button with [description]; the label is always visible, so no action is icon-only.
 */
@Composable
fun RoundAction(
    icon: ImageVector,
    label: String,
    description: String,
    style: RoundStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color? = null,
) {
    // The label is part of the target too: the whole column takes the tap.
    Column(
        modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp).clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick { onClick(); true }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val iconSize = if (size > 56.dp) 30.dp else 24.dp
        when (style) {
            RoundStyle.OUTLINED -> OutlinedIconButton(
                onClick = onClick,
                modifier = Modifier.size(size),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) { Icon(icon, null, Modifier.size(iconSize), tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) }
            RoundStyle.TONAL -> FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(size),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) { Icon(icon, null, Modifier.size(iconSize), tint = tint ?: MaterialTheme.colorScheme.onSecondaryContainer) }
            RoundStyle.PRIMARY -> FilledIconButton(onClick = onClick, modifier = Modifier.size(size)) {
                Icon(icon, null, Modifier.size(iconSize), tint = tint ?: MaterialTheme.colorScheme.onPrimary)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** A row of [RoundAction]s, evenly spread, bottom-aligned so a bigger primary action lines up its label. */
@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) { content() }
}

/** Keeps [SnackbarSlot] free under the card. */
@Composable
fun SnackbarSpace(host: SnackbarHostState?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(SnackbarSlot), contentAlignment = Alignment.Center) {
        if (host != null) CompactSnackbarHost(host)
    }
}

const val TAG_COMPACT_SNACKBAR = "compact_snackbar"
