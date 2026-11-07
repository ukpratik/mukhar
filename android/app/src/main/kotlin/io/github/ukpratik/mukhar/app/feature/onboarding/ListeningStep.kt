package io.github.ukpratik.mukhar.app.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.settings.CloudSoonCard
import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import io.github.ukpratik.mukhar.ui.theme.MukharSpace

/** 2A (ADR-008): "How should Mukhar listen?" after the disclosure, with honest limits for each choice. */
@Composable
fun ListeningBody(selected: AnalysisMode, onSelect: (AnalysisMode) -> Unit) {
    Column {
        ObHeadline(stringResource(R.string.listen_title))
        ObLead(stringResource(R.string.listen_lead))
        Column(Modifier.padding(top = MukharSpace.l).selectableGroup(), verticalArrangement = Arrangement.spacedBy(MukharSpace.m)) {
            Choice(
                stringResource(R.string.listen_device_title),
                stringResource(R.string.listen_device_body),
                stringResource(R.string.listen_device_download),
                selected.effective == AnalysisMode.ON_DEVICE,
                TAG_LISTEN_DEVICE,
            ) { onSelect(AnalysisMode.ON_DEVICE) }
            Choice(
                stringResource(R.string.listen_keys_title),
                stringResource(R.string.listen_keys_body),
                null,
                selected.effective == AnalysisMode.OWN_KEYS,
                TAG_LISTEN_KEYS,
            ) { onSelect(AnalysisMode.OWN_KEYS) }
            CloudSoonCard()
        }
    }
}

@Composable
private fun Choice(title: String, body: String, extra: String?, selected: Boolean, tag: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).testTag(tag),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (extra != null) {
                    Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun ListeningCta(onContinue: () -> Unit) {
    CtaBar {
        PrimaryCta(stringResource(R.string.ob_continue), onContinue, arrow = true, modifier = Modifier.testTag(TAG_LISTEN_CONTINUE))
    }
}

const val TAG_LISTEN_DEVICE = "listen_device"
const val TAG_LISTEN_KEYS = "listen_keys"
const val TAG_LISTEN_CONTINUE = "listen_continue"
