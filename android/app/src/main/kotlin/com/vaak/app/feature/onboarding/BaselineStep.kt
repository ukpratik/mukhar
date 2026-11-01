package com.vaak.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaak.app.R
import com.vaak.data.baseline.BaselineRecorder
import com.vaak.ui.theme.TileShape
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

private fun mmss(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

/** S05: an optional 1-minute baseline (US-003). Recording goes through [BaselineRecorder]. */
@Composable
fun BaselineBody(state: OnboardingUiState) {
    val hindi = state.langChoice == LangChoice.HI
    Column {
        when (val b = state.baseline) {
            is BaselineUi.Recording -> BaselineRecording(b.elapsedMs)
            is BaselineUi.Saved -> {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(30.dp))
                }
                ObHeadline(stringResource(R.string.baseline_saved_title), Modifier.padding(top = 14.dp))
                ObLead(stringResource(R.string.baseline_saved_lead, mmss(b.durationMs)))
            }
            else -> {
                Eyebrow(stringResource(R.string.ob_optional))
                ObHeadline(stringResource(R.string.baseline_title))
                ObLead(stringResource(R.string.baseline_lead))
                if (b == BaselineUi.Later) {
                    ObQuestion(stringResource(R.string.baseline_wait_on_today))
                    BaselineCardPreview()
                } else {
                    PromptCard(hindi)
                    Row(Modifier.fillMaxWidth().padding(top = VaakSpace.m), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WhyTile(stringResource(R.string.baseline_then_now), stringResource(R.string.baseline_then_now_body), Modifier.weight(1f))
                        WhyTile(stringResource(R.string.baseline_hear), stringResource(R.string.baseline_hear_body), Modifier.weight(1f))
                    }
                    if (b == BaselineUi.TooShort) {
                        Text(
                            stringResource(R.string.baseline_too_short),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().padding(top = VaakSpace.m).clip(MaterialTheme.shapes.medium)
                                .background(VaakTheme.colors.accentTint).padding(14.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptCard(hindi: Boolean) {
    VaakCard(Modifier.fillMaxWidth().padding(top = VaakSpace.l)) {
        Column(Modifier.padding(VaakSpace.l)) {
            Eyebrow(stringResource(if (hindi) R.string.baseline_prompt_eyebrow_hi else R.string.baseline_prompt_eyebrow_en))
            Text(
                stringResource(R.string.baseline_prompt),
                style = VaakTheme.type.prompt,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(Icons.Outlined.Description, stringResource(R.string.baseline_chip_report), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                InfoChip(Icons.Outlined.Flag, stringResource(R.string.baseline_chip_day0), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Static info chip (design `.chip`): not tappable. */
@Composable
fun InfoChip(icon: ImageVector?, text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaakSpace.xs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun WhyTile(title: String, body: String, modifier: Modifier) {
    VaakCard(modifier, shape = TileShape) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = VaakSpace.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The Today card the baseline will wait as (S05 state 4, S07 state 1). */
@Composable
fun BaselineCardPreview(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    VaakCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(VaakSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_baseline_title), style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize), color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.today_baseline_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BaselineRecording(elapsedMs: Long) {
    val a11y = (elapsedMs / 1000).toInt().let { pluralStringResource(R.plurals.baseline_recording_a11y, it, it) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaakSpace.s),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(VaakTheme.colors.accent))
            Text(stringResource(R.string.baseline_recording), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    VaakCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(Modifier.padding(VaakSpace.l)) {
            Eyebrow(stringResource(R.string.baseline_recording_eyebrow))
            Text(stringResource(R.string.baseline_prompt), style = VaakTheme.type.prompt, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = VaakSpace.xl).semantics(mergeDescendants = true) { contentDescription = a11y; liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mmss(elapsedMs), style = com.vaak.ui.theme.timerStyle(), color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.baseline_of), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ProgressTrack(elapsedMs.toFloat() / BaselineRecorder.MAX_MS)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("1:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaselineCta(
    state: OnboardingUiState,
    onRecord: () -> Unit,
    onLater: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onKeep: (Boolean) -> Unit,
    onSavedContinue: () -> Unit,
    onDiscard: (Boolean) -> Unit,
) {
    when (val b = state.baseline) {
        is BaselineUi.Recording -> CtaBar {
            StopControl(stringResource(R.string.baseline_stop), stringResource(R.string.baseline_stop_a11y), onStop)
            PrivateLine(stringResource(R.string.baseline_private), Icons.Rounded.Lock)
        }
        is BaselineUi.Saved -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = onSavedContinue, sheetState = sheetState) {
                KeepRecordingSheet(state.keepBaselineAudio, onKeep, onSavedContinue)
            }
        }
        else -> Column {
            if (b == BaselineUi.Later) LaterToast(onNext)
            CtaBar {
                AccentCta(stringResource(R.string.baseline_record), onRecord, leading = Icons.Rounded.Mic)
                TextCta(stringResource(R.string.baseline_later), onLater)
            }
        }
    }
    if (state.confirmDiscardBaseline) {
        AlertDialog(
            onDismissRequest = { onDiscard(false) },
            title = { Text(stringResource(R.string.baseline_discard_title)) },
            confirmButton = { TextButton(onClick = { onDiscard(true) }) { Text(stringResource(R.string.baseline_discard)) } },
            dismissButton = { TextButton(onClick = { onDiscard(false) }) { Text(stringResource(R.string.baseline_keep_recording)) } },
        )
    }
}

/** "Keep this recording?": asked once, the switch defaults to off (US-003). */
@Composable
fun KeepRecordingSheet(keep: Boolean, onKeep: (Boolean) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.padding(start = VaakSpace.gutter, end = VaakSpace.gutter, bottom = VaakSpace.xxl)) {
        Text(stringResource(R.string.baseline_keep_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.baseline_keep_lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = VaakSpace.xs, bottom = VaakSpace.m))
        val label = stringResource(R.string.baseline_keep_switch)
        Row(
            Modifier.fillMaxWidth().toggleable(keep, role = Role.Switch, onValueChange = onKeep).semantics { contentDescription = label }.padding(vertical = VaakSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.baseline_keep_size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = keep, onCheckedChange = null)
        }
        InfoNote(Icons.Outlined.Insights, stringResource(R.string.baseline_keep_note), Modifier.padding(vertical = VaakSpace.m))
        PrimaryCta(stringResource(R.string.ob_continue), onContinue, arrow = true)
    }
}

@Composable
private fun LaterToast(onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = VaakSpace.gutter).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.inverseSurface).padding(start = VaakSpace.l, end = VaakSpace.xs)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaakSpace.s),
    ) {
        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.baseline_later_toast), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.weight(1f))
        TextButton(onClick = onNext) {
            Text(stringResource(R.string.baseline_next), color = MaterialTheme.colorScheme.inversePrimary)
        }
    }
}
