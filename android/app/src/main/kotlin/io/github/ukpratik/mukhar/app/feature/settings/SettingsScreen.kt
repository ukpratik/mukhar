package io.github.ukpratik.mukhar.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.BuildConfig
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.debug.DebugMenuEntry
import io.github.ukpratik.mukhar.app.feature.onboarding.PrimaryCta
import io.github.ukpratik.mukhar.app.feature.onboarding.TextCta
import io.github.ukpratik.mukhar.app.feature.onboarding.label
import io.github.ukpratik.mukhar.app.feature.onboarding.timeLabel
import io.github.ukpratik.mukhar.app.feature.progress.PlanRowUi
import io.github.ukpratik.mukhar.app.feature.progress.planText
import io.github.ukpratik.mukhar.app.feature.talk.common.ArtCircle
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharAppBar
import io.github.ukpratik.mukhar.app.feature.talk.common.langLabel
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.data.prefs.ReminderSettings
import io.github.ukpratik.mukhar.data.work.ReminderCopy
import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import java.time.LocalTime

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onPlan: () -> Unit = {},
    val onDisclosure: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onLangs: (Set<Lang>) -> Unit = {},
    val onTracks: (Set<GoalTrack>) -> Unit = {},
    val onDailyWords: (Int) -> Unit = {},
    val onWeeklyGoal: (Int) -> Unit = {},
    val onReminderOn: (Boolean) -> Unit = {},
    val onReminderTime: (LocalTime) -> Unit = {},
    val onReminderPhrase: (String) -> Unit = {},
    val onStutterFriendly: (Boolean) -> Unit = {},
    val onTransliteration: (Boolean) -> Unit = {},
    val onKeepBaselineAudio: (Boolean) -> Unit = {},
    val onAnalytics: (Boolean) -> Unit = {},
    val onDeleteOpen: () -> Unit = {},
    val onDeleteUnderstood: (Boolean) -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteCancel: () -> Unit = {},
    val onStartFresh: () -> Unit = {},
    val onAnalysisMode: (AnalysisMode) -> Unit = {},
    val onDownloadModel: (Lang) -> Unit = {},
    val onSoftCap: (Boolean) -> Unit = {},
    val onKeys: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onDisclosure: () -> Unit,
    onStartFresh: () -> Unit,
    onKeys: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    SettingsContent(
        state,
        SettingsActions(
            onBack = onBack,
            onPlan = onPlan,
            onDisclosure = onDisclosure,
            onOpenUrl = { runCatching { uri.openUri(it) } },
            onLangs = viewModel::onLangs,
            onTracks = viewModel::onTracks,
            onDailyWords = viewModel::onDailyWords,
            onWeeklyGoal = viewModel::onWeeklyGoal,
            onReminderOn = viewModel::onReminderOn,
            onReminderTime = viewModel::onReminderTime,
            onReminderPhrase = viewModel::onReminderPhrase,
            onStutterFriendly = viewModel::onStutterFriendly,
            onTransliteration = viewModel::onTransliteration,
            onKeepBaselineAudio = viewModel::onKeepBaselineAudio,
            onAnalytics = viewModel::onAnalytics,
            onDeleteOpen = viewModel::onDeleteOpen,
            onDeleteUnderstood = viewModel::onDeleteUnderstood,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDeleteCancel = viewModel::onDeleteCancel,
            onStartFresh = onStartFresh,
            onAnalysisMode = viewModel::onAnalysisMode,
            onDownloadModel = viewModel::onDownloadModel,
            onSoftCap = viewModel::onSoftCap,
            onKeys = onKeys,
        ),
    )
}

private enum class SettingsDialog { NONE, LANGS, TRACKS, TIME, PHRASE }

