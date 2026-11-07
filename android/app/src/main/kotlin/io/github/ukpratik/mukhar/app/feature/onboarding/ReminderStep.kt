package io.github.ukpratik.mukhar.app.feature.onboarding

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.data.prefs.ReminderSettings
import io.github.ukpratik.mukhar.data.prefs.Routine
import io.github.ukpratik.mukhar.data.work.ReminderCopy
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Routine.icon(): ImageVector = when (this) {
    Routine.MORNING_TEA -> Icons.Outlined.EmojiFoodBeverage
    Routine.COMMUTE -> Icons.Outlined.DirectionsBus
    Routine.LUNCH -> Icons.Outlined.LunchDining
    Routine.WORKOUT -> Icons.Outlined.FitnessCenter
    Routine.DINNER -> Icons.Outlined.DinnerDining
    Routine.CUSTOM -> Icons.Outlined.Edit
}

/** "8:15" + "am" (or "20:15" + "" on a 24-hour phone). */
@Composable
fun timeParts(time: LocalTime): Pair<String, String> {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    if (is24) return time.format(DateTimeFormatter.ofPattern("H:mm")) to ""
    return time.format(DateTimeFormatter.ofPattern("h:mm")) to time.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH)).lowercase()
}

@Composable
fun timeLabel(time: LocalTime): String = timeParts(time).let { (t, ap) -> if (ap.isEmpty()) t else "$t $ap" }

/** The exact notification copy, bold phrase first (the S06 preview). */
@Composable
fun reminderBody(reminder: ReminderSettings): AnnotatedString {
    val res = androidx.compose.ui.platform.LocalResources.current
    val phrase = ReminderCopy.phrase(res, reminder)
    val body = ReminderCopy.body(res, reminder).toString()
    return buildAnnotatedString {
        append(body)
        addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), 0, phrase.length)
    }
}

/** S06: "After my ___ at HH:MM" and the weekly goal (US-004, US-032). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReminderBody(
    state: OnboardingUiState,
    onRoutine: (Routine) -> Unit,
    onCustom: (String) -> Unit,
    onTime: (LocalTime) -> Unit,
    onGoal: (Int) -> Unit,
) {
    val res = androidx.compose.ui.platform.LocalResources.current
    var picking by rememberSaveable { mutableStateOf(false) }
    Column {
        ObHeadline(stringResource(R.string.reminder_title))
        ObLead(stringResource(R.string.reminder_lead))
        Text(
            stringResource(R.string.reminder_after_my),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = MukharSpace.xl, bottom = MukharSpace.xs).semantics { heading() },
        )
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Routine.entries.forEach { routine ->
                val selected = state.routine == routine
                FilterChip(
                    selected = selected,
                    onClick = { onRoutine(routine) },
                    label = { Text(ReminderCopy.routineLabel(res, routine), style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(if (selected) Icons.Rounded.Check else routine.icon(), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    },
                    modifier = Modifier.semantics { this.role = Role.RadioButton },
                )
            }
        }
        val (t, ampm) = timeParts(state.time)
        val timeA11y = stringResource(R.string.reminder_time_a11y, timeLabel(state.time))
        if (state.routine == Routine.CUSTOM) {
            // The custom routine gets its own line, so "at" and the time never squeeze it at large font sizes.
            val label = stringResource(R.string.reminder_custom_label)
            OutlinedTextField(
                value = state.customPhrase,
                onValueChange = onCustom,
                singleLine = true,
                placeholder = { Text(label) },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = MukharSpace.m).semantics { contentDescription = label },
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = MukharSpace.m),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.reminder_at), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(
                Modifier.heightIn(min = MukharSpace.touch).clip(MaterialTheme.shapes.medium).clickable(role = Role.Button) { picking = true }
                    .semantics(mergeDescendants = true) { contentDescription = timeA11y }
                    .padding(horizontal = MukharSpace.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MukharSpace.xs),
            ) {
                Text(t, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                if (ampm.isNotEmpty()) Text(ampm, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            }
        }
        NotificationPreview(state.reminder, state.time, Modifier.padding(top = MukharSpace.l))
        ObQuestion(stringResource(R.string.reminder_days_q))
        WeeklyGoalPicker(state.weeklyGoal, onGoal, withSubLabels = true)
    }
    if (picking) {
        val pickerState = rememberTimePickerState(state.time.hour, state.time.minute, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(pickerState.hour, pickerState.minute))
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) } },
            title = { Text(stringResource(R.string.reminder_time_title)) },
            text = { TimePicker(pickerState) },
        )
    }
}

@Composable
private fun NotificationPreview(reminder: ReminderSettings, time: LocalTime, modifier: Modifier = Modifier) {
    val body = reminderBody(reminder)
    val a11y = stringResource(R.string.reminder_preview_a11y, body.text)
    MukharCard(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(MukharSpace.m), horizontalArrangement = Arrangement.spacedBy(MukharSpace.m)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                CappedFontScale { Text("M", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary) }
            }
            Column {
                Text(stringResource(R.string.reminder_preview_header, timeLabel(time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** 3 / 5 / 7 days (US-032). Also used by the streak sheet (S20). */
