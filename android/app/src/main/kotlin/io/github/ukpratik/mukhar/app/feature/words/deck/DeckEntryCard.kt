package io.github.ukpratik.mukhar.app.feature.words.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.data.repo.DeckState
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme

/** US-081: "New words · 2 of 5 today · Next: articulate" on Words and Today; opens the deck. */
@Composable
fun DeckEntryCard(deck: DeckState?, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    if (deck == null) return
    val next = deck.cards.firstOrNull()
    val progress = if (deck.goalMet) pluralStringResource(R.plurals.deck_progress_extra, deck.decidedToday, deck.decidedToday) else pluralStringResource(R.plurals.deck_progress, deck.goal, deck.decidedToday, deck.goal)
    val line = when {
        next == null -> stringResource(R.string.deck_entry_none)
        deck.goalMet -> stringResource(R.string.deck_entry_done_today)
        else -> stringResource(R.string.deck_entry_next, next.lemma)
    }
    val a11y = stringResource(R.string.deck_entry_a11y, progress, line)
    val open = next != null
    MukharCard(
        modifier.fillMaxWidth().testTag(TAG_DECK_ENTRY).clearAndSetSemantics {
            contentDescription = a11y
            if (open) {
                role = Role.Button
                onClick { onOpen(); true }
            }
        },
        onClick = if (open) onOpen else null,
    ) {
        Row(Modifier.padding(MukharSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp).clip(MaterialTheme.shapes.medium)
                    .background(if (deck.goalMet) MaterialTheme.colorScheme.tertiaryContainer else MukharTheme.colors.accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Style, null, tint = if (deck.goalMet) MaterialTheme.colorScheme.tertiary else MukharTheme.colors.accentInk)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.deck_entry_title), style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
                Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    line,
                    style = if (next?.lang == Lang.HI && !deck.goalMet) MukharTheme.type.hiBodyMedium else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (open) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

const val TAG_DECK_ENTRY = "deck_entry"
