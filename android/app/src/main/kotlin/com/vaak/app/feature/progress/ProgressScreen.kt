package com.vaak.app.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.onboarding.PrimaryCta
import com.vaak.app.feature.talk.common.ArtCircle
import com.vaak.app.feature.talk.common.InfoNote
import com.vaak.app.feature.talk.common.SectionHead
import com.vaak.app.feature.talk.common.clock
import com.vaak.app.feature.talk.common.langLabel
import com.vaak.domain.billing.SubscriptionStatus
import com.vaak.domain.model.Lang
import com.vaak.domain.progress.Direction
import com.vaak.domain.progress.Trend
import com.vaak.domain.progress.TrendRange
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProgressActions(
    val onSettings: () -> Unit = {},
    val onPlan: () -> Unit = {},
    val onTalk: () -> Unit = {},
    val onBaseline: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onRange: (TrendRange) -> Unit = {},
    val onLang: (Lang) -> Unit = {},
    val onDismissInsight: (String) -> Unit = {},
)

/** The Me tab opens on Progress (S16). */
@Composable
fun ProgressScreen(
    onSettings: () -> Unit,
    onPlan: () -> Unit,
    onTalk: () -> Unit,
    onBaseline: () -> Unit,
    onShare: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = rememberTalkPlayer()
    ProgressContent(
        state = state,
        actions = ProgressActions(
            onSettings = onSettings,
            onPlan = onPlan,
            onTalk = onTalk,
            onBaseline = onBaseline,
            onShare = onShare,
            onRange = viewModel::onRange,
            onLang = viewModel::onLang,
            onDismissInsight = viewModel::onDismissInsight,
        ),
        player = player,
    )
}

/** S16, stateless. Order: header, insight, totals, trends (or the empty state), then and now, plan. */
@Composable
fun ProgressContent(state: ProgressUiState, actions: ProgressActions, modifier: Modifier = Modifier, player: TalkPlayer? = null) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = VaakSpace.gutter).padding(top = VaakSpace.s, bottom = VaakSpace.xxl)
            .testTag(TAG_PROGRESS),
        verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        Header(actions.onSettings)
        if (state.loading) return@Column
        state.insight?.let { InsightCard(it, state.canShare, actions) }
        TotalsRow(state)
        if (state.showTrends) {
            if (state.langs.size > 1) LangSwitch(state.lang, state.langs, actions.onLang)
            RangeSwitch(state.range, actions.onRange)
            state.trends?.let { TrendGrid(state, it) }
        } else {
            EmptyTrends(actions.onTalk)
        }
        if (state.showBaselineCta) BaselineRow(actions.onBaseline)
        state.compare?.let { Compare(it, state.canShare, actions.onShare, player) }
        PlanRow(state.plan, actions.onPlan)
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.progress_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onSettings, modifier = Modifier.testTag(TAG_SETTINGS)) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.progress_settings_a11y))
        }
    }
}

@Composable
private fun TotalsRow(state: ProgressUiState) {
    val t = state.totals
    val desc = stringResource(R.string.progress_totals_a11y)
    // 3-up; one column at large text (design-system §10.5: tiles reflow).
    Reflow(Modifier.fillMaxWidth().testTag(TAG_TOTALS).semantics { contentDescription = desc; isTraversalGroup = true }, VaakSpace.s) {
        tile { TotalTile(t.talks.toString(), pluralStringResource(R.plurals.progress_total_talks, t.talks), it) }
        tile { TotalTile(t.minutesSpoken.toString(), stringResource(R.string.progress_total_minutes), it) }
        tile { TotalTile(t.wordsLearned.toString(), stringResource(R.string.progress_total_words), it) }
    }
}

/** True above 130 % font size: multi-column rows stack. */
@Composable
private fun largeFont(): Boolean = LocalDensity.current.fontScale > LARGE_FONT_SCALE

private class ReflowScope {
    val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
    fun tile(content: @Composable (Modifier) -> Unit) {
        tiles += content
    }
}

/** Tiles side by side with equal widths, or one per line at large text. */
@Composable
private fun Reflow(modifier: Modifier, spacing: androidx.compose.ui.unit.Dp, content: ReflowScope.() -> Unit) {
    val tiles = ReflowScope().apply(content).tiles
    if (largeFont()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) { tiles.forEach { it(Modifier.weight(1f)) } }
    }
}

