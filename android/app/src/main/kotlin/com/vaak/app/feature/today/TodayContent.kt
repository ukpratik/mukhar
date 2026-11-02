package com.vaak.app.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaak.app.R
import com.vaak.app.feature.habit.StreakRing
import com.vaak.app.feature.habit.WeekDots
import com.vaak.app.feature.habit.streakTitle
import com.vaak.app.feature.onboarding.Eyebrow
import com.vaak.app.feature.onboarding.currentLocale
import com.vaak.app.feature.onboarding.label
import com.vaak.app.feature.onboarding.timeLabel
import com.vaak.app.feature.words.SpeakerSlot
import com.vaak.app.feature.words.hearDescription
import com.vaak.app.feature.words.posAndRegister
import com.vaak.data.prefs.ReminderSettings
import com.vaak.data.repo.WordItem
import com.vaak.data.repo.WordSource
import com.vaak.data.work.ReminderCopy
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Callbacks out of Today. Talk and Revise go to destinations M2 and M4 provide. */
data class TodayActions(
    val onStartTalk: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onOpenStreak: () -> Unit = {},
    val onOpenRevise: () -> Unit = {},
    val onOpenWords: () -> Unit = {},
    val onOpenMe: () -> Unit = {},
    val onTurnOnTalks: () -> Unit = {},
    val onRecordBaseline: () -> Unit = {},
    val onOpenWaiting: () -> Unit = {},
    val onOpenRecovered: (String) -> Unit = {},
    val onTipTap: () -> Unit = {},
    val onDismissTip: () -> Unit = {},
    val onOpenWord: (String) -> Unit = {},
    val onSpeak: (WordItem) -> Unit = {},
)

/** S07, stateless (state in, events out). Order is fixed by US-030. */
@Composable
fun TodayContent(state: TodayUiState, actions: TodayActions, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = VaakSpace.gutter).padding(top = VaakSpace.m, bottom = VaakSpace.xxl)
            .testTag(TAG_TODAY),
        verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        if (state.loading) return@Column
        Header(state, actions.onOpenMe)
        if (state.offline) OfflineBanner()
        state.waiting?.let { w ->
            CardRow(
                icon = Icons.Outlined.HourglassTop, badge = Badge.BRAND,
                title = pluralStringResource(R.plurals.talks_waiting, w.count, w.count),
                sub = stringResource(R.string.today_waiting_sub, clockTime(w.latestRecordedAt), mmss(w.latestDurationMs.toLong())),
                onClick = actions.onOpenWaiting,
            )
        }
        state.recovered?.let { r ->
            CardRow(
                icon = Icons.Outlined.Restore, badge = Badge.BRAND,
                title = stringResource(if (r.isBaseline) R.string.today_recovered_baseline_title else R.string.today_recovered_title),
                sub = stringResource(R.string.today_recovered_sub, mmss(r.durationMs.toLong()), clockTime(r.recordedAt)),
                onClick = { actions.onOpenRecovered(r.sessionId) },
            )
        }
        state.streak?.let { StreakCard(it, actions.onOpenStreak) }
        state.notificationTip?.let { NotificationTip(it, actions.onTipTap, actions.onDismissTip) }
        state.talk?.let { TalkHero(it, actions.onStartTalk, actions.onShuffle) }
        if (state.doneForToday) DoneCard(state)
        if (state.showBaseline && state.baselineFirst) BaselineCard(actions.onRecordBaseline)
        ReviseCard(state.revise, actions.onOpenRevise)
        if (!state.talksEnabled) {
            val a11y = stringResource(R.string.talks_off_a11y)
            CardRow(
                icon = Icons.Outlined.ToggleOn, badge = Badge.BRAND,
                title = stringResource(R.string.talks_off_title),
                sub = stringResource(R.string.talks_off_body),
                onClick = actions.onTurnOnTalks,
                a11y = a11y,
            )
        }
        WordsSection(state, actions)
        if (state.showBaseline && !state.baselineFirst) BaselineCard(actions.onRecordBaseline)
    }
}

