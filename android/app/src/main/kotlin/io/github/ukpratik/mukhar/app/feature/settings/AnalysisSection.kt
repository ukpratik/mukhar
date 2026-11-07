package io.github.ukpratik.mukhar.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.talk.common.langLabel
import io.github.ukpratik.mukhar.data.byok.ByokPrices
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.KeyInfo
import io.github.ukpratik.mukhar.data.local.ModelState
import io.github.ukpratik.mukhar.data.local.OfflineModel
import io.github.ukpratik.mukhar.data.prefs.ByokUsage
import io.github.ukpratik.mukhar.data.prefs.DEFAULT_SOFT_CAP_INR
import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharCard

/** Settings → Analysis (ADR-008): mode, offline models, own keys and spending, and the Mukhar Cloud card. */
data class AnalysisUi(
    val mode: AnalysisMode = AnalysisMode.DEFAULT,
    val models: List<OfflineModel> = listOf(
        OfflineModel(Lang.EN, "vosk-model-small-en-in-0.4", 36, ModelState.NOT_INSTALLED),
        OfflineModel(Lang.HI, "vosk-model-small-hi-0.22", 42, ModelState.NOT_INSTALLED),
    ),
    val canDownload: Boolean = false,
    val keys: Map<ByokProvider, KeyInfo> = emptyMap(),
    val usage: ByokUsage = ByokUsage(),
    val softCapOn: Boolean = false,
    val softCapInr: Int = DEFAULT_SOFT_CAP_INR,
)

@Composable
fun ColumnScope.AnalysisGroupContent(a: AnalysisUi, actions: SettingsActions) {
    Text(
        stringResource(R.string.analysis_mode_heading),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
    Column(Modifier.selectableGroup()) {
        ModeRow(stringResource(R.string.mode_on_device), stringResource(R.string.mode_on_device_sub), a.mode.effective == AnalysisMode.ON_DEVICE, true, TAG_MODE_DEVICE) {
            actions.onAnalysisMode(AnalysisMode.ON_DEVICE)
        }
        ModeRow(stringResource(R.string.mode_own_keys), stringResource(R.string.mode_own_keys_sub), a.mode.effective == AnalysisMode.OWN_KEYS, true, TAG_MODE_KEYS) {
            actions.onAnalysisMode(AnalysisMode.OWN_KEYS)
        }
        ModeRow(stringResource(R.string.mode_cloud), stringResource(R.string.mode_cloud_sub), false, AnalysisMode.CLOUD_ENABLED, TAG_MODE_CLOUD) {
            actions.onAnalysisMode(AnalysisMode.CLOUD)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        stringResource(R.string.models_heading),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
    a.models.forEach { ModelRow(it, a.canDownload) { actions.onDownloadModel(it.lang) } }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    KeysRow(a.keys, actions.onKeys)
    if (a.mode.effective == AnalysisMode.OWN_KEYS || a.keys.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SpendRow(a.usage)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = a.softCapOn, role = Role.Switch) { actions.onSoftCap(it) }
                .padding(horizontal = 16.dp, vertical = 12.dp).testTag(TAG_SOFT_CAP),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Savings, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.soft_cap, a.softCapInr), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.soft_cap_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = a.softCapOn, onCheckedChange = null)
        }
    }
}

@Composable
private fun ModeRow(title: String, sub: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelRow(m: OfflineModel, canDownload: Boolean, onDownload: () -> Unit) {
    val name = stringResource(langLabel(m.lang))
    val status = stringResource(
        when (m.state) {
            ModelState.INSTALLED -> R.string.model_installed
            ModelState.DOWNLOADING -> R.string.model_downloading
            ModelState.FAILED -> R.string.model_failed
            ModelState.NOT_INSTALLED -> R.string.model_not_installed
        },
    )
    val soonCd = stringResource(R.string.model_download_soon_cd, name)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).testTag(TAG_MODEL_PREFIX + m.lang.code),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.model_row, name, m.sizeMb), style = MaterialTheme.typography.labelLarge)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (m.state == ModelState.NOT_INSTALLED || m.state == ModelState.FAILED) {
            TextButton(
                onClick = onDownload,
                enabled = canDownload,
                modifier = Modifier.heightIn(min = 48.dp).then(if (canDownload) Modifier else Modifier.semantics { contentDescription = soonCd }),
            ) { Text(stringResource(R.string.model_download)) }
        }
    }
}

@Composable
private fun KeysRow(keys: Map<ByokProvider, KeyInfo>, onClick: () -> Unit) {
    val summary = if (keys.isEmpty()) {
        stringResource(R.string.keys_row_none)
    } else {
        ByokProvider.entries.filter { it in keys }.map { p ->
            "${stringResource(providerName(p))} ${stringResource(R.string.key_masked, keys.getValue(p).last4)}"
        }.joinToString(" · ")
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}.testTag(TAG_KEYS_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.keys_row), style = MaterialTheme.typography.labelLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SpendRow(u: ByokUsage) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag(TAG_SPEND)) {
        Text(
            stringResource(R.string.spend_line, pluralStringResource(R.plurals.spend_talks, u.talks, u.talks), ByokPrices.roundedInr(u)),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(stringResource(R.string.spend_note, ByokPrices.AS_OF), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Mukhar Cloud · coming soon" (D-039): one honest card instead of the paywall. */
@Composable
fun CloudSoonCard(modifier: Modifier = Modifier) {
    MukharCard(modifier.fillMaxWidth().testTag(TAG_CLOUD_SOON)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cloud_soon_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.cloud_soon_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                InfoChip(
                    stringResource(R.string.cloud_soon_chip),
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

fun providerName(p: ByokProvider): Int = when (p) {
    ByokProvider.DEEPGRAM -> R.string.provider_deepgram
    ByokProvider.SARVAM -> R.string.provider_sarvam
    ByokProvider.ANTHROPIC -> R.string.provider_anthropic
}

const val TAG_MODE_DEVICE = "analysis_mode_device"
const val TAG_MODE_KEYS = "analysis_mode_keys"
const val TAG_MODE_CLOUD = "analysis_mode_cloud"
const val TAG_MODEL_PREFIX = "analysis_model_"
const val TAG_KEYS_ROW = "analysis_keys"
const val TAG_SPEND = "analysis_spend"
const val TAG_SOFT_CAP = "analysis_soft_cap"
const val TAG_CLOUD_SOON = "cloud_soon"
