package com.vaak.app.feature.talk.report

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Support
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.talk.common.BannerTone
import com.vaak.app.feature.talk.common.CenterState
import com.vaak.app.feature.talk.common.CreditPill
import com.vaak.app.feature.talk.common.InfoNote
import com.vaak.app.feature.talk.common.SectionHead
import com.vaak.app.feature.talk.common.Skeleton
import com.vaak.app.feature.talk.common.StepState
import com.vaak.app.feature.talk.common.StepsList
import com.vaak.app.feature.talk.common.VaakAppBar
import com.vaak.app.feature.talk.common.VaakBanner
import com.vaak.app.feature.talk.common.clock
import com.vaak.app.feature.talk.common.langLabel
import com.vaak.app.feature.talk.common.rememberHaptics
import com.vaak.app.feature.talk.common.trackLabel
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.TileShape
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Navigation out of S10/S11. */
data class ReportActions(
    val onBack: () -> Unit,
    val onSayAgain: (ReportUiState) -> Unit,
    val onRecordAgain: (ReportUiState) -> Unit,
    val onSettings: () -> Unit,
)

@Composable
fun ReportScreen(actions: ReportActions, vm: ReportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val messages = mapOf(
        R.string.toast_added to stringResource(R.string.toast_added),
        R.string.toast_added_later to stringResource(R.string.toast_added_later),
        R.string.toast_already_in_gym to stringResource(R.string.toast_already_in_gym),
        R.string.toast_reported to stringResource(R.string.toast_reported),
        R.string.toast_report_failed to stringResource(R.string.toast_report_failed),
    )
    val undo = stringResource(R.string.undo)
    LaunchedEffect(vm) {
        vm.effect.collect { e ->
            when (e) {
                ReportEffect.Added -> haptics.confirm()
                is ReportEffect.Toast -> launch {
                    val result = snackbar.showSnackbar(
                        message = messages[e.message].orEmpty(),
                        actionLabel = e.undo?.let { undo },
                    )
                    if (result == SnackbarResult.ActionPerformed) e.undo?.let(vm::onEvent)
                }
            }
        }
    }
    LaunchedEffect(state.phase) { if (state.phase == ReportPhase.GONE) actions.onBack() }
    ReportContent(state, vm::onEvent, actions, snackbar)
}

