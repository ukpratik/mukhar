package io.github.ukpratik.mukhar.app.feature.talk.sayagain

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.talk.common.SectionHead
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharAppBar
import io.github.ukpratik.mukhar.app.feature.talk.common.clock
import io.github.ukpratik.mukhar.app.feature.talk.common.reducedMotion
import io.github.ukpratik.mukhar.app.feature.talk.common.rememberHaptics
import io.github.ukpratik.mukhar.app.feature.talk.report.CtaBar
import io.github.ukpratik.mukhar.app.feature.talk.report.GrowCard
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportActions
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportContent
import io.github.ukpratik.mukhar.domain.talk.SayItAgain
import io.github.ukpratik.mukhar.ui.theme.FrauncesText
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme

@Composable
fun SayAgainScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onReport: (String) -> Unit,
    onOnceMore: (SayAgainUiState) -> Unit,
    vm: SayAgainViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    LaunchedEffect(state.deltas?.celebrate) { if (state.deltas?.celebrate == true) haptics.confirm() }
    val report = state.retakeReport
    Box(Modifier.fillMaxSize()) {
        if (state.deltas == null && report != null) {
            // Still analysing (or waiting offline): the S10 states for the re-take.
            ReportContent(report, vm::onReportEvent, ReportActions(onBack = onBack, onSayAgain = {}, onRecordAgain = { onOnceMore(state) }, onSettings = onBack))
        } else {
            SayAgainContent(state, onBack, onDone, { onReport(vm.retakeId) }, { onOnceMore(state) }, vm::toggleWord)
        }
        // Stays in place while the screen swaps from analysing to the comparison, so the result is read out.
        HeroAnnouncer(state)
    }
}

@Composable
private fun HeroAnnouncer(state: SayAgainUiState) {
    val text = state.deltas?.let { stringResource(heroText(it).first) }
    Spacer(
        Modifier.fillMaxWidth().height(1.dp).testTag(TAG_SAY_AGAIN_ANNOUNCER).semantics {
            liveRegion = LiveRegionMode.Polite
            if (text != null) contentDescription = text
        },
    )
}

const val TAG_SAY_AGAIN_ANNOUNCER = "say_again_announcer"

