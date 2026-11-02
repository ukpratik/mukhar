package com.vaak.app.feature.talk.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SdCardAlert
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.talk.common.BannerTone
import com.vaak.app.feature.talk.common.CenterState
import com.vaak.app.feature.talk.common.VaakBanner
import com.vaak.app.feature.talk.common.clock
import com.vaak.app.feature.talk.common.langLabel
import com.vaak.app.feature.talk.common.reducedMotion
import com.vaak.app.feature.talk.common.rememberHaptics
import com.vaak.app.feature.talk.common.trackLabel
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.FrauncesDisplay
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import com.vaak.ui.theme.timerStyle

@Composable
fun RecordScreen(
    onLeave: () -> Unit,
    onReport: (String) -> Unit,
    onSayAgain: (String) -> Unit,
    onQuota: () -> Unit = {},
    vm: RecordViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val micBusy = stringResource(R.string.mic_unavailable)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onEvent(RecordEvent.PermissionResult(it))
    }
    // RECORD_AUDIO is requested in context: the user just asked to record.
    LaunchedEffect(state.phase == RecordPhase.PERMISSION, state.permissionDenied) {
        if (state.phase != RecordPhase.PERMISSION || state.permissionDenied) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.onEvent(RecordEvent.PermissionResult(true)) else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(vm) {
        vm.effect.collect { e ->
            when (e) {
                RecordEffect.Leave -> onLeave()
                is RecordEffect.OpenReport -> onReport(e.sessionId)
                is RecordEffect.OpenSayAgain -> onSayAgain(e.retakeId)
                RecordEffect.HapticConfirm -> haptics.confirm()
                RecordEffect.HapticTick -> haptics.tick()
                RecordEffect.MicUnavailable -> snackbar.showSnackbar(micBusy)
                RecordEffect.OpenQuota -> onQuota()
                RecordEffect.OpenStorageSettings -> runCatching {
                    context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
    // The mic can't record in the background (no foreground service): going away auto-pauses (S09 state 4).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.onEvent(RecordEvent.AppBackgrounded) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val view = LocalView.current
    val active = state.phase == RecordPhase.RECORDING || state.phase == RecordPhase.PAUSED
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { vm.onEvent(RecordEvent.Close) }

    RecordContent(
        state = state,
        onEvent = vm::onEvent,
        onAllowAgain = {
            val activity = context as? android.app.Activity
            val canAsk = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: true
            if (canAsk) {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onOpenStorage = vm::openStorageSettings,
        snackbar = snackbar,
    )
}

/** Stateless S09 (every state). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordContent(
    state: RecordUiState,
    onEvent: (RecordEvent) -> Unit,
    onAllowAgain: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val paper = MaterialTheme.colorScheme.background
    val tint = MaterialTheme.colorScheme.primaryContainer
    Scaffold(containerColor = paper, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(paper, lerp(paper, tint, 0.15f))))
                .statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VaakSpace.gutter).padding(bottom = 28.dp),
        ) {
            TopRow(state, onEvent)
            when (state.phase) {
                RecordPhase.LOADING, RecordPhase.SAVING -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                RecordPhase.PERMISSION -> Permission(state.permissionDenied, onAllowAgain)
                RecordPhase.READY -> Ready(state, onEvent)
                RecordPhase.THINK -> Think(state, onEvent)
                RecordPhase.RECORDING, RecordPhase.PAUSED -> Recording(state, onEvent)
                RecordPhase.TOO_SHORT -> TooShort(state, onEvent)
            }
        }
    }
    if (state.storageSheet != StorageSheet.NONE) StorageSheetView(state, onEvent, onOpenStorage)
    if (state.discardSheet) {
        ModalBottomSheet(onDismissRequest = { onEvent(RecordEvent.KeepTalking) }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
                Text(stringResource(R.string.discard_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Button(onClick = { onEvent(RecordEvent.KeepTalking) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(54.dp)) { Text(stringResource(R.string.keep_talking)) }
                TextButton(onClick = { onEvent(RecordEvent.ConfirmDiscard) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.discard)) }
            }
        }
    }
}

@Composable
private fun TopRow(state: RecordUiState, onEvent: (RecordEvent) -> Unit) {
    val active = state.phase == RecordPhase.RECORDING || state.phase == RecordPhase.PAUSED
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onEvent(RecordEvent.Close) }) {
            Icon(Icons.Rounded.Close, stringResource(if (active) R.string.close_discard_cd else R.string.close))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when (state.phase) {
                RecordPhase.READY -> StatusPill(stringResource(R.string.status_ready)) { HollowDot() }
                RecordPhase.THINK -> StatusPill(stringResource(R.string.status_think)) { Icon(Icons.Rounded.Psychology, null, Modifier.size(18.dp)) }
                RecordPhase.RECORDING -> StatusPill(stringResource(R.string.status_recording), live = true) { RecDot() }
                RecordPhase.PAUSED -> StatusPill(stringResource(R.string.status_paused), live = true) { Icon(Icons.Rounded.Pause, null, Modifier.size(18.dp)) }
                else -> Unit
            }
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun StatusPill(text: String, live: Boolean = false, icon: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        }
    }
}

@Composable
private fun HollowDot() {
    Box(Modifier.size(10.dp).border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape))
}

/** `.rec-dot`: breathes over 2.4 s unless reduced motion is on. */
@Composable
private fun RecDot() {
    val alpha = if (reducedMotion()) {
        1f
    } else {
        rememberInfiniteTransition(label = "breathe").animateFloat(1f, 0.35f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "a").value
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(VaakTheme.colors.accent.copy(alpha = alpha)))
}

@Composable
private fun PromptCard(state: RecordUiState, showHint: Boolean) {
    val prompt = state.prompt ?: return
    val eyebrow = stringResource(R.string.dot_pair, stringResource(langLabel(prompt.lang)), stringResource(trackLabel(state.track)))
    VaakCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                (if (state.isRetake) stringResource(R.string.retake_eyebrow, eyebrow) else eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val style = if (prompt.lang == Lang.HI) VaakTheme.type.hiPrompt.copy(fontSize = 18.sp) else VaakTheme.type.prompt.copy(fontSize = 18.sp, lineHeight = 24.sp)
            Text(prompt.text, style = style, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            val tip = prompt.tip ?: if (prompt.prep.isNotEmpty() && state.structureHint) stringResource(R.string.prep_tip) else null
            if (showHint && tip != null) {
                Row(Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = VaakTheme.colors.accentInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PrivateLine() {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.private_line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** `.big`: 84 dp. Ready = saffron mic ("go"), recording = indigo with a saffron square, paused = indigo play. */
@Composable
private fun BigButton(label: String, contentDescription: String, go: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val fill = if (go) VaakTheme.colors.accent else MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(
            Modifier.size(100.dp).clip(CircleShape).background(fill.copy(alpha = if (go) 0.22f else 0.12f))
                .clickable(onClick = onClick, role = Role.Button)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) { icon() }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).semantics { invisibleToUser() })
    }
}

/** `.round`: 60 dp secondary control. */
@Composable
private fun RoundButton(label: String, contentDescription: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).padding(top = 12.dp)) {
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.5.dp, VaakTheme.colors.lineStrong, CircleShape)
                .clickable(onClick = onClick, role = Role.Button)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).semantics { invisibleToUser() })
    }
}

@Composable
private fun ColumnScope.Ready(state: RecordUiState, onEvent: (RecordEvent) -> Unit) {
    PromptCard(state, showHint = true)
    val check = state.micCheck
    val noisy = check?.noisy == true
    if (noisy) {
        VaakBanner(Icons.Rounded.GraphicEq, stringResource(R.string.noise_title), stringResource(R.string.noise_body), BannerTone.WARN, Modifier.padding(top = 12.dp), assertive = true)
    }
    if (check != null) LevelCard(check.bars, noisy)
    if (!noisy) {
        VaakCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.think_switch), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.think_switch_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val cd = stringResource(R.string.think_switch)
                Switch(checked = state.thinkTime, onCheckedChange = { onEvent(RecordEvent.SetThinkTime(it)) }, modifier = Modifier.semantics { contentDescription = cd })
            }
        }
    }
    Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
    val cap = clock(state.capMs)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val label = if (noisy) stringResource(R.string.record_anyway) else stringResource(R.string.start_cta, cap)
        BigButton(label, if (noisy) label else stringResource(R.string.start_cd, cap), go = true, onClick = { onEvent(RecordEvent.Start) }) {
            Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.onAccent, modifier = Modifier.size(40.dp))
        }
        if (noisy) TextButton(onClick = { onEvent(RecordEvent.CheckAgain) }, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.check_again)) }
    }
    PrivateLine()
}