/** Stateless S10 + S11 (every state), previewable and tested on Robolectric. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContent(
    state: ReportUiState,
    onEvent: (ReportEvent) -> Unit,
    actions: ReportActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf<MetricKind?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = actions.onBack)

    val phase = state.phase
    val saved = phase == ReportPhase.QUEUED || phase == ReportPhase.BUSY
    val title = stringResource(
        when {
            saved -> R.string.saved_title
            phase == ReportPhase.HELPLINE -> R.string.your_talk
            else -> R.string.report_title
        },
    )
    val backCd = stringResource(
        when (phase) {
            ReportPhase.LISTENING -> R.string.back_continue_cd
            ReportPhase.STT_FAILED -> R.string.back_kept_cd
            else -> R.string.back_to_today
        },
    )
    val showFlag = phase in setOf(ReportPhase.METRICS_READY, ReportPhase.FEEDBACK_FAILED, ReportPhase.READY, ReportPhase.HELPLINE)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VaakAppBar(title, backCd, actions.onBack) {
                if (showFlag) {
                    IconButton(onClick = { reportOpen = true }) { Icon(Icons.Rounded.Flag, stringResource(R.string.report_cd)) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            when (phase) {
                ReportPhase.READY -> CtaBar {
                    if (!state.isRetake) {
                        Button(onClick = { actions.onSayAgain(state) }, modifier = Modifier.weight(1f).height(54.dp)) {
                            Icon(Icons.Rounded.Replay, null, tint = VaakTheme.colors.primaryIcon)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.say_again_cta, clock((state.retakeCapS ?: 0) * 1000)), maxLines = 2)
                        }
                    }
                    OutlinedButton(onClick = actions.onBack, modifier = Modifier.height(54.dp).then(if (state.isRetake) Modifier.weight(1f) else Modifier)) {
                        Text(stringResource(R.string.done))
                    }
                }
                ReportPhase.HELPLINE -> CtaBar {
                    OutlinedButton(onClick = actions.onBack, modifier = Modifier.weight(1f).height(54.dp)) { Text(stringResource(R.string.back_to_today)) }
                }
                else -> Unit
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).padding(bottom = 24.dp),
        ) {
            when (phase) {
                ReportPhase.LISTENING -> Listening(state)
                ReportPhase.QUEUED, ReportPhase.BUSY -> Waiting(state, actions.onBack) { confirmDiscard = true }
                ReportPhase.STT_FAILED -> SttFailed(state, onRetry = { onEvent(ReportEvent.RetryTranscript) }, onLater = actions.onBack)
                ReportPhase.TOO_LITTLE -> TooLittle(state, onRetry = { actions.onRecordAgain(state) }, onBack = actions.onBack)
                ReportPhase.HELPLINE -> Helpline(state) { info = it }
                ReportPhase.METRICS_READY, ReportPhase.FEEDBACK_FAILED, ReportPhase.READY -> Report(state, onEvent, actions, onInfo = { info = it }) { reportOpen = true }
                ReportPhase.GONE -> Unit
            }
        }
    }

    if (reportOpen) {
        ReportSheet(onDismiss = { reportOpen = false }) { reason, consent, note ->
            reportOpen = false
            onEvent(ReportEvent.SendReport(reason, consent, note))
        }
    }
    info?.let { MetricInfoSheet(it) { info = null } }
    if (confirmDiscard) {
        ModalBottomSheet(onDismissRequest = { confirmDiscard = false }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
                Text(stringResource(R.string.discard_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Button(onClick = { confirmDiscard = false }, modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 12.dp)) { Text(stringResource(R.string.keep_it)) }
                TextButton(onClick = { confirmDiscard = false; onEvent(ReportEvent.Discard) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.discard)) }
            }
        }
    }
}

/** `.cta-bar`: sticky, paper gradient above, padded for the navigation bar (edge-to-edge). */
@Composable
fun CtaBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val paper = MaterialTheme.colorScheme.background
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to paper.copy(alpha = 0f), 0.3f to paper))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = VaakSpace.gutter, end = VaakSpace.gutter, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Meta(state: ReportUiState) {
    val m = state.meta ?: return
    val today = LocalDate.now(m.zone)
    val at = m.createdAt.atZone(m.zone)
    val time = at.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).lowercase(Locale.US)
    val whenText = if (at.toLocalDate() == today) {
        stringResource(R.string.meta_today, time)
    } else {
        at.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US))
    }
    val parts = listOf(
        stringResource(R.string.dot_pair, stringResource(langLabel(m.lang)), stringResource(trackLabel(m.track))),
        clock(m.durationMs),
        whenText,
    )
    Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Summary(text: String, lang: Lang) {
    val style = if (lang == Lang.HI) VaakTheme.type.hiHeadline else MaterialTheme.typography.headlineMedium
    Text(text, style = style, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
}

@Composable
private fun ColumnScope.Listening(state: ReportUiState) {
    CenterState(
        icon = null,
        title = stringResource(R.string.listening),
        body = stringResource(R.string.listening_body),
        leading = { CircularProgressIndicator(Modifier.size(64.dp), strokeWidth = 5.dp, trackColor = MaterialTheme.colorScheme.primaryContainer) },
    ) {
        state.credit?.let { CreditPill(it, Modifier.padding(top = 16.dp)) }
        StepsList(
            listOf(
                stringResource(R.string.step_saved, clock(state.meta?.durationMs ?: 0)) to StepState.DONE,
                stringResource(R.string.step_transcript) to StepState.NOW,
                stringResource(R.string.step_measure) to StepState.TODO,
                stringResource(R.string.step_notes) to StepState.TODO,
            ),
        )
    }
}

@Composable
private fun ColumnScope.Waiting(state: ReportUiState, onBack: () -> Unit, onDiscard: () -> Unit) {
    val busy = state.phase == ReportPhase.BUSY
    CenterState(
        icon = if (busy) Icons.Rounded.HourglassTop else Icons.Rounded.CloudUpload,
        title = stringResource(if (busy) R.string.busy_title else R.string.queued_title),
        body = stringResource(if (busy) R.string.busy_body else R.string.queued_body),
    ) {
        state.credit?.let { CreditPill(it, Modifier.padding(top = 16.dp)) }
        VaakCard(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(if (busy) Icons.Rounded.Schedule else Icons.Rounded.HourglassTop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.waiting_card, clock(state.meta?.durationMs ?: 0)), style = MaterialTheme.typography.labelLarge)
                    val m = state.meta
                    if (m != null) {
                        val time = m.createdAt.atZone(m.zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).lowercase(Locale.US)
                        Text(stringResource(R.string.quoted_with_time, state.promptText, time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
        InfoNote(Icons.Rounded.Notifications, stringResource(if (busy) R.string.busy_note else R.string.queued_note), Modifier.padding(top = 12.dp))
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.back_to_today)) }
    if (!busy) TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.discard_talk)) }
}