private fun heroText(d: SayItAgain.Deltas): Pair<Int, Int> = when {
    d.fillersImproved && d.pausesImproved -> R.string.sa_title_both to R.string.sa_body_both
    d.fillersImproved -> R.string.sa_title_fillers to R.string.sa_body_fillers
    d.pausesImproved -> R.string.sa_title_pauses to R.string.sa_body_pauses
    d.paceImproved -> R.string.sa_title_pace to R.string.sa_body_pace
    else -> R.string.sa_title_same to R.string.sa_body_same
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SayAgainContent(
    state: SayAgainUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onReport: () -> Unit,
    onOnceMore: () -> Unit,
    onWord: (io.github.ukpratik.mukhar.domain.feedback.UpgradedWord) -> Unit,
) {
    BackHandler(onBack = onBack)
    val d = state.deltas ?: return
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MukharAppBar(stringResource(R.string.retake_title), stringResource(R.string.back_report_cd), onBack) },
        bottomBar = {
            CtaBar {
                Button(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Text(stringResource(R.string.done_for_today)) }
                when {
                    d.celebrate -> {
                        val cd = stringResource(R.string.report_button_cd)
                        OutlinedButton(onClick = onReport, modifier = Modifier.heightIn(min = 54.dp).semantics { contentDescription = cd }) { Text(stringResource(R.string.report_button)) }
                    }
                    state.premium -> {
                        val cd = stringResource(R.string.once_more_cd)
                        OutlinedButton(onClick = onOnceMore, modifier = Modifier.heightIn(min = 54.dp).semantics { contentDescription = cd }) { Text(stringResource(R.string.once_more)) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter).padding(bottom = 24.dp)) {
                Hero(state)
                Deltas(state)
                if (!d.celebrate && state.nextTime != null) {
                    Spacer(Modifier.height(16.dp))
                    GrowCard(state.nextTime, state.lang, onQuoteTap = {}, tagOverride = stringResource(R.string.for_next_time))
                }
                if (state.words.isNotEmpty()) {
                    SectionHead(stringResource(R.string.words_for_you), stringResource(R.string.tap_to_add))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (w in state.words) WordChip(w.word, state.collected(w)) { onWord(w) }
                    }
                }
                state.creditDay?.let { day ->
                    Row(Modifier.padding(top = 14.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = MukharTheme.colors.accent, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (d.celebrate) R.string.note_already_counted else R.string.note_counted_either, day),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (d.celebrate) Confetti()
        }
    }
}

@Composable
private fun Hero(state: SayAgainUiState) {
    val d = state.deltas ?: return
    val (title, body) = heroText(d)
    val scale = remember { Animatable(if (d.celebrate) 0.8f else 1f) }
    val still = reducedMotion()
    LaunchedEffect(d.celebrate) { if (d.celebrate && !still) scale.animateTo(1f, tween(400)) else scale.snapTo(1f) }
    Column(Modifier.padding(top = 10.dp, bottom = 6.dp).semantics(mergeDescendants = true) {}) {
        Box(
            Modifier.size(64.dp).scale(scale.value).clip(CircleShape)
                .background(if (d.celebrate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (d.celebrate) Icons.Rounded.WorkspacePremium else Icons.Rounded.Replay, null,
                tint = if (d.celebrate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            stringResource(R.string.retake_time, clock(state.retakeMs), clock(state.capMs)).uppercase(),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
        )
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Deltas(state: SayAgainUiState) {
    val cd = stringResource(R.string.deltas_cd)
    MukharCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // Heading navigation lands on the comparison; nothing is drawn and the card adds no extra stop.
            Spacer(Modifier.fillMaxWidth().height(1.dp).semantics { heading(); contentDescription = cd })
            val rows = state.rows
            rows.forEachIndexed { i, r ->
                DeltaRowView(r)
                if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun DeltaRowView(r: DeltaRowUi) {
    val label = stringResource(
        when (r.kind) {
            DeltaKind.FILLERS -> R.string.metric_fillers
            DeltaKind.PAUSES -> R.string.metric_pauses
            DeltaKind.PACE -> R.string.metric_pace
        },
    )
    val unit = stringResource(if (r.kind == DeltaKind.PACE) R.string.unit_wpm else R.string.unit_per_min)
    val unitSpoken = stringResource(if (r.kind == DeltaKind.PACE) R.string.a11y_unit_wpm else R.string.a11y_unit_per_min)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text = chipText(r)
    val spoken = stringResource(R.string.a11y_delta_row, label, r.first, r.retake, unitSpoken, text)
    val values = @Composable { modifier: Modifier ->
        Column(modifier) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append(r.first) }
                    withStyle(SpanStyle(color = muted, fontSize = 15.sp)) { append("  →  ") }
                    append(r.retake)
                    withStyle(SpanStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)) { append(unit) }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FrauncesText, fontSize = 19.sp, fontFeatureSettings = "tnum"),
            )
        }
    }
    val icon = when (r.chip) {
        Chip.FEWER, Chip.SLOWER, Chip.SLOWER_COMFORTABLE -> Icons.Rounded.ArrowDownward
        Chip.MORE, Chip.FASTER, Chip.FASTER_COMFORTABLE -> Icons.Rounded.ArrowUpward
        Chip.SAME -> Icons.Rounded.HorizontalRule
        Chip.STILL_COMFORTABLE, Chip.NOW_COMFORTABLE -> Icons.Rounded.Check
    }
    val chip = @Composable {
        if (r.good) {
            InfoChip(text, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary, icon = icon)
        } else {
            InfoChip(text, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, icon = icon)
        }
    }
    // One TalkBack stop per row: "Fillers: from 9.0 to 4.1 per minute. 54% fewer".
    val rowSemantics = Modifier.fillMaxWidth().padding(vertical = 11.dp).semantics(mergeDescendants = true) { contentDescription = spoken }
    if (LocalDensity.current.fontScale > 1.5f) {
        Column(rowSemantics, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values(Modifier)
            chip()
        }
    } else {
        Row(rowSemantics, verticalAlignment = Alignment.CenterVertically) {
            values(Modifier.weight(1f))
            chip()
        }
    }
}

@Composable
private fun chipText(r: DeltaRowUi): String = when (r.chip) {
    Chip.FEWER -> stringResource(R.string.chip_fewer, r.percent)
    Chip.SAME -> stringResource(R.string.chip_same)
    Chip.MORE -> stringResource(R.string.chip_more)
    Chip.STILL_COMFORTABLE -> stringResource(R.string.chip_still_comfortable)
    Chip.FASTER_COMFORTABLE -> stringResource(R.string.chip_faster_comfortable)
    Chip.SLOWER_COMFORTABLE -> stringResource(R.string.chip_slower_comfortable)
    Chip.NOW_COMFORTABLE -> stringResource(R.string.chip_now_comfortable)
    Chip.FASTER -> stringResource(R.string.chip_faster)
    Chip.SLOWER -> stringResource(R.string.chip_slower)
}

/** `.wchip`: one tap adds the word to Word Gym (US-021); toggles like the S11 chips. */
@Composable
private fun WordChip(word: String, added: Boolean, onClick: () -> Unit) {
    val bg = if (added) MaterialTheme.colorScheme.primary else MukharTheme.colors.accentTint
    val fg = if (added) MaterialTheme.colorScheme.onPrimary else MukharTheme.colors.accentInk
    val cd = stringResource(if (added) R.string.added_word_cd else R.string.add_word_cd, word)
    // 48 dp touch target around the 40 dp pill (design-system §10.2: chips get invisible padding).
    Box(
        Modifier.heightIn(min = 48.dp).clip(CircleShape)
            .toggleable(value = added, role = Role.Switch, onValueChange = { onClick() })
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).clip(CircleShape).background(bg).padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (added) Icons.Rounded.Check else Icons.Rounded.Add, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(word, style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}

/** 7 pieces settle from the top over 2.6 s; drawn in place with reduced motion; never covers text (pointer-transparent). */
@Composable
private fun Confetti() {
    val still = reducedMotion()
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still) progress.animateTo(1f, tween(2600)) }
    val colors = listOf(MukharTheme.colors.accent, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    val pieces = remember { listOf(0.08f to 18f, 0.22f to 44f, 0.38f to 10f, 0.55f to 52f, 0.68f to 20f, 0.80f to 60f, 0.90f to 14f) }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val p = progress.value
        pieces.forEachIndexed { i, (x, y) ->
            val dy = (1f - p) * -24.dp.toPx()
            val c = colors[i % colors.size].copy(alpha = (p * 3f).coerceAtMost(1f))
            val s = 8.dp.toPx()
            val o = Offset(size.width * x, y.dp.toPx() + dy)
            if (i % 2 == 0) drawCircle(c, s / 2, o + Offset(s / 2, s / 2)) else drawRect(c, o, Size(s, s))
        }
    }
}