/** `.level`: 5 bars (glow when quiet, saffron when noisy) + one line. */
@Composable
private fun LevelCard(bars: Int, noisy: Boolean) {
    VaakCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(20.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val heights = listOf(6, 10, 14, 17, 20)
                heights.forEachIndexed { i, h ->
                    val on = i < bars
                    val color = when {
                        !on -> VaakTheme.colors.track
                        noisy -> VaakTheme.colors.accent
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    Box(Modifier.width(5.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(color))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (noisy) R.string.mic_noisy else R.string.mic_good), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(if (noisy) R.string.mic_noisy_sub else R.string.mic_good_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (noisy) {
                Icon(Icons.Rounded.Error, null, tint = VaakTheme.colors.accentInk)
            } else {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun ColumnScope.Think(state: RecordUiState, onEvent: (RecordEvent) -> Unit) {
    PromptCard(state, showHint = true)
    val track = VaakTheme.colors.track
    val accent = VaakTheme.colors.accent
    val fraction = state.thinkLeft / 30f
    Box(Modifier.padding(top = 22.dp).size(150.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(clock(state.thinkLeft * 1000L), style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp, lineHeight = 48.sp, fontFamily = FrauncesDisplay))
            Text(stringResource(R.string.think_left), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val live = pluralStringResource(R.plurals.think_live, state.thinkLeft, state.thinkLeft)
    Text("", Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = if (state.thinkLeft % 10 == 0) live else "" })
    Text(stringResource(R.string.think_nudge), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
    Button(onClick = { onEvent(RecordEvent.StartNow) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.primaryIcon)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.start_now))
    }
    PrivateLine()
}

@Composable
private fun ColumnScope.Recording(state: RecordUiState, onEvent: (RecordEvent) -> Unit) {
    val paused = state.phase == RecordPhase.PAUSED
    if (paused && state.autoPaused) {
        VaakBanner(Icons.Rounded.PauseCircle, stringResource(R.string.paused_away_title), stringResource(R.string.paused_away_body), BannerTone.INFO, Modifier.padding(top = 8.dp))
    }
    if (!paused) PromptCard(state, showHint = false)
    val cap = clock(state.capMs)
    // TalkBack: elapsed time every 30 s (design-system §10).
    val announce = stringResource(R.string.recording_live, clock(state.elapsedMs / 30_000 * 30_000), cap)
    val started = stringResource(R.string.recording_started)
    Text("", Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = if (state.elapsedMs < 30_000) started else announce })
    Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            clock(state.elapsedMs),
            style = timerStyle(),
            color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (paused) R.string.timer_of_paused else R.string.timer_of, cap),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
    }
    ProgressTrack(state.elapsedMs, state.capMs, paused)
    Waveform(state.waveform, still = paused)
    Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Top) {
        if (paused) {
            RoundButton(stringResource(R.string.stop), stringResource(R.string.stop_cd), { onEvent(RecordEvent.Stop) }, Icons.Rounded.Stop)
            BigButton(stringResource(R.string.resume), stringResource(R.string.resume_cd), go = false, onClick = { onEvent(RecordEvent.Resume) }) {
                Icon(Icons.Rounded.PlayArrow, null, tint = VaakTheme.colors.accent, modifier = Modifier.size(40.dp))
            }
            Text(
                pluralStringResource(R.plurals.pauses_left, state.pausesLeft, state.pausesLeft),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp).padding(top = 32.dp),
            )
        } else {
            Spacer(Modifier.width(72.dp))
            BigButton(stringResource(R.string.stop), stringResource(R.string.stop_cd), go = false, onClick = { onEvent(RecordEvent.Stop) }) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(VaakTheme.colors.accent))
            }
            if (state.pausesLeft > 0) {
                RoundButton(
                    stringResource(R.string.pause),
                    pluralStringResource(R.plurals.pause_cd, state.pausesLeft, state.pausesLeft),
                    { onEvent(RecordEvent.Pause) },
                    Icons.Rounded.Pause,
                )
            } else {
                Spacer(Modifier.width(72.dp))
            }
        }
    }
    PrivateLine()
}

