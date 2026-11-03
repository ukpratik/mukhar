package io.github.ukpratik.mukhar.app.feature.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.Eyebrow
import io.github.ukpratik.mukhar.app.feature.onboarding.InfoNote
import io.github.ukpratik.mukhar.app.feature.onboarding.PrimaryCta
import io.github.ukpratik.mukhar.app.feature.onboarding.TextCta
import io.github.ukpratik.mukhar.app.feature.onboarding.WeeklyGoalPicker
import io.github.ukpratik.mukhar.app.feature.onboarding.currentLocale
import io.github.ukpratik.mukhar.app.feature.today.DayDot
import io.github.ukpratik.mukhar.app.feature.today.StreakCardUi
import io.github.ukpratik.mukhar.data.repo.HabitSnapshot
import io.github.ukpratik.mukhar.data.repo.SessionKind
import io.github.ukpratik.mukhar.domain.streak.StreakEngine
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import java.time.format.TextStyle
import java.util.Locale

enum class SheetMode { DEFAULT, REPAIR, FRESH }

fun HabitSnapshot.sheetMode(): SheetMode = when {
    repairDay != null -> SheetMode.REPAIR
    freshStart -> SheetMode.FRESH
    else -> SheetMode.DEFAULT
}

/** S20 as a bottom sheet over Today. Closing it acknowledges the one-time freeze or fresh-start message. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakSheet(
    habit: HabitSnapshot,
    card: StreakCardUi,
    talksEnabled: Boolean,
    wordsLearned: Int,
    onGoal: (Int) -> Unit,
    onTalk: () -> Unit,
    onRevise: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        StreakSheetContent(habit, card, talksEnabled, wordsLearned, onGoal, onTalk, onRevise, onDismiss)
    }
}

@Composable
fun StreakSheetContent(
    habit: HabitSnapshot,
    card: StreakCardUi,
    talksEnabled: Boolean,
    wordsLearned: Int,
    onGoal: (Int) -> Unit,
    onTalk: () -> Unit,
    onRevise: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = MukharSpace.gutter, end = MukharSpace.gutter, bottom = 26.dp).testTag(TAG_STREAK_SHEET),
    ) {
        val act: () -> Unit = if (talksEnabled) onTalk else onRevise
        when (habit.sheetMode()) {
            SheetMode.REPAIR -> RepairContent(habit, talksEnabled, act, onDismiss)
            SheetMode.FRESH -> FreshContent(habit, talksEnabled, wordsLearned, onGoal, act)
            SheetMode.DEFAULT -> DefaultContent(habit, card, talksEnabled, onGoal, act)
        }
    }
}

@Composable
private fun DefaultContent(habit: HabitSnapshot, card: StreakCardUi, talksEnabled: Boolean, onGoal: (Int) -> Unit, act: () -> Unit) {
    val notice = habit.freezeNoticeDay
    if (notice != null) {
        val title = if (notice == habit.today.minusDays(1)) {
            stringResource(R.string.sheet_freeze_title)
        } else {
            stringResource(R.string.sheet_freeze_title_day, notice.dayOfWeek.getDisplayName(TextStyle.FULL, currentLocale()))
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = MukharSpace.m).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 14.dp, vertical = MukharSpace.m)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.AcUnit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.sheet_freeze_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MukharSpace.l)) {
        StreakRing(card.covered, card.goal, card.met, describe = true)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { heading() }) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MukharTheme.colors.accent, modifier = Modifier.size(22.dp))
                Text(streakTitle(card), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                if (habit.frozenThisWeek.isNotEmpty()) {
                    stringResource(R.string.sheet_frozen_note)
                } else {
                    pluralStringResource(R.plurals.sheet_best, habit.streak.best, habit.streak.best)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val weekA11y = weekDescription(card.dots)
            WeekDots(card.dots, Modifier.semantics { contentDescription = weekA11y })
        }
    }
    HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.sheet_freezes),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            val held = habit.streak.freezesHeld
            Text(
                habit.nextFreezeInDays?.let { pluralStringResource(R.plurals.sheet_next_freeze, it, held, it) }
                    ?: stringResource(R.string.sheet_freezes_held, held),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(StreakEngine.MAX_FREEZES) { i -> FreezeSlot(held = i < habit.streak.freezesHeld) }
        }
    }
    GoalBlock(
        title = if (notice != null && !card.met) stringResource(R.string.sheet_weekly_goal_n, card.covered, card.goal) else stringResource(R.string.sheet_weekly_goal),
        note = when {
            card.met -> stringResource(R.string.sheet_goal_met_note)
            notice != null -> (card.goal - card.covered).let { pluralStringResource(R.plurals.sheet_days_more, it, it) }
            else -> stringResource(R.string.sheet_goal_note)
        },
        goal = habit.habit.weeklyGoal,
        onGoal = onGoal,
    )
    if (notice == null) InfoNote(Icons.Outlined.Info, stringResource(R.string.sheet_rule), Modifier.padding(top = MukharSpace.xs))
    if (!habit.coveredToday) {
        PrimaryCta(
            stringResource(if (talksEnabled) R.string.sheet_do_talk else R.string.sheet_do_revise),
            act,
            leading = if (talksEnabled) Icons.Rounded.Mic else null,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun FreezeSlot(held: Boolean) {
    if (held) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                .border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AcUnit, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
    } else {
        val line = MukharTheme.colors.lineStrong
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(34.dp)) {
                val w = 2.dp.toPx()
                drawCircle(line, radius = size.minDimension / 2 - w / 2, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            }
            Icon(Icons.Outlined.AcUnit, contentDescription = null, tint = MukharTheme.colors.faint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun GoalBlock(title: String, note: String, goal: Int, onGoal: (Int) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.padding(top = 10.dp, bottom = MukharSpace.m)) {
        Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = MukharSpace.s))
        WeeklyGoalPicker(goal, onGoal, withSubLabels = false)
    }
}

@Composable
private fun RepairContent(habit: HabitSnapshot, talksEnabled: Boolean, act: () -> Unit, onDismiss: () -> Unit) {
    val missed = habit.repairDay ?: return
    val tonight = habit.today >= missed.plusDays(StreakEngine.REPAIR_WINDOW_DAYS)
    Row(
        Modifier.clip(CircleShape).background(MukharTheme.colors.accentTint).padding(horizontal = 10.dp, vertical = MukharSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MukharSpace.xs),
    ) {
        Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = MukharTheme.colors.accentInk, modifier = Modifier.size(16.dp))
        Text(
            stringResource(if (tonight) R.string.sheet_repair_tonight else R.string.sheet_repair_tomorrow),
            style = MaterialTheme.typography.labelMedium,
            color = MukharTheme.colors.accentInk,
        )
    }
    Text(
        stringResource(R.string.sheet_repair_title, habit.repairableStreak),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp).semantics { heading() },
    )
    Text(stringResource(R.string.sheet_repair_lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MukharSpace.xs, bottom = MukharSpace.m))
    Column(Modifier.fillMaxWidth().padding(top = MukharSpace.s, bottom = MukharSpace.m).clip(io.github.ukpratik.mukhar.ui.theme.TileShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)) {
        Eyebrow(stringResource(R.string.sheet_repair_progress, habit.repairSessionsDone))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
            val done = habit.sessionsToday.take(StreakEngine.REPAIR_SESSIONS)
            for (i in 0 until StreakEngine.REPAIR_SESSIONS) {
                val kind = done.getOrNull(i)
                val label = when (kind ?: if (talksEnabled) SessionKind.TALK else SessionKind.REVIEW) {
                    SessionKind.TALK -> stringResource(R.string.sheet_repair_talk)
                    SessionKind.REVIEW -> stringResource(R.string.sheet_repair_review)
                }
                RepairStep(label, done = kind != null, modifier = Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.sheet_repair_rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(verticalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
        PrimaryCta(
            when {
                !talksEnabled -> stringResource(R.string.sheet_do_revise)
                habit.repairSessionsDone >= 1 -> stringResource(R.string.sheet_repair_finish_talk)
                else -> stringResource(R.string.sheet_repair_start_talk)
            },
            act,
            leading = if (talksEnabled) Icons.Rounded.Mic else null,
        )
        TextCta(stringResource(R.string.sheet_not_today), onDismiss)
    }
}

@Composable
private fun RepairStep(label: String, done: Boolean, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    val base = modifier.heightIn(min = MukharSpace.touch).clip(shape)
    val styled = if (done) {
        base.background(MaterialTheme.colorScheme.primaryContainer).border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        base.background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
    }
    // Done / to do is said in words too, not only by the icon and colour (design §10.3).
    val state = stringResource(if (done) R.string.repair_step_done else R.string.repair_step_todo)
    Row(
        styled.semantics(mergeDescendants = true) { stateDescription = state }.testTag(TAG_REPAIR_STEP).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FreshContent(habit: HabitSnapshot, talksEnabled: Boolean, wordsLearned: Int, onGoal: (Int) -> Unit, act: () -> Unit) {
    Box(Modifier.padding(bottom = 10.dp).size(64.dp).clip(CircleShape).background(MukharTheme.colors.accentTint), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.WbTwilight, contentDescription = null, tint = MukharTheme.colors.accentInk, modifier = Modifier.size(34.dp))
    }
    Text(stringResource(R.string.sheet_fresh_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.sheet_fresh_lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MukharSpace.xs))
    val best = pluralStringResource(R.plurals.sheet_best_days, habit.streak.best, habit.streak.best)
    val bestLabel = stringResource(R.string.sheet_best_kept)
    val words = pluralStringResource(R.plurals.sheet_words, wordsLearned, wordsLearned)
    val wordsLabel = stringResource(R.string.sheet_words_kept)
    // One sentence with the values in it; the heading alone would hide what is kept.
    val keptA11y = stringResource(R.string.sheet_kept_list_a11y, stringResource(R.string.sheet_kept_a11y), best, bestLabel, words, wordsLabel)
    Row(
        Modifier.fillMaxWidth().padding(vertical = MukharSpace.m).semantics(mergeDescendants = true) { contentDescription = keptA11y }.testTag(TAG_KEPT),
        horizontalArrangement = Arrangement.spacedBy(MukharSpace.s),
    ) {
        KeptTile(best, bestLabel, Modifier.weight(1f))
        KeptTile(words, wordsLabel, Modifier.weight(1f))
    }
    GoalBlock(stringResource(R.string.sheet_weekly_goal), stringResource(R.string.sheet_fresh_goal_note), habit.habit.weeklyGoal, onGoal)
    PrimaryCta(
        stringResource(if (talksEnabled) R.string.sheet_start_day1 else R.string.sheet_do_revise),
        act,
        leading = if (talksEnabled) Icons.Rounded.Mic else null,
    )
}

@Composable
private fun KeptTile(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = MukharSpace.m, vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun streakTitle(card: StreakCardUi): String =
    if (card.useDayLabel) stringResource(R.string.streak_day_label, card.dayNumber) else stringResource(R.string.streak_n_day, card.streak)

/** "This week: Monday and Tuesday done, Wednesday frozen, today to do" for TalkBack. */
@Composable
private fun weekDescription(dots: List<DayDot>): String {
    val names = java.time.DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, currentLocale()) }
    val parts = dots.mapIndexedNotNull { i, d ->
        when (d) {
            DayDot.DONE, DayDot.TODAY_DONE -> stringResource(R.string.sheet_week_done, names[i])
            DayDot.FROZEN -> stringResource(R.string.sheet_week_frozen, names[i])
            DayDot.TODAY -> stringResource(R.string.sheet_week_today)
            DayDot.EMPTY -> null
        }
    }
    return stringResource(R.string.sheet_week_a11y, parts.joinToString(", "))
}

const val TAG_STREAK_SHEET = "streak_sheet"
const val TAG_REPAIR_STEP = "streak_repair_step"
const val TAG_KEPT = "streak_kept"