/** S19: four groups (Practice, Reminders, Coaching, Privacy) plus Plan; Delete all my data at the bottom, neutral. */
@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    if (state.delete == DeleteStage.DONE) {
        Deleted(actions.onStartFresh)
        return
    }
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
    val s = state.settings
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        MukharAppBar(stringResource(R.string.settings_title), stringResource(R.string.plan_back_a11y), actions.onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.xxl).testTag(TAG_SETTINGS_LIST)) {
            Group(R.string.settings_group_practice) {
                LinkRow(Icons.Outlined.Translate, stringResource(R.string.settings_langs), s.langs.sortedBy { it.ordinal }.map { stringResource(langLabel(it)) }.joinToString(", ")) { dialog = SettingsDialog.LANGS }
                Divider()
                LinkRow(Icons.Outlined.Flag, stringResource(R.string.settings_tracks), GoalTrack.entries.filter { it in s.tracks }.map { stringResource(it.label()) }.joinToString(", ")) { dialog = SettingsDialog.TRACKS }
                Divider()
                SegRow(Icons.Outlined.MenuBook, stringResource(R.string.settings_daily_words), stringResource(R.string.settings_daily_words_sub), (1..5).toList(), s.dailyNewWords, { it.toString() }, actions.onDailyWords)
                Divider()
                SegRow(Icons.Outlined.EventAvailable, stringResource(R.string.settings_weekly_goal), stringResource(R.string.settings_weekly_goal_sub), listOf(3, 5, 7), state.weeklyGoal, { pluralStringResource(R.plurals.settings_days, it, it) }, actions.onWeeklyGoal)
            }
            Group(R.string.settings_group_analysis) { AnalysisGroupContent(state.analysis, actions) }
            CloudSoonCard(Modifier.padding(top = 10.dp))
            Group(R.string.settings_group_reminders) {
                val reminder = s.reminder
                SwitchRow(Icons.Outlined.Notifications, stringResource(R.string.settings_reminder), stringResource(R.string.settings_reminder_sub), reminder != null, actions.onReminderOn, TAG_SWITCH_REMINDER)
                if (reminder != null) {
                    val res = LocalResources.current
                    Divider()
                    LinkRow(Icons.Outlined.Schedule, stringResource(R.string.settings_reminder_time), ReminderCopy.phrase(res, reminder), value = timeLabel(reminder.time)) { dialog = SettingsDialog.TIME }
                    Divider()
                    LinkRow(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.settings_reminder_phrase), reminder.customPhrase ?: stringResource(R.string.settings_reminder_phrase_none)) { dialog = SettingsDialog.PHRASE }
                }
            }
            Group(R.string.settings_group_coaching) {
                SwitchRow(Icons.Outlined.SelfImprovement, stringResource(R.string.settings_stutter), stringResource(R.string.settings_stutter_sub), s.stutterFriendly, actions.onStutterFriendly, TAG_SWITCH_STUTTER)
                Divider()
                SwitchRow(Icons.Outlined.Abc, stringResource(R.string.settings_translit), stringResource(R.string.settings_translit_sub), s.transliteration, actions.onTransliteration, TAG_SWITCH_TRANSLIT)
            }
            if (state.showPlan) {
                Group(R.string.settings_group_plan) {
                    val (title, sub) = planText(state.plan)
                    LinkRow(Icons.Outlined.WorkspacePremium, title, sub, value = planValue(state.plan), onClick = actions.onPlan, tag = TAG_PLAN)
                }
            }
            Group(R.string.settings_group_privacy) {
                SwitchRow(Icons.Outlined.GraphicEq, stringResource(R.string.settings_keep_baseline), stringResource(R.string.settings_keep_baseline_sub), s.keepBaselineAudio, actions.onKeepBaselineAudio, TAG_SWITCH_BASELINE)
                Divider()
                SwitchRow(Icons.Outlined.Insights, stringResource(R.string.settings_analytics), stringResource(R.string.settings_analytics_sub), s.analyticsConsent, actions.onAnalytics, TAG_SWITCH_ANALYTICS)
                Divider()
                LinkRow(Icons.Outlined.CloudUpload, stringResource(R.string.settings_what_leaves), stringResource(R.string.settings_what_leaves_sub), onClick = actions.onDisclosure)
                Divider()
                LinkRow(
                    Icons.Outlined.Policy, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_sub),
                    trailing = Icons.AutoMirrored.Rounded.OpenInNew, clickLabel = stringResource(R.string.settings_open_in_browser),
                ) { actions.onOpenUrl(Links.PRIVACY_POLICY) }
            }
            OutlinedButton(
                onClick = actions.onDeleteOpen,
                modifier = Modifier.fillMaxWidth().padding(top = MukharSpace.l).heightIn(min = 52.dp).testTag(TAG_DELETE),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.settings_delete))
            }
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = MukharSpace.l, bottom = MukharSpace.m),
            )
            DebugMenuEntry() // debug builds only (src/debug); a no-op in release
        }
    }
    when (dialog) {
        SettingsDialog.LANGS -> MultiPick(
            title = stringResource(R.string.settings_langs),
            options = Lang.entries,
            selected = s.langs,
            label = { stringResource(langLabel(it)) },
            max = Lang.entries.size,
            onDone = { actions.onLangs(it); dialog = SettingsDialog.NONE },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.TRACKS -> MultiPick(
            title = stringResource(R.string.settings_tracks),
            options = GoalTrack.entries,
            selected = s.tracks,
            label = { stringResource(it.label()) },
            max = GoalTrack.MAX_PICKED,
            onDone = { actions.onTracks(it); dialog = SettingsDialog.NONE },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.TIME -> s.reminder?.let { r ->
            TimeDialog(r.time, onDone = { actions.onReminderTime(it); dialog = SettingsDialog.NONE }, onDismiss = { dialog = SettingsDialog.NONE })
        }
        SettingsDialog.PHRASE -> s.reminder?.let { r ->
            PhraseDialog(r.customPhrase.orEmpty(), onDone = { actions.onReminderPhrase(it); dialog = SettingsDialog.NONE }, onDismiss = { dialog = SettingsDialog.NONE })
        }
        SettingsDialog.NONE -> Unit
    }
    if (state.delete == DeleteStage.CONFIRM || state.delete == DeleteStage.DELETING) DeleteSheet(state, actions)
}

@Composable
private fun planValue(plan: PlanRowUi): String? =
    if (plan.status == io.github.ukpratik.mukhar.domain.billing.SubscriptionStatus.FREE || plan.status == io.github.ukpratik.mukhar.domain.billing.SubscriptionStatus.EXPIRED) {
        stringResource(R.string.settings_try_premium)
    } else {
        null
    }

@Composable
private fun Group(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = MukharSpace.l, bottom = 6.dp).semantics { heading() },
    )
    MukharCard(Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun RowText(title: String, sub: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!sub.isNullOrEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    sub: String?,
    value: String? = null,
    trailing: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    tag: String? = null,
    clickLabel: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}
            .let { if (tag != null) it.testTag(tag) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        RowText(title, sub, Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        } else {
            Icon(trailing, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        RowText(title, sub, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> SegRow(icon: ImageVector, title: String, sub: String, options: List<T>, selected: T, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            RowText(title, sub, Modifier.weight(1f))
        }
        // The visible title names the group (no container label). Above 150 % font size the segments would
        // truncate, so the options become a radio list (design-system §10.5: never truncate).
        if (LocalDensity.current.fontScale > SEG_MAX_FONT_SCALE) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).selectableGroup()) {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = o == selected, role = Role.RadioButton) { onPick(o) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = null)
                        Text(label(o), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                options.forEachIndexed { i, o ->
                    SegmentedButton(selected = o == selected, onClick = { onPick(o) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                        Text(label(o))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> MultiPick(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    max: Int,
    onDone: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by androidx.compose.runtime.remember { mutableStateOf(selected.toList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    val on = o in picked
                    val enabled = on || picked.size < max
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(value = on, enabled = enabled, role = Role.Checkbox) { picked = if (on) picked - o else picked + o },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, enabled = enabled)
                        Text(label(o), modifier = Modifier.padding(start = 12.dp))
                    }
                }
                // Why the other boxes are off, in words and announced (not only greyed out).
                if (max < options.size && picked.size >= max) {
                    Text(
                        stringResource(R.string.settings_pick_max, max),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).testTag(TAG_PICK_MAX).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked.toSet()) }, enabled = picked.isNotEmpty()) { Text(stringResource(R.string.settings_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: LocalTime, onDone: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(time.hour, time.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time)) },
        // The clock dial doesn't fit large text; the keyboard input does, and the dialog body scrolls.
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) TimeInput(picker) else TimePicker(picker)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.settings_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun PhraseDialog(phrase: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(phrase) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_phrase)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ReminderSettings.MAX_PHRASE) },
                singleLine = true,
                supportingText = { Text("${text.length} / ${ReminderSettings.MAX_PHRASE}") },
                label = { Text(stringResource(R.string.settings_reminder_phrase_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.settings_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** The confirm sheet: says exactly what goes; the primary is disabled until the box is ticked (S19 state 2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteSheet(state: SettingsUiState, actions: SettingsActions) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = actions.onDeleteCancel, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.l).testTag(TAG_DELETE_SHEET)) {
            Text(stringResource(R.string.delete_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.delete_lead), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            SubHead(R.string.delete_on_phone)
            ListLine(Icons.Outlined.Mic, pluralStringResource(R.plurals.delete_talks, state.talkCount, state.talkCount))
            ListLine(Icons.Outlined.MenuBook, pluralStringResource(R.plurals.delete_words, state.wordCount, state.wordCount))
            SubHead(R.string.delete_on_servers)
            ListLine(Icons.Outlined.CloudOff, stringResource(R.string.delete_server_rows))
            ListLine(Icons.Outlined.Info, stringResource(R.string.delete_play_note))
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)
                    .toggleable(value = state.deleteUnderstood, role = Role.Checkbox, onValueChange = actions.onDeleteUnderstood)
                    .testTag(TAG_DELETE_CHECK),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.deleteUnderstood, onCheckedChange = null)
                Text(stringResource(R.string.delete_understand), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
            }
            PrimaryCta(
                stringResource(R.string.delete_confirm),
                actions.onDeleteConfirm,
                enabled = state.deleteUnderstood && state.delete == DeleteStage.CONFIRM,
                modifier = Modifier.padding(top = 12.dp).testTag(TAG_DELETE_CONFIRM),
            )
            if (state.delete == DeleteStage.DELETING) {
                Text(
                    stringResource(R.string.delete_deleting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(TAG_DELETING).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            TextCta(stringResource(R.string.delete_keep), actions.onDeleteCancel, Modifier.testTag(TAG_DELETE_KEEP))
        }
    }
}

@Composable
private fun SubHead(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun ListLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** After delete (S19 state 3): calm confirmation, then the app restarts at Welcome. */
@Composable
private fun Deleted(onStartFresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(horizontal = MukharSpace.gutter).testTag(TAG_DELETED),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The message scrolls at large font sizes; the CTA stays pinned below it.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ArtCircle(Icons.Rounded.Check)
            Text(stringResource(R.string.deleted_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
            Text(stringResource(R.string.deleted_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
        PrimaryCta(stringResource(R.string.deleted_start), onStartFresh, modifier = Modifier.padding(top = MukharSpace.m, bottom = MukharSpace.l).testTag(TAG_START_FRESH))
    }
}

const val TAG_SETTINGS_LIST = "settings_list"
const val TAG_SWITCH_REMINDER = "settings_reminder"
const val TAG_SWITCH_STUTTER = "settings_stutter"
const val TAG_SWITCH_TRANSLIT = "settings_translit"
const val TAG_SWITCH_BASELINE = "settings_baseline"
const val TAG_SWITCH_ANALYTICS = "settings_analytics"
const val TAG_PLAN = "settings_plan"
const val TAG_DELETE = "settings_delete"
const val TAG_DELETE_SHEET = "delete_sheet"
const val TAG_DELETE_CHECK = "delete_check"
const val TAG_DELETE_CONFIRM = "delete_confirm"
const val TAG_DELETED = "deleted"
const val TAG_DELETE_KEEP = "delete_keep"
const val TAG_DELETING = "delete_deleting"
const val TAG_START_FRESH = "deleted_start_fresh"
const val TAG_PICK_MAX = "settings_pick_max"

/** Above this the segmented controls become radio lists. */
private const val SEG_MAX_FONT_SCALE = 1.5f

/** Above this the time dial becomes keyboard input. */
private const val LARGE_FONT_SCALE = 1.3f