/** Timer track with soft markers at 1:00 and 2:00; the last 15 s warm from indigo to saffron. */
@Composable
private fun ProgressTrack(elapsedMs: Long, capMs: Long, paused: Boolean) {
    val track = VaakTheme.colors.track
    val markerColor = VaakTheme.colors.lineStrong
    val passedColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val brand = MaterialTheme.colorScheme.primary
    val warm = capMs - elapsedMs <= 15_000
    val fillColor by animateColorAsState(if (warm) VaakTheme.colors.accent else brand, label = "fill")
    val fraction = (elapsedMs.toFloat() / capMs).coerceIn(0f, 1f)
    val markers = listOf(60_000L, 120_000L).filter { it < capMs }
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Canvas(Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 6.dp)) {
            val h = 6.dp.toPx()
            val top = (size.height - h) / 2
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2)
            drawRoundRect(track, Offset(0f, top), Size(size.width, h), r)
            drawRoundRect(fillColor.copy(alpha = if (paused) 0.5f else 1f), Offset(0f, top), Size(size.width * fraction, h), r)
            for (m in markers) {
                val x = size.width * m / capMs
                drawRoundRect(if (elapsedMs >= m) passedColor else markerColor, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
            val hx = size.width * fraction
            drawCircle(passedColor, 7.dp.toPx(), Offset(hx, size.height / 2))
            drawCircle(brand, 7.dp.toPx(), Offset(hx, size.height / 2), style = Stroke(3.dp.toPx()))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 6.dp).semantics { invisibleToUser() }) {
            for (m in markers) {
                val x = maxWidth * (m.toFloat() / capMs)
                Text(clock(m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.offset(x = x - 12.dp, y = 4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp).semantics { invisibleToUser() }, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(clock(capMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** `.wave`: shows only that you're heard (no live judgement). Static bars when paused or with reduced motion. */
@Composable
private fun Waveform(peaks: List<Float>, still: Boolean) {
    val brand2 = MaterialTheme.colorScheme.secondary
    val accent = VaakTheme.colors.accent
    val reduced = reducedMotion()
    Row(
        Modifier.fillMaxWidth().height(88.dp).padding(top = 14.dp).semantics { invisibleToUser() },
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bars = 26
        for (i in 0 until bars) {
            val p = peaks.getOrNull(peaks.size - bars + i) ?: 0f
            val h = when {
                still -> 12.dp
                reduced -> 40.dp
                else -> (10 + (p * 2.2f).coerceAtMost(1f) * 70).dp
            }
            val color = if (i % 3 == 2) accent.copy(alpha = if (still) 0.45f else 0.95f) else brand2.copy(alpha = if (still) 0.45f else 0.8f)
            Box(Modifier.width(4.dp).height(h).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
private fun ColumnScope.TooShort(state: RecordUiState, onEvent: (RecordEvent) -> Unit) {
    CenterState(
        Icons.Rounded.Hearing, stringResource(R.string.low_title), pluralStringResource(R.plurals.low_body, state.tooShortSpeechS, state.tooShortSpeechS),
        assertive = true, artSize = 88.dp,
    ) {
        VaakCard(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Tip(Icons.Rounded.BackHand, stringResource(R.string.low_tip_distance))
                Tip(Icons.Rounded.MicExternalOn, stringResource(R.string.low_tip_mic))
                Tip(Icons.Rounded.RecordVoiceOver, stringResource(R.string.low_tip_start))
            }
        }
    }
    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
    Button(onClick = { onEvent(RecordEvent.TryAgain) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.primaryIcon)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.try_again))
    }
    TextButton(onClick = { onEvent(RecordEvent.Close) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back_to_today)) }
}

@Composable
private fun Tip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ColumnScope.Permission(denied: Boolean, onAllow: () -> Unit) {
    if (!denied) return
    CenterState(Icons.Rounded.Mic, stringResource(R.string.perm_title), stringResource(R.string.perm_denied_body), artSize = 88.dp)
    Text(stringResource(R.string.perm_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.perm_allow)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageSheetView(state: RecordUiState, onEvent: (RecordEvent) -> Unit, onOpenStorage: () -> Unit) {
    val block = state.storageSheet == StorageSheet.BLOCK
    ModalBottomSheet(onDismissRequest = { onEvent(RecordEvent.DismissStorage) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
            Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SdCardAlert, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
            Text(
                stringResource(if (block) R.string.storage_block_body else R.string.storage_body, state.availableMb),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (block) {
                Button(onClick = onOpenStorage, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.open_storage)) }
                TextButton(onClick = { onEvent(RecordEvent.DismissStorage) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            } else {
                Button(onClick = { onEvent(RecordEvent.RecordAnyway) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.primaryIcon)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.record_anyway))
                }
                TextButton(onClick = onOpenStorage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_storage)) }
            }
        }
    }
}

