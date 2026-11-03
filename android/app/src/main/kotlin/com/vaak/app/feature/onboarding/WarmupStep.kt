package com.vaak.app.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaak.app.R
import com.vaak.app.feature.onboarding.warmup.PaceBand
import com.vaak.app.feature.onboarding.warmup.VolumeBand
import com.vaak.app.feature.onboarding.warmup.WarmupAnalyzer
import com.vaak.app.feature.talk.common.reducedMotion
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.TileShape
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

@Composable
fun readAloudText(lang: Lang): String =
    stringResource(if (lang == Lang.HI) R.string.warmup_text_hi else R.string.warmup_text_en)

/** S03: read a short paragraph for 20 s; encouragement is worked out on the phone (US-002). */
@Composable
fun WarmupBody(state: OnboardingUiState, onReadInHindi: (Boolean) -> Unit) {
    val lang = state.readAloudLang
    Column {
        when (val w = state.warmup) {
            WarmupUi.Ready, WarmupUi.TooShort, WarmupUi.MicError -> {
                ObHeadline(stringResource(R.string.warmup_title))
                ObLead(stringResource(R.string.warmup_lead))
                ReadCard(lang, waiting = true)
                if (state.langChoice == LangChoice.BOTH) {
                    TextButton(onClick = { onReadInHindi(!state.readInHindi) }) {
                        Text(stringResource(if (state.readInHindi) R.string.warmup_read_in_english else R.string.warmup_read_in_hindi))
                    }
                }
                when (w) {
                    WarmupUi.TooShort -> Banner(stringResource(R.string.warmup_too_short))
                    // Errors use an assertive live region (design §10.4).
                    WarmupUi.MicError -> Banner(stringResource(R.string.warmup_mic_error), assertive = true)
                    else -> MicWhy()
                }
            }
            is WarmupUi.Recording -> RecordingBody(w, lang)
            is WarmupUi.Done -> DoneBody(w)
        }
    }
}