@Composable
private fun TotalTile(value: String, label: String, modifier: Modifier) {
    VaakCard(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = VaakTheme.type.metric.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyTrends(onTalk: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = VaakSpace.l).testTag(TAG_EMPTY), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtCircle(Icons.Outlined.ShowChart)
        Text(
            stringResource(R.string.progress_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = VaakSpace.l).semantics { heading() },
        )
        Text(
            stringResource(R.string.progress_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = VaakSpace.m),
        )
        PrimaryCta(stringResource(R.string.progress_empty_cta), onTalk, leading = Icons.Rounded.Mic)
    }
}

@Composable
private fun RangeSwitch(range: TrendRange, onRange: (TrendRange) -> Unit) {
    val desc = stringResource(R.string.progress_range_a11y)
    val labels = mapOf(TrendRange.FOUR to R.string.progress_range_4, TrendRange.TWELVE to R.string.progress_range_12, TrendRange.ALL to R.string.progress_range_all)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = desc; isTraversalGroup = true }) {
        TrendRange.entries.forEachIndexed { i, r ->
            SegmentedButton(
                selected = r == range,
                onClick = { onRange(r) },
                shape = SegmentedButtonDefaults.itemShape(i, TrendRange.entries.size),
            ) { Text(stringResource(labels.getValue(r))) }
        }
    }
}

@Composable
private fun LangSwitch(lang: Lang, langs: List<Lang>, onLang: (Lang) -> Unit) {
    val desc = stringResource(R.string.progress_lang_a11y)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = desc; isTraversalGroup = true }) {
        langs.forEachIndexed { i, l ->
            SegmentedButton(selected = l == lang, onClick = { onLang(l) }, shape = SegmentedButtonDefaults.itemShape(i, langs.size)) {
                Text(stringResource(langLabel(l)))
            }
        }
    }
}

@Composable
private fun TrendGrid(state: ProgressUiState, trends: com.vaak.domain.progress.Trends) {
    val hi = state.lang == Lang.HI
    Column(Modifier.fillMaxWidth().testTag(TAG_TRENDS), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Reflow(Modifier.fillMaxWidth(), 10.dp) {
            tile {
            MetricTrend(
                label = stringResource(R.string.progress_pace),
                trend = trends.pace,
                value = trends.pace.current?.let { pct(it) },
                unit = stringResource(R.string.progress_pace_unit),
                format = { stringResource(R.string.percent, pct(it)) },
                fixedMin = 0.0,
                fixedMax = 100.0,
                modifier = it.testTag(TAG_TREND_PACE),
            )
            }
            tile {
            MetricTrend(
                label = stringResource(R.string.progress_fillers),
                trend = trends.fillers,
                value = trends.fillers.current?.let(::oneDecimal),
                unit = stringResource(R.string.progress_per_min),
                format = { oneDecimal(it) },
                lowerIsBetter = true,
                modifier = it.testTag(TAG_TREND_FILLERS),
            )
            }
        }
        Reflow(Modifier.fillMaxWidth(), 10.dp) {
            tile {
            MetricTrend(
                label = stringResource(if (hi) R.string.progress_variety_hi else R.string.progress_variety),
                trend = trends.variety,
                value = trends.variety.current?.let(::twoDecimals),
                unit = null,
                format = { twoDecimals(it) },
                emptyText = stringResource(R.string.progress_variety_locked),
                modifier = it.testTag(TAG_TREND_VARIETY),
            )
            }
            tile { TalksTile(trends, it.testTag(TAG_TREND_TALKS)) }
        }
    }
}

