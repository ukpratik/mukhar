package com.vaak.app.feature.soundlab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaak.app.R
import com.vaak.app.feature.talk.common.ArtCircle
import com.vaak.ui.text.mixedScript
import com.vaak.ui.theme.VaakCard

const val TAG_SOUNDLAB_ENTRY = "soundlab_entry"

/** The Practice tab's way into Sound Lab (S08 → S22): one tappable `.card.row`. */
@Composable
fun SoundLabEntryCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.soundlab_entry_title)
    val body = stringResource(R.string.soundlab_entry_body)
    VaakCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(TAG_SOUNDLAB_ENTRY).semantics(mergeDescendants = true) { contentDescription = "$title. $body" }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtCircle(Icons.Rounded.Hearing, size = 44.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(mixedScript(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}
