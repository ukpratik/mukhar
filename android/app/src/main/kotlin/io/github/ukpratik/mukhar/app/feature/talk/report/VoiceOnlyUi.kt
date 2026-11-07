package io.github.ukpratik.mukhar.app.feature.talk.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.TileShape

/**
 * D-048: the report was made from the voice alone. Says why in one line, what's still possible, and offers to write
 * the transcript from the kept recording (or to add a key) without blocking the rest of the report.
 */
@Composable
fun VoiceOnlyCard(reason: VoiceOnlyReason, onWrite: () -> Unit, onKeys: () -> Unit, modifier: Modifier = Modifier) {
    MukharCard(modifier = modifier.fillMaxWidth().testTag(TAG_VOICE_ONLY), shape = TileShape) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.voice_only_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                stringResource(
                    when (reason) {
                        VoiceOnlyReason.NO_ENGINE -> R.string.voice_only_no_engine
                        VoiceOnlyReason.OFFLINE -> R.string.voice_only_offline
                        VoiceOnlyReason.SERVICE -> R.string.voice_only_service
                        VoiceOnlyReason.AUDIO_GONE -> R.string.voice_only_gone
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (reason != VoiceOnlyReason.AUDIO_GONE) {
                Text(
                    stringResource(if (reason == VoiceOnlyReason.NO_ENGINE) R.string.voice_only_kept_key else R.string.voice_only_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (reason == VoiceOnlyReason.NO_ENGINE) {
                    // Nothing on this phone can write it yet: a key is the way forward (adding one writes it automatically).
                    Button(onClick = onKeys, modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Key, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.voice_only_keys))
                    }
                    TextButton(onClick = onWrite, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(TAG_VOICE_ONLY_WRITE)) {
                        Text(stringResource(R.string.voice_only_try_again))
                    }
                } else {
                    Button(onClick = onWrite, modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp).testTag(TAG_VOICE_ONLY_WRITE)) {
                        Icon(Icons.Rounded.TextSnippet, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.voice_only_write))
                    }
                }
            }
        }
    }
}

/** US-079: after a talk, mark its prompt as done so Speak Studio never picks it again (undo-able). */
@Composable
fun PromptDoneCard(done: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    MukharCard(modifier = modifier.fillMaxWidth().testTag(TAG_PROMPT_DONE), shape = TileShape) {
        if (done) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.prompt_done_state), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.undo)) }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.prompt_done_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.prompt_done_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(onClick = { onChange(true) }, modifier = Modifier.padding(top = 10.dp).heightIn(min = 48.dp).testTag(TAG_PROMPT_DONE_CTA)) {
                    Icon(Icons.Rounded.TaskAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.prompt_done_cta))
                }
            }
        }
    }
}

const val TAG_VOICE_ONLY = "report_voice_only"
const val TAG_VOICE_ONLY_WRITE = "report_voice_only_write"
const val TAG_PROMPT_DONE = "report_prompt_done"
const val TAG_PROMPT_DONE_CTA = "report_prompt_done_cta"