@Composable
private fun MetricTrend(
    label: String,
    trend: Trend,
    value: String?,
    unit: String?,
    format: @Composable (Double) -> String,
    modifier: Modifier = Modifier,
    lowerIsBetter: Boolean = false,
    emptyText: String? = null,
    fixedMin: Double? = null,
    fixedMax: Double? = null,
) {
    val first = trend.first?.let { format(it) }
    val deltaText = when {
        value == null -> emptyText ?: stringResource(R.string.progress_no_data)
        trend.direction == Direction.STEADY -> stringResource(R.string.progress_steady)
        trend.direction == Direction.UP -> stringResource(R.string.progress_up_from, first.orEmpty())
        else -> stringResource(R.string.progress_down_from, first.orEmpty())
    }.let { if (lowerIsBetter && value != null && trend.direction != Direction.STEADY) stringResource(R.string.progress_lower_better, it) else it }
    val valueText = listOfNotNull(value, unit).joinToString(" ")
    val desc = stringResource(R.string.progress_trend_a11y, label, valueText.ifEmpty { "–" }, deltaText)
    VaakCard(modifier.clearAndSetSemantics { contentDescription = desc }) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(value ?: "–", style = VaakTheme.type.metric.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface)
                if (unit != null && value != null) {
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                }
            }
            Sparkline(trend.points, Modifier.padding(vertical = 6.dp), fixedMin, fixedMax)
            DeltaLine(
                icon = when {
                    value == null || trend.direction == Direction.STEADY -> Icons.Rounded.HorizontalRule
                    trend.direction == Direction.UP -> Icons.Rounded.ArrowUpward
                    else -> Icons.Rounded.ArrowDownward
                },
                text = deltaText,
                good = trend.improved,
            )
        }
    }
}

@Composable
private fun TalksTile(trends: com.vaak.domain.progress.Trends, modifier: Modifier) {
    val t = trends.talks
    val label = stringResource(R.string.progress_talks_week)
    val goal = stringResource(R.string.progress_goal_unit, t.goal)
    val met = pluralStringResource(R.plurals.progress_goal_met, t.weeks, t.metWeeks, t.weeks)
    val desc = stringResource(R.string.progress_trend_a11y, label, "${t.thisWeek}, $goal", met)
    VaakCard(modifier.clearAndSetSemantics { contentDescription = desc }) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(t.thisWeek.toString(), style = VaakTheme.type.metric.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(goal, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
            }
            WeekBars(t.counts, t.goalMet, t.goal, Modifier.padding(vertical = 6.dp))
            DeltaLine(Icons.Rounded.CheckCircle, met, good = t.metWeeks > 0)
        }
    }
}

