package io.github.ukpratik.mukhar.app.feature.talk.common

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.talk.report.CreditUi
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Difficulty
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import java.util.Locale

// Shared talk-flow pieces built from design-system §6 (base.css classes named in each KDoc).

/** `m:ss` for timers, durations and caps. */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@StringRes
fun trackLabel(track: Track): Int = when (track) {
    Track.PROFESSIONAL -> R.string.track_professional
    Track.INTERVIEWS -> R.string.track_interviews
    Track.EVERYDAY -> R.string.track_everyday
    Track.PUBLIC_SPEAKING -> R.string.track_public_speaking
}

@StringRes
fun langLabel(lang: Lang): Int = if (lang == Lang.HI) R.string.lang_hi else R.string.lang_en

@StringRes
fun difficultyLabel(d: Difficulty): Int = when (d) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}

/** Haptics (design-system §8): CONFIRM for start/stop/cap/add (VIRTUAL_KEY below API 30), CLOCK_TICK for markers. */
class Haptics(private val perform: (Int) -> Unit) {
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics { view.performHapticFeedback(it) } }
}

/**
 * `ANIMATOR_DURATION_SCALE == 0` turns off loops and transitions (design-system §8 reduced motion). Observed, so
 * changing the setting while a screen is open takes effect without leaving it.
 */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/**
 * The indeterminate spinner for waits. With reduced motion it is a still hourglass instead of an endless loop
 * (design-system §8). Decorative: the text next to it says what is happening.
 */
@Composable
fun MukharSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    if (reducedMotion()) {
        Icon(Icons.Rounded.HourglassTop, null, tint = color, modifier = modifier.size(size))
    } else {
        CircularProgressIndicator(modifier.size(size), color = color, strokeWidth = strokeWidth, trackColor = trackColor)
    }
}

/** `.appbar`: back + centred serif title + optional action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MukharAppBar(title: String, backDescription: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backDescription) }
        },
        actions = { action() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** `.credit` pill: "Day 13 counted" (accent tint), or the neutral 10–30 s variant. */
@Composable
fun CreditPill(credit: CreditUi, modifier: Modifier = Modifier) {
    val counted = credit.day != null
    val text = when {
        credit.day == null -> stringResource(R.string.credit_short)
        credit.goalMet -> stringResource(R.string.credit_counted_goal, credit.day)
        else -> stringResource(R.string.credit_counted, credit.day)
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (counted) MukharTheme.colors.accentTint else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (counted) MukharTheme.colors.accentInk else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (counted) Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(17.dp))
            if (counted) Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

enum class BannerTone { INFO, WARN, OK }

/** `.banner`: icon + bold line + text, inline (not a Snackbar). Noise and errors are assertive live regions. */
@Composable
fun MukharBanner(
    icon: ImageVector,
    title: String,
    body: String?,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    assertive: Boolean = false,
) {
    val bg = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.primaryContainer
        BannerTone.WARN -> MukharTheme.colors.accentTint
        BannerTone.OK -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val iconTint = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.primary
        BannerTone.WARN -> MukharTheme.colors.accentInk
        BannerTone.OK -> MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** `.info-note`: a sunken one-liner with an icon. */
@Composable
fun InfoNote(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp, 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The tinted icon circle used by `.empty`, `.low` and `.center` states. */
@Composable
fun ArtCircle(icon: ImageVector, size: Dp = 72.dp, iconSize: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize)) }
}

/** `.center` / `.low`: art, serif title, one muted line, then any extra content. */
@Composable
fun CenterState(
    icon: ImageVector?,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    assertive: Boolean = false,
    artSize: Dp = 72.dp,
    leading: (@Composable () -> Unit)? = null,
    /** False when the screen already announces its state through one stable announcer node. */
    live: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(top = 28.dp)
            .then(if (live) Modifier.semantics { liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        leading?.invoke()
        if (icon != null) ArtCircle(icon, artSize, if (artSize > 80.dp) 42.dp else 34.dp)
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp).semantics { heading() },
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
            )
        }
        content()
    }
}

enum class StepState { DONE, NOW, TODO, RETRY }

@StringRes
private fun stepStateLabel(state: StepState): Int = when (state) {
    StepState.DONE -> R.string.a11y_step_done
    StepState.NOW -> R.string.a11y_step_now
    StepState.TODO -> R.string.a11y_step_todo
    StepState.RETRY -> R.string.a11y_step_retry
}

/**
 * `.steps`: the analysing checklist. Each row is one TalkBack stop with its state ("Done", "In progress"…); the
 * current step's row is a polite live region.
 */
@Composable
fun StepsList(steps: List<Pair<String, StepState>>, modifier: Modifier = Modifier, retryIcon: ImageVector? = null) {
    MukharCard(modifier = modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            steps.forEachIndexed { i, (label, state) ->
                val stateText = stringResource(stepStateLabel(state))
                val current = state == StepState.NOW || state == StepState.RETRY
                Row(
                    Modifier.heightIn(min = 48.dp).fillMaxWidth().semantics(mergeDescendants = true) {
                        stateDescription = stateText
                        if (current) liveRegion = LiveRegionMode.Polite
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (state) {
                        StepState.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                        StepState.NOW -> MukharSpinner(size = 20.dp)
                        StepState.TODO -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        StepState.RETRY -> Icon(retryIcon ?: Icons.Rounded.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (state == StepState.DONE || state == StepState.NOW) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (i < steps.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** `.skel`: holds the final height; shimmers unless reduced motion is on. */
@Composable
fun Skeleton(widthFraction: Float, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val still = reducedMotion()
    val base = MukharTheme.colors.track
    val alpha = if (still) {
        1f
    } else {
        val t = rememberInfiniteTransition(label = "shimmer")
        t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "a").value
    }
    Box(
        modifier.padding(vertical = 4.dp).fillMaxWidth(widthFraction).height(height)
            .clip(RoundedCornerShape(8.dp)).background(base.copy(alpha = alpha)),
    )
}

/**
 * Section head `.sec-h`: serif title left, muted label right. The title gives way at large font scales so both
 * wrap instead of clipping; [trailingDescription] replaces what TalkBack reads for the label.
 */
@Composable
fun SectionHead(title: String, trailing: String?, modifier: Modifier = Modifier, trailingDescription: String? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = MukharSpace.xl, bottom = MukharSpace.m, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        if (trailing != null) {
            Text(
                trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp)
                    .then(if (trailingDescription != null) Modifier.semantics { contentDescription = trailingDescription } else Modifier),
            )
        }
    }
}

/** `.chip` (static info). */
@Composable
fun InfoChip(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