@Composable
fun WeeklyGoalPicker(goal: Int, onGoal: (Int) -> Unit, withSubLabels: Boolean, modifier: Modifier = Modifier) {
    val options = listOf(3 to R.string.goal_easy, 5 to R.string.goal_steady, 7 to R.string.goal_every_day)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (n, sub) ->
            SegmentedButton(
                selected = goal == n,
                onClick = { onGoal(n) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) {
                if (withSubLabels) {
                    // Read as "3 days, Easy" rather than "3, Easy".
                    val a11y = stringResource(R.string.goal_option_a11y, pluralStringResource(R.plurals.goal_days, n, n), stringResource(sub))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { contentDescription = a11y }) {
                        Text(n.toString(), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(sub), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                    }
                } else {
                    Text(pluralStringResource(R.plurals.goal_days, n, n), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ReminderCta(enabled: Boolean, onRemindMe: () -> Unit, onNone: () -> Unit) {
    CtaBar {
        PrimaryCta(stringResource(R.string.reminder_remind_me), onRemindMe, enabled = enabled, leading = Icons.Rounded.Notifications)
        TextCta(stringResource(R.string.reminder_none), onNone)
    }
}

/** S06 state 4: denial is respected; the plan is kept and nothing nags. */
@Composable
fun ReminderDeniedBody(state: OnboardingUiState) {
    val res = androidx.compose.ui.platform.LocalResources.current
    Column {
        val name = state.firstName.trim()
        ObHeadline(if (name.isEmpty()) stringResource(R.string.reminder_all_set) else stringResource(R.string.reminder_all_set_name, name))
        Row(
            Modifier.fillMaxWidth().padding(top = MukharSpace.s).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 14.dp, vertical = MukharSpace.m),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column {
                Text(stringResource(R.string.reminder_off_title), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.reminder_off_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        MukharCard(Modifier.fillMaxWidth().padding(top = MukharSpace.m)) {
            Column(Modifier.padding(MukharSpace.l)) {
                Eyebrow(stringResource(R.string.reminder_plan))
                IconLine(state.routine.icon(), stringResource(R.string.reminder_plan_when, ReminderCopy.phrase(res, state.reminder), timeParts(state.time).first))
                IconLine(Icons.Outlined.CalendarMonth, pluralStringResource(R.plurals.reminder_plan_days, state.weeklyGoal, state.weeklyGoal))
                if (state.warmupCredited) IconLine(Icons.Outlined.LocalFireDepartment, stringResource(R.string.reminder_plan_day1))
            }
        }
        InfoNote(Icons.Outlined.Lightbulb, stringResource(R.string.reminder_tip), Modifier.padding(top = MukharSpace.m))
    }
}

@Composable
fun ReminderDeniedCta(onToday: () -> Unit, onOpenSettings: () -> Unit) {
    CtaBar {
        PrimaryCta(stringResource(R.string.reminder_go_today), onToday, arrow = true)
        TextCta(stringResource(R.string.reminder_open_notif_settings), onOpenSettings)
    }
}

/** S06 state 5: one-time battery guide on Xiaomi, Oppo, Vivo and Realme (US-033). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemGuideSheet(oem: Oem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OemGuideContent(oem, onOpen, onDismiss)
    }
}

@Composable
fun OemGuideContent(oem: Oem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.padding(start = MukharSpace.gutter, end = MukharSpace.gutter, bottom = MukharSpace.xxl)) {
        Box(Modifier.padding(bottom = MukharSpace.m).size(52.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
        Text(stringResource(R.string.oem_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Text(stringResource(oem.lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MukharSpace.xs, bottom = MukharSpace.m))
        listOf(oem.step1, oem.step2).forEachIndexed { i, step ->
            Row(
                Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MukharSpace.m),
            ) {
                Box(
                    Modifier.sizeIn(minWidth = 28.dp, minHeight = 28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    CappedFontScale { Text((i + 1).toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                }
                Text(annotatedResource(step), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Column(Modifier.padding(top = MukharSpace.l), verticalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
            PrimaryCta(stringResource(R.string.oem_open), onOpen)
            TextCta(stringResource(R.string.oem_not_now), onDismiss)
        }
    }
}