@Composable
private fun Header(state: TodayUiState, onOpenMe: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = VaakSpace.xs), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val hello = stringResource(
                when (state.greeting) {
                    Greeting.MORNING -> R.string.greet_morning
                    Greeting.AFTERNOON -> R.string.greet_afternoon
                    Greeting.EVENING -> R.string.greet_evening
                },
            )
            Text(
                state.firstName?.let { stringResource(R.string.greet_with_name, hello, it) } ?: hello,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).semantics { heading() },
            )
        }
        val label = stringResource(R.string.today_profile_a11y)
        Box(
            Modifier.size(VaakSpace.touch).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(role = Role.Button, onClick = onOpenMe).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            val initial = state.firstName?.firstOrNull()?.uppercaseChar()
            if (initial != null) {
                Text(initial.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = VaakSpace.m)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(stringResource(R.string.today_offline_title), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.today_offline_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun StreakCard(card: StreakCardUi, onClick: () -> Unit) {
    val title = streakTitle(card)
    val line = stringResource(
        when (card.line) {
            StreakLine.WARMUP -> R.string.streak_line_warmup
            StreakLine.MET -> R.string.streak_line_met
            StreakLine.COUNTED -> R.string.streak_line_counted
            StreakLine.COUNTED_QUEUED -> R.string.streak_line_counted_queued
            StreakLine.TALKS_OFF -> R.string.streak_line_talks_off
            StreakLine.GOAL_TODAY -> R.string.streak_line_goal_today
            StreakLine.KEEP_GOING -> R.string.streak_line_keep_going
            StreakLine.FRESH -> R.string.streak_line_fresh
        },
    )
    val notCovered = card.line in setOf(StreakLine.TALKS_OFF, StreakLine.GOAL_TODAY, StreakLine.KEEP_GOING)
    val freezes = if (notCovered && card.freezes > 0) " " + pluralStringResource(R.plurals.freezes_saved, card.freezes, card.freezes) else ""
    val sub = line + freezes
    val a11y = if (card.met) {
        pluralStringResource(R.plurals.streak_card_met_a11y, card.goal, title, sub, card.covered, card.goal)
    } else {
        pluralStringResource(R.plurals.streak_card_a11y, card.goal, title, sub, card.covered, card.goal)
    }
    VaakCard(Modifier.fillMaxWidth().testTag(TAG_STREAK_CARD).clearAndSetSemantics { contentDescription = a11y; role = Role.Button }, onClick = onClick) {
        Row(Modifier.padding(VaakSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaakSpace.l)) {
            StreakRing(card.covered, card.goal, card.met)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = VaakTheme.colors.accent, modifier = Modifier.size(22.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                if (card.line != StreakLine.COUNTED_QUEUED) WeekDots(card.dots)
            }
        }
    }
}

@Composable
private fun NotificationTip(reminder: ReminderSettings, onTap: () -> Unit, onDismiss: () -> Unit) {
    val res = androidx.compose.ui.platform.LocalResources.current
    val phrase = ReminderCopy.phrase(res, reminder).replaceFirstChar { it.lowercase() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = VaakSpace.touch).clickable(role = Role.Button, onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Text(stringResource(R.string.today_tip_nudge, phrase), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.today_tip_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** The one saturated block on Today (design §6 hero card). Whole card = one target; shuffle is separate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalkHero(talk: TalkCardUi, onStart: () -> Unit, onShuffle: () -> Unit) {
    val colors = VaakTheme.colors
    val eyebrow = stringResource(
        when (talk.eyebrow) {
            TalkEyebrow.FIRST -> R.string.talk_eyebrow_first
            TalkEyebrow.TODAY -> R.string.talk_eyebrow_today
            TalkEyebrow.AGAIN -> R.string.talk_eyebrow_again
        },
    )
    val lang = stringResource(if (talk.prompt.lang == Lang.HI) R.string.lang_hi else R.string.lang_en)
    val track = stringResource(talk.prompt.track.label())
    val a11y = stringResource(R.string.talk_card_a11y, eyebrow, lang, track, talk.prompt.text)
    val accent = colors.accent
    Card(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = a11y }.testTag(TAG_TALK_CARD),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.hero, contentColor = colors.onHero),
        border = if (isSystemInDarkTheme()) BorderStroke(1.dp, colors.heroOutlineDark) else null,
    ) {
        Box(
            Modifier.fillMaxWidth().drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width - 40.dp.toPx(), 30.dp.toPx()), radius = 90.dp.toPx()),
                    radius = 90.dp.toPx(),
                    center = Offset(size.width - 40.dp.toPx(), 30.dp.toPx()),
                )
            },
        ) {
            Column(Modifier.padding(VaakSpace.xl).clearAndSetSemantics {}) {
                Eyebrow(eyebrow, color = colors.onHero2)
                FlowRow(Modifier.padding(top = 10.dp, bottom = VaakSpace.m), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeroChip(lang)
                    HeroChip(track)
                    if (talk.easy) HeroChip(stringResource(R.string.talk_easy))
                }
                Text(
                    talk.prompt.text,
                    style = if (talk.prompt.lang == Lang.HI) VaakTheme.type.hiPrompt else VaakTheme.type.prompt.copy(fontSize = 21.sp, lineHeight = 27.sp),
                    color = colors.onHero,
                )
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (talk.analysedLater) Icons.Outlined.CloudOff else Icons.Outlined.Schedule, contentDescription = null, tint = colors.onHero2, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(
                                when {
                                    talk.analysedLater -> R.string.talk_analysed_later
                                    talk.eyebrow == TalkEyebrow.FIRST -> R.string.talk_minutes_first
                                    else -> R.string.talk_minutes
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onHero2,
                        )
                    }
                    Row(
                        Modifier.heightIn(min = VaakSpace.touch).clip(CircleShape).background(colors.accent).padding(start = 14.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VaakSpace.s),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
                        Text(stringResource(R.string.talk_start), style = MaterialTheme.typography.labelLarge, color = colors.onAccent)
                    }
                }
            }
            IconButton(
                onClick = onShuffle,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
            ) {
                Icon(Icons.Outlined.Shuffle, contentDescription = stringResource(R.string.talk_shuffle), tint = colors.onHero)
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = VaakTheme.colors.onHero,
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun DoneCard(state: TodayUiState) {
    VaakCard(Modifier.fillMaxWidth().testTag(TAG_DONE_CARD)) {
        Column(Modifier.padding(VaakSpace.xl)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(30.dp))
            }
            Text(
                stringResource(R.string.today_done_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp).semantics { heading() },
            )
            Text(stringResource(R.string.today_done_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = VaakSpace.xs))
            state.tomorrowPrompt?.let { p ->
                HorizontalDivider(Modifier.padding(top = 14.dp), color = VaakTheme.colors.lineStrong)
                Row(Modifier.padding(top = VaakSpace.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Eyebrow(stringResource(R.string.today_tomorrow))
                }
                // Curiosity: only the first ~40 characters, fading out (US-030).
                val ink = MaterialTheme.colorScheme.onSurface
                Text(
                    p.text.take(TEASE_CHARS) + "…",
                    style = (if (p.lang == Lang.HI) VaakTheme.type.hiPrompt else VaakTheme.type.prompt).merge(
                        TextStyle(fontSize = 17.sp, brush = Brush.horizontalGradient(0.4f to ink, 1f to Color.Transparent)),
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(top = VaakSpace.xs),
                )
            }
        }
    }
}

private enum class Badge { ACCENT, BRAND, GLOW }

@Composable
private fun CardRow(
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    number: Int? = null,
    badge: Badge = Badge.ACCENT,
    onClick: (() -> Unit)? = null,
    a11y: String? = null,
) {
    val desc = a11y ?: "$title. $sub"
    VaakCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }, onClick = onClick) {
        Row(Modifier.padding(VaakSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val (bg, fg) = when (badge) {
                Badge.ACCENT -> VaakTheme.colors.accentTint to VaakTheme.colors.accentInk
                Badge.BRAND -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                Badge.GLOW -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
            }
            Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(bg), contentAlignment = Alignment.Center) {
                if (number != null) {
                    Text(number.toString(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = fg)
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReviseCard(r: ReviseUi, onOpen: () -> Unit) {
    when (r.kind) {
        ReviseKind.DUE -> CardRow(
            title = pluralStringResource(R.plurals.revise_due, r.due, r.due),
            sub = stringResource(
                when {
                    r.offline -> R.string.revise_minutes_offline
                    r.talksOff -> R.string.revise_minutes_on_phone
                    else -> R.string.revise_minutes
                },
                r.minutes,
            ),
            number = r.due,
            onClick = onOpen,
            modifier = Modifier.testTag(TAG_REVISE),
        )
        ReviseKind.CAUGHT_UP -> CardRow(
            title = stringResource(R.string.revise_caught_up),
            sub = pluralStringResource(R.plurals.revise_due_tomorrow, r.dueTomorrow, r.dueTomorrow),
            icon = Icons.Outlined.DoneAll,
            badge = Badge.GLOW,
            modifier = Modifier.testTag(TAG_REVISE),
        )
        ReviseKind.NOTHING -> CardRow(
            title = stringResource(R.string.revise_title),
            sub = stringResource(R.string.revise_nothing),
            icon = Icons.Outlined.AutoStories,
            badge = Badge.GLOW,
            modifier = Modifier.testTag(TAG_REVISE),
        )
    }
}

@Composable
private fun BaselineCard(onClick: () -> Unit) {
    CardRow(
        title = stringResource(R.string.today_baseline_title),
        sub = stringResource(R.string.today_baseline_body),
        icon = Icons.Outlined.Flag,
        badge = Badge.BRAND,
        onClick = onClick,
        a11y = stringResource(R.string.today_baseline_a11y),
    )
}

/** Today's words (US-020): meaning, example, register and source, with the speaker when a voice is installed. */
@Composable
private fun WordsSection(state: TodayUiState, actions: TodayActions) {
    Row(Modifier.fillMaxWidth().padding(top = VaakSpace.s, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.today_words), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).semantics { heading() })
        Box(
            Modifier.heightIn(min = VaakSpace.touch).clickable(role = Role.Button, onClick = actions.onOpenWords).padding(horizontal = VaakSpace.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.today_word_bank), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
    VaakCard(Modifier.fillMaxWidth().testTag(TAG_WORDS)) {
        if (state.words.isEmpty()) {
            Text(
                stringResource(R.string.today_words_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(VaakSpace.l),
            )
        }
        state.words.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val meta = posAndRegister(w)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { actions.onOpenWord(w.id) }
                    .padding(start = VaakSpace.l, end = VaakSpace.s, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            w.lemma,
                            style = if (w.lang == Lang.HI) VaakTheme.type.hiBodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${if (w.lang == Lang.HI) w.gloss ?: w.meaning else w.meaning} “${w.example}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                    )
                    if (w.source == WordSource.TALK) {
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.accentInk, modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.today_word_from_talk), style = MaterialTheme.typography.labelSmall, color = VaakTheme.colors.accentInk, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                SpeakerSlot(state.voices[w.lang] == true, hearDescription(w.lemma, w.lang), { actions.onSpeak(w) })
            }
        }
    }
}

private fun mmss(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

@Composable
private fun clockTime(at: java.time.Instant): String = timeLabel(at.atZone(ZoneId.systemDefault()).toLocalTime())

private const val TEASE_CHARS = 40
const val TAG_TODAY = "today"
const val TAG_STREAK_CARD = "today_streak"
const val TAG_TALK_CARD = "today_talk"
const val TAG_DONE_CARD = "today_done"
const val TAG_REVISE = "today_revise"
const val TAG_WORDS = "today_words"