@Composable
private fun DeltaLine(icon: ImageVector, text: String, good: Boolean) {
    val color = if (good) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun InsightCard(insight: InsightUi, canShare: Boolean, actions: ProgressActions) {
    val (title, body) = insightText(insight)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().testTag(TAG_INSIGHT).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = VaakSpace.l, end = 4.dp, top = 4.dp, bottom = if (canShare) 4.dp else VaakSpace.l)) {
            // The pill and the close button share a row, so close never overlaps the text and is read in order.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(top = VaakSpace.l - 4.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.tertiary) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.progress_insight_tag), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                IconButton(onClick = { actions.onDismissInsight(insight.key) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.progress_insight_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.padding(end = VaakSpace.l - 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp).semantics { heading() })
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                if (canShare) {
                    TextButton(onClick = actions.onShare, modifier = Modifier.padding(start = 0.dp)) {
                        Icon(Icons.Outlined.IosShare, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.progress_share_cta), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun insightText(i: InsightUi): Pair<String, String> = when (i.kind) {
    InsightKind.FILLERS_DOWN -> stringResource(R.string.insight_fillers_title, i.percent) to
        stringResource(R.string.insight_fillers_body, oneDecimal(i.last), oneDecimal(i.first))
    InsightKind.TALKS -> pluralStringResource(R.plurals.insight_talks_title, i.count, i.count) to
        pluralStringResource(R.plurals.insight_talks_body, i.count, i.count)
    InsightKind.FIRST_100_TOKENS -> stringResource(R.string.insight_tokens_title) to stringResource(R.string.insight_tokens_body)
    InsightKind.STREAK -> stringResource(R.string.insight_streak_title, i.count) to pluralStringResource(R.plurals.insight_streak_body, i.count, i.count)
    InsightKind.WORDS -> pluralStringResource(R.plurals.insight_words_title, i.count, i.count) to
        pluralStringResource(R.plurals.insight_words_body, i.count, i.count)
}

@Composable
private fun BaselineRow(onClick: () -> Unit) {
    NavRow(
        icon = Icons.Outlined.Flag,
        title = stringResource(R.string.progress_baseline_title),
        sub = stringResource(R.string.progress_baseline_sub),
        onClick = onClick,
    )
}

@Composable
private fun Compare(c: CompareUi, canShare: Boolean, onShare: () -> Unit, player: TalkPlayer?) {
    Row(Modifier.fillMaxWidth().padding(top = VaakSpace.s), verticalAlignment = Alignment.Bottom) {
        SectionHead(stringResource(R.string.progress_then_now), null, Modifier.weight(1f).padding(top = 0.dp))
        if (canShare) {
            TextButton(onClick = onShare, modifier = Modifier.testTag(TAG_SHARE_LINK)) { Text(stringResource(R.string.progress_share)) }
        }
    }
    val before = shortDate(c.baselineDate)
    val after = shortDate(c.latestDate)
    val desc = stringResource(R.string.compare_a11y, before, after)
    val large = largeFont()
    VaakCard(Modifier.fillMaxWidth().testTag(TAG_COMPARE).semantics { contentDescription = desc }) {
        // The column heads are visual only: each row reads as one sentence with both values.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp).clearAndSetSemantics {}) {
            // At large text the metric label gets its own line, so the two value columns take the full width.
            if (!large) Spacer(Modifier.weight(1f))
            ColHead(stringResource(R.string.progress_baseline_col), before, Modifier.weight(1f))
            ColHead(stringResource(R.string.progress_latest_col), after, Modifier.weight(1f))
        }
        c.rows.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompareRow(row, large)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Baseline: its player, or a dashed "Audio not kept" note. Latest: only if that talk was saved (S16 notes).
        // Side by side, or stacked at large text.
        val chips = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp)
        if (large) {
            Column(chips, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerChip(c.baselineAudio, c.baselineDurationMs, then = true, player = player, modifier = Modifier.fillMaxWidth())
                if (c.latestAudio != null) PlayerChip(c.latestAudio, c.latestDurationMs, then = false, player = player, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(chips, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerChip(c.baselineAudio, c.baselineDurationMs, then = true, player = player, modifier = Modifier.weight(1f))
                if (c.latestAudio != null) {
                    PlayerChip(c.latestAudio, c.latestDurationMs, then = false, player = player, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    InfoNote(
        Icons.Outlined.Lock,
        stringResource(
            when {
                c.baselineAudio != null && c.latestAudio != null -> R.string.progress_audio_note_both
                c.baselineAudio != null -> R.string.progress_audio_note_baseline
                else -> R.string.progress_audio_note_none
            },
        ),
    )
}

@Composable
private fun ColHead(title: String, date: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(title.uppercase(com.vaak.app.feature.onboarding.currentLocale()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompareRow(row: CompareRowUi, large: Boolean) {
    val (label, unit) = when (row.kind) {
        CompareKind.PACE -> R.string.compare_pace to R.string.unit_wpm
        CompareKind.FILLERS -> R.string.compare_fillers to R.string.progress_per_min
        CompareKind.PAUSES -> R.string.compare_pauses to R.string.progress_per_min
        CompareKind.VARIETY -> R.string.compare_variety to null
    }
    val fmt: (Double) -> String = when (row.kind) {
        CompareKind.PACE -> { v -> v.toInt().toString() }
        CompareKind.VARIETY -> ::twoDecimals
        else -> ::oneDecimal
    }
    val delta = compareDelta(row)
    val name = stringResource(label)
    val unitText = unit?.let { stringResource(it) }
    fun withUnit(v: Double) = listOfNotNull(fmt(v), unitText).joinToString(" ")
    // "Pace: baseline 152 wpm, latest 138 wpm. Now in your comfortable band"
    val cd = stringResource(R.string.compare_row_cd, name, withUnit(row.before), withUnit(row.after), delta)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("compare_${row.kind.name.lowercase()}").clearAndSetSemantics { contentDescription = cd }) {
        if (large) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) { CompareValues(row, fmt, unitText) }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                CompareValues(row, fmt, unitText)
            }
        }
        val icon = when (row.delta) {
            CompareDelta.IntoBand, CompareDelta.StillInBand -> Icons.Rounded.CheckCircle
            is CompareDelta.Percent -> if (row.delta.down) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward
            is CompareDelta.Amount -> if (row.delta.down) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward
            CompareDelta.Same -> Icons.Rounded.HorizontalRule
        }
        Row(Modifier.padding(top = 2.dp)) { DeltaLine(icon, delta, row.delta.good) }
    }
}

@Composable
private fun RowScope.CompareValues(row: CompareRowUi, fmt: (Double) -> String, unit: String?) {
    ValueText(fmt(row.before), unit, strong = false, modifier = Modifier.weight(1f))
    ValueText(fmt(row.after), unit, strong = true, modifier = Modifier.weight(1f))
}

@Composable
private fun compareDelta(row: CompareRowUi): String = when (val d = row.delta) {
    CompareDelta.IntoBand -> stringResource(R.string.compare_into_band)
    CompareDelta.StillInBand -> stringResource(R.string.compare_in_band)
    is CompareDelta.Percent -> stringResource(if (d.down) R.string.compare_down_pct else R.string.compare_up_pct, d.percent)
    is CompareDelta.Amount -> {
        val amount = if (row.kind == CompareKind.VARIETY) twoDecimals(d.amount) else d.amount.toInt().toString()
        stringResource(if (d.down) R.string.compare_down_amount else R.string.compare_up_amount, amount)
    }
    CompareDelta.Same -> stringResource(R.string.compare_same)
}

@Composable
private fun ValueText(value: String, unit: String?, strong: Boolean, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = VaakTheme.type.metric.copy(fontSize = 17.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal),
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (unit != null) Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
    }
}

@Composable
private fun PlayerChip(file: File?, durationMs: Long, then: Boolean, player: TalkPlayer?, modifier: Modifier) {
    // Rounded rectangles, not circles, so a label that wraps at large text isn't clipped.
    val shape = RoundedCornerShape(24.dp)
    if (file == null) {
        Box(
            modifier.heightIn(min = VaakSpace.touch).clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(stringResource(R.string.player_not_kept), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val playing = player?.playing == file
    val label = stringResource(if (then) R.string.player_then else R.string.player_now, clock(if (playing) player.positionMs else durationMs))
    val action = stringResource(
        when {
            then && playing -> R.string.player_pause_baseline
            then -> R.string.player_play_baseline
            playing -> R.string.player_pause_latest
            else -> R.string.player_play_latest
        },
    )
    Surface(modifier.heightIn(min = VaakSpace.touch), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp, top = 2.dp, bottom = 2.dp)) {
            FilledTonalIconButton(onClick = { player?.toggle(file) }) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = action)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                val progress = if (playing && durationMs > 0) (player.positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(4.dp)),
                    trackColor = VaakTheme.colors.track,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun PlanRow(plan: PlanRowUi, onClick: () -> Unit) {
    val (title, sub) = planText(plan)
    NavRow(Icons.Outlined.WorkspacePremium, title, sub, onClick, Modifier.padding(top = VaakSpace.xs).testTag(TAG_PLAN_ROW))
}

/** Title and line for the plan row (S16 card, S19 row). */
@Composable
fun planText(plan: PlanRowUi): Pair<String, String> {
    val date = plan.expiry?.let(::shortDate).orEmpty()
    return when (plan.status) {
        SubscriptionStatus.FREE -> stringResource(R.string.plan_free_title) to stringResource(R.string.plan_free_sub)
        SubscriptionStatus.TRIAL -> stringResource(R.string.plan_trial_title) to stringResource(R.string.plan_trial_sub, date)
        SubscriptionStatus.TRIAL_CANCELLED, SubscriptionStatus.CANCELLED -> stringResource(R.string.plan_premium_title) to stringResource(R.string.plan_cancelled_sub, date)
        SubscriptionStatus.ACTIVE -> stringResource(R.string.plan_premium_title) to stringResource(R.string.plan_active_sub, date)
        SubscriptionStatus.EXPIRED -> stringResource(R.string.plan_expired_title) to stringResource(R.string.plan_expired_sub)
    }
}

/** A tappable card row: brand badge, title, line, chevron (`.card.row.tap`). */
@Composable
fun NavRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    VaakCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Row(Modifier.padding(VaakSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))

private fun pct(v: Double): String = Math.round(v).toString()
fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)
fun twoDecimals(v: Double): String = String.format(Locale.US, "%.2f", v)

private const val LARGE_FONT_SCALE = 1.3f

const val TAG_PROGRESS = "progress"
const val TAG_TOTALS = "progress_totals"
const val TAG_SETTINGS = "progress_settings"
const val TAG_EMPTY = "progress_empty"
const val TAG_TRENDS = "progress_trends"
const val TAG_TREND_PACE = "trend_pace"
const val TAG_TREND_FILLERS = "trend_fillers"
const val TAG_TREND_VARIETY = "trend_variety"
const val TAG_TREND_TALKS = "trend_talks"
const val TAG_INSIGHT = "progress_insight"
const val TAG_COMPARE = "progress_compare"
const val TAG_SHARE_LINK = "progress_share"
const val TAG_PLAN_ROW = "progress_plan"