@Composable
private fun ReadCard(lang: Lang, waiting: Boolean) {
    VaakCard(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = VaakSpace.l, vertical = 14.dp)) {
            Eyebrow(stringResource(if (lang == Lang.HI) R.string.warmup_eyebrow_hi else R.string.warmup_eyebrow_en))
            val style: TextStyle = if (lang == Lang.HI) VaakTheme.type.hiPrompt.copy(fontSize = 18.sp) else VaakTheme.type.prompt.copy(fontSize = 18.sp, lineHeight = 27.sp)
            Text(
                readAloudText(lang),
                style = style,
                color = if (waiting) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MicWhy() {
    Row(
        Modifier.fillMaxWidth().padding(top = VaakSpace.m).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column {
            Text(stringResource(R.string.warmup_mic_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.warmup_mic_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Banner(text: String, assertive: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(top = VaakSpace.m).clip(MaterialTheme.shapes.medium)
            .background(VaakTheme.colors.accentTint).padding(horizontal = 14.dp, vertical = VaakSpace.m)
            .semantics { liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingBody(w: WarmupUi.Recording, lang: Lang) {
    val seconds = (w.elapsedMs / 1000).toInt()
    val a11y = stringResource(R.string.warmup_recording_a11y, seconds)
    val started = stringResource(R.string.recording_started)
    FlowRow(
        Modifier.fillMaxWidth().padding(top = VaakSpace.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(VaakSpace.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Start is announced once here; the timer itself is not a live region (it would speak every second).
        Row(
            Modifier.clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .semantics(mergeDescendants = true) { contentDescription = started; liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaakSpace.s),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(VaakTheme.colors.accent))
            Text(stringResource(R.string.warmup_listening), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = a11y }) {
            Text(
                "0:%02d".format(seconds),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 40.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(R.string.warmup_of), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = VaakSpace.xs, bottom = VaakSpace.xs))
        }
    }
    ProgressTrack(w.elapsedMs.toFloat() / WarmupAnalyzer.DURATION_MS)
    ReadCard(lang, waiting = false)
    Wave(w.levels)
}

@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(RoundedCornerShape(6.dp)).background(VaakTheme.colors.track)
            .clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary))
    }
}

/** Live level bars (saffron fill). Decorative: TalkBack hears the timer instead. Static with reduced motion (§8). */
@Composable
private fun Wave(levels: List<Float>) {
    val color = VaakTheme.colors.accent
    val still = reducedMotion()
    Canvas(Modifier.fillMaxWidth().height(80.dp).padding(vertical = VaakSpace.s).clearAndSetSemantics {}) {
        val bars = 24
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = if (still) {
                STATIC_WAVE[i % STATIC_WAVE.size] * size.height
            } else {
                val level = levels.getOrNull(levels.size - bars + i) ?: -60f
                (((level + 60f) / 60f).coerceIn(0.08f, 1f)) * size.height
            }
            drawRoundRect(color, Offset(i * (w + gap), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}

/** The big round stop control: indigo circle, saffron square, label below. */
@Composable
fun StopControl(label: String, a11y: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = VaakSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = a11y },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(VaakTheme.colors.accent))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics {})
    }
}

@Composable
fun PrivateLine(text: String, filledIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.PhoneAndroid) {
    Row(
        Modifier.fillMaxWidth().padding(top = VaakSpace.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(filledIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun DoneBody(w: WarmupUi.Done) {
    val r = w.result
    val good = r.pace == PaceBand.COMFORTABLE && r.volume == VolumeBand.CLEAR
    Column(Modifier.semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(30.dp))
        }
        ObHeadline(stringResource(if (good) R.string.warmup_done_title else R.string.warmup_done_title_other), Modifier.padding(top = 14.dp))
        ObLead(stringResource(R.string.warmup_done_lead))
    }
    Row(Modifier.fillMaxWidth().padding(top = VaakSpace.l, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BandTile(
            label = stringResource(R.string.warmup_pace),
            value = stringResource(
                when (r.pace) {
                    PaceBand.SLOW -> R.string.pace_slow
                    PaceBand.COMFORTABLE -> R.string.pace_comfortable
                    PaceBand.FAST -> R.string.pace_fast
                },
            ),
            marker = r.paceMarker,
            start = stringResource(R.string.pace_end_slow),
            end = stringResource(R.string.pace_end_fast),
            modifier = Modifier.weight(1f),
        )
        BandTile(
            label = stringResource(R.string.warmup_volume),
            value = stringResource(
                when (r.volume) {
                    VolumeBand.SOFT -> R.string.volume_soft
                    VolumeBand.CLEAR -> R.string.volume_clear
                    VolumeBand.LOUD -> R.string.volume_loud
                    VolumeBand.NOISY -> R.string.volume_noisy
                },
            ),
            marker = r.volumeMarker,
            start = stringResource(R.string.volume_end_soft),
            end = stringResource(R.string.volume_end_loud),
            modifier = Modifier.weight(1f),
        )
    }
    CreditPill(if (w.day <= 1) stringResource(R.string.warmup_day_one) else stringResource(R.string.warmup_day_n, w.day))
    InfoNote(Icons.Outlined.PhoneAndroid, stringResource(R.string.warmup_on_phone), Modifier.padding(top = 14.dp))
}

/** A metric tile with the 3-segment band and an indigo marker; the meaning is in the word (design §6). */
@Composable
private fun BandTile(label: String, value: String, marker: Float, start: String, end: String, modifier: Modifier = Modifier) {
    val a11y = stringResource(R.string.warmup_band_a11y, label, value)
    VaakCard(modifier.semantics(mergeDescendants = true) { contentDescription = a11y }, shape = TileShape) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = VaakSpace.m)) {
            Eyebrow(label)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = VaakSpace.xs))
            val track = VaakTheme.colors.track
            val ok = VaakTheme.colors.bandOk
            val markerColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = VaakSpace.s).clearAndSetSemantics {}) {
                val segW = size.width / 3
                val h = 6.dp.toPx()
                listOf(track, ok, track).forEachIndexed { i, c ->
                    drawRoundRect(c, Offset(i * segW + if (i > 0) 1.dp.toPx() else 0f, 0f), Size(segW - 2.dp.toPx(), h), CornerRadius(h / 2))
                }
                val x = size.width * marker
                drawRoundRect(markerColor, Offset(x - 2.dp.toPx(), -3.dp.toPx()), Size(4.dp.toPx(), h + 6.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(start, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(end, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Day 1 counted" pill (accent tint / accent ink). */
@Composable
fun CreditPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(VaakTheme.colors.accentTint).padding(start = 10.dp, end = VaakSpace.m, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = VaakTheme.colors.accentInk, modifier = Modifier.size(17.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = VaakTheme.colors.accentInk)
    }
}

@Composable
fun WarmupCta(
    state: OnboardingUiState,
    onAllowMic: () -> Unit,
    onNotNow: () -> Unit,
    onDone: () -> Unit,
    onContinue: () -> Unit,
) {
    when (state.warmup) {
        is WarmupUi.Recording -> CtaBar {
            StopControl(stringResource(R.string.warmup_done), stringResource(R.string.warmup_done_a11y), onDone)
            PrivateLine(stringResource(R.string.warmup_private))
        }
        is WarmupUi.Done -> CtaBar { PrimaryCta(stringResource(R.string.ob_continue), onContinue, arrow = true) }
        WarmupUi.TooShort, WarmupUi.MicError -> CtaBar {
            PrimaryCta(stringResource(R.string.warmup_try_again), onAllowMic, leading = Icons.Rounded.Mic)
            TextCta(stringResource(R.string.warmup_not_now), onNotNow)
        }
        WarmupUi.Ready -> CtaBar {
            PrimaryCta(stringResource(R.string.warmup_allow_mic), onAllowMic, leading = Icons.Rounded.Mic)
            TextCta(stringResource(R.string.warmup_not_now), onNotNow)
        }
    }
}

/** S03 state 5: explains the value, retry, settings, and carry on without the mic (US-002). */
@Composable
fun MicDeniedBody() {
    MessageBlock(
        icon = Icons.Outlined.MicOff,
        title = stringResource(R.string.mic_denied_title),
        body = stringResource(R.string.mic_denied_body),
        items = listOf(
            Icons.Outlined.Speed to stringResource(R.string.mic_denied_pace),
            Icons.Outlined.FavoriteBorder to stringResource(R.string.mic_denied_feedback),
            Icons.Outlined.Replay to stringResource(R.string.mic_denied_again),
        ),
        footer = stringResource(R.string.mic_denied_offline),
    )
}

/** After a second denial ("don't ask again") the primary becomes "Open settings". */
@Composable
fun MicDeniedCta(permanentlyDenied: Boolean, onTryAgain: () -> Unit, onOpenSettings: () -> Unit, onContinueWithout: () -> Unit) {
    CtaBar {
        if (permanentlyDenied) {
            PrimaryCta(stringResource(R.string.mic_open_settings), onOpenSettings, leading = Icons.Outlined.Settings)
        } else {
            PrimaryCta(stringResource(R.string.warmup_try_again), onTryAgain, leading = Icons.Rounded.Mic)
            OutlineCta(stringResource(R.string.mic_open_settings), onOpenSettings, leading = Icons.Outlined.Settings, modifier = Modifier.padding(top = VaakSpace.xs))
        }
        TextCta(stringResource(R.string.mic_continue_without), onContinueWithout)
    }
}

/** A fixed bar shape for the reduced-motion wave. */
private val STATIC_WAVE = listOf(0.3f, 0.5f, 0.7f, 0.45f, 0.6f, 0.35f)