@Composable
private fun ColumnScope.SttFailed(state: ReportUiState, onRetry: () -> Unit, onLater: () -> Unit) {
    CenterState(Icons.Rounded.GraphicEq, stringResource(R.string.stt_title), stringResource(R.string.stt_body), assertive = true) {
        state.credit?.let { CreditPill(it, Modifier.padding(top = 16.dp)) }
        StepsList(
            listOf(
                stringResource(R.string.step_saved, clock(state.meta?.durationMs ?: 0)) to StepState.DONE,
                stringResource(R.string.step_transcript_retry) to StepState.RETRY,
                stringResource(R.string.step_measure) to StepState.TODO,
                stringResource(R.string.step_notes) to StepState.TODO,
            ),
            retryIcon = Icons.Rounded.Replay,
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Icon(Icons.Rounded.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.try_again))
    }
    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keep_for_later)) }
}

@Composable
private fun ColumnScope.TooLittle(state: ReportUiState, onRetry: () -> Unit, onBack: () -> Unit) {
    CenterState(Icons.Rounded.Hearing, stringResource(R.string.low_title), pluralStringResource(R.plurals.low_body, (state.speechMs / 1000).toInt(), (state.speechMs / 1000).toInt()), assertive = true, artSize = 88.dp)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.try_again)) }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back_to_today)) }
}

@Composable
private fun ColumnScope.Helpline(state: ReportUiState, onInfo: (MetricKind) -> Unit) {
    val context = LocalContext.current
    var showNumbers by rememberSaveable { mutableStateOf(false) }
    Meta(state)
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer).padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Support, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp).semantics { heading() })
        Text(stringResource(R.string.help_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 14.dp))
        Button(
            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:14416".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Rounded.Call, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.help_call))
        }
        Text(stringResource(R.string.help_small), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.help_trust))
        }
    }
    state.credit?.let { CreditPill(it, Modifier.padding(top = 4.dp)) }
    ExpandRow(stringResource(R.string.show_numbers), showNumbers) { showNumbers = !showNumbers }
    AnimatedVisibility(showNumbers) { MetricsGrid(state.tiles, onInfo) }
}

@Composable
private fun ExpandRow(label: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.secondary)
    }
}

/** S10 states 2 and 4 and the full S11: metrics first (D-019), then transcript, Glow, Grow, better lines. */
@Composable
private fun ColumnScope.Report(
    state: ReportUiState,
    onEvent: (ReportEvent) -> Unit,
    actions: ReportActions,
    onInfo: (MetricKind) -> Unit,
    onReport: () -> Unit,
) {
    val lang = state.meta?.lang ?: Lang.EN
    val scope = rememberCoroutineScope()
    val transcriptAnchor = remember { BringIntoViewRequester() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val phase = state.phase

    Meta(state)
    when {
        phase == ReportPhase.METRICS_READY -> Column(Modifier.padding(top = 10.dp, bottom = 14.dp)) {
            Skeleton(0.9f, height = 20.dp)
            Skeleton(0.6f, height = 20.dp)
        }
        phase == ReportPhase.FEEDBACK_FAILED ->
            Summary(stringResource(if (state.fillersDown) R.string.summary_numbers_fillers else R.string.summary_numbers), Lang.EN)
        state.summary != null -> Summary(state.summary, lang)
    }
    state.credit?.let { CreditPill(it) }
    if (state.stutterFriendly) StutterNote(actions.onSettings)
    if (state.noisy) {
        VaakBanner(Icons.Rounded.GraphicEq, stringResource(R.string.noise_title), stringResource(R.string.noise_body_results), BannerTone.WARN, Modifier.padding(top = 12.dp), assertive = true)
    }

    if (phase == ReportPhase.FEEDBACK_FAILED) {
        DashedCard(Modifier.padding(top = 16.dp, bottom = 10.dp).semantics { liveRegion = LiveRegionMode.Assertive }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.EditNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                Text(stringResource(R.string.fb_error_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                Text(stringResource(R.string.fb_error_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                Button(onClick = { onEvent(ReportEvent.RetryFeedback) }, enabled = !state.retrying) {
                    if (state.retrying) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (state.manualRetryFailed) R.string.try_later else R.string.try_again))
                    }
                }
            }
        }
    }

    if (state.tiles.isNotEmpty()) {
        MetricsGrid(state.tiles, onInfo, Modifier.padding(top = 16.dp, bottom = 22.dp))
    }

    state.transcript?.let { t ->
        val (from, to) = transcriptRange(t, expanded)
        SectionHead(stringResource(R.string.transcript_title), stringResource(R.string.transcript_range, from, to))
        if (phase == ReportPhase.READY) TranscriptLegend(t, lang)
        TranscriptCard(t, lang, expanded, Modifier.bringIntoViewRequester(transcriptAnchor))
        if (t.paragraphs.size > ReportMapper.COLLAPSED_PARAGRAPHS) {
            ExpandRow(stringResource(if (expanded) R.string.show_less else R.string.show_full_transcript), expanded) { expanded = !expanded }
        }
        if (lang == Lang.HI && phase == ReportPhase.READY) {
            InfoNote(Icons.Rounded.Translate, stringResource(R.string.hi_borrowing_note), Modifier.padding(top = 8.dp, bottom = 16.dp))
        }
    }

    if (phase == ReportPhase.METRICS_READY) {
        Row(Modifier.padding(top = 18.dp, bottom = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 3.dp, trackColor = MaterialTheme.colorScheme.primaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.writing_notes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        SkeletonCard(listOf(0.4f, 0.9f, 0.8f, 0.6f))
        SkeletonCard(listOf(0.4f, 0.8f, 0.9f))
        return
    }

    val toTranscript: () -> Unit = { scope.launch { transcriptAnchor.bringIntoView() } }
    Spacer(Modifier.height(12.dp))
    AnimatedVisibility(visible = state.glow != null, enter = fadeIn()) { state.glow?.let { GlowCard(it, lang, toTranscript) } }
    AnimatedVisibility(visible = state.grow != null, enter = fadeIn()) { state.grow?.let { GrowCard(it, lang, toTranscript) } }

    if (state.better.isNotEmpty()) {
        SectionHead(stringResource(R.string.better_title), betterCount(state.better.size), Modifier.padding(top = 10.dp))
        Row(Modifier.padding(start = 2.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.better_tip), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (b in state.better) {
            BetterRow(
                b, lang,
                collected = { w -> com.vaak.data.repo.TalkRepository.wordId(lang, w.word) in state.collectedWordIds },
                saved = b.index in state.savedLines,
                onWord = { w -> onEvent(ReportEvent.ToggleWord(w, b.rewrite)) },
                onSave = { onEvent(ReportEvent.ToggleSave(b)) },
            )
        }
    }
    if (phase == ReportPhase.READY) {
        if (state.showRating) RatingRow(state.rating) { onEvent(ReportEvent.Rate(it)) }
        ReportLink(onReport)
    }
}

@Composable
private fun StutterNote(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SelfImprovement, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.sfm_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onSettings) { Text(stringResource(R.string.sfm_settings), color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun SkeletonCard(widths: List<Float>) {
    VaakCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 120.dp), shape = TileShape) {
        Column(Modifier.padding(16.dp)) {
            widths.forEachIndexed { i, w -> Skeleton(w, height = if (i == 1) 20.dp else 12.dp) }
        }
    }
}

