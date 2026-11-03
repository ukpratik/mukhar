package com.vaak.app.feature.habit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaak.app.R
import com.vaak.app.feature.onboarding.CappedFontScale
import com.vaak.app.feature.today.DayDot
import com.vaak.ui.theme.VaakTheme

/**
 * The weekly ring (design `.ring`): 76 dp, 8 dp stroke; turns `glow` when the goal is met. Never red.
 * Silent by default (the Today card reads it in its sentence); with [describe] it is a progress bar for TalkBack.
 */
@Composable
fun StreakRing(covered: Int, goal: Int, met: Boolean, modifier: Modifier = Modifier, describe: Boolean = false) {
    val track = MaterialTheme.colorScheme.primaryContainer
    val arc = if (met) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val state = pluralStringResource(R.plurals.streak_ring_state, goal, covered, goal)
    val semantics = if (describe) {
        Modifier.clearAndSetSemantics {
            stateDescription = state
            progressBarRangeInfo = ProgressBarRangeInfo(covered.coerceIn(0, goal).toFloat(), 0f..goal.toFloat(), steps = (goal - 1).coerceAtLeast(0))
        }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(modifier.size(76.dp).testTag(TAG_STREAK_RING).then(semantics), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            val sweep = 360f * (covered.toFloat() / goal).coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(arc, -90f, sweep, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // The ring is a fixed 76 dp graphic: its label scales to 130% at most (the same facts are read out in full).
        CappedFontScale {
            RingLabel(covered, goal)
        }
    }
}

@Composable
private fun RingLabel(covered: Int, goal: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$covered/$goal",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.streak_week).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Mon–Sun dots: done (indigo ✓), today (dashed accent-ink), frozen (❄ tint), empty. */
@Composable
fun WeekDots(dots: List<DayDot>, modifier: Modifier = Modifier) {
    val initials = stringResource(R.string.day_initials)
    Row(modifier.padding(top = 10.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dots.forEachIndexed { i, dot ->
            Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Dot(dot)
                Text(
                    initials.getOrElse(i) { ' ' }.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    color = if (dot == DayDot.TODAY || dot == DayDot.TODAY_DONE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun Dot(dot: DayDot) {
    val brand = MaterialTheme.colorScheme.primary
    when (dot) {
        DayDot.DONE, DayDot.TODAY_DONE -> Box(Modifier.size(22.dp).clip(CircleShape).background(brand), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
        DayDot.FROZEN -> Box(
            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(13.dp))
        }
        DayDot.TODAY -> {
            val ink = VaakTheme.colors.accentInk
            Canvas(Modifier.size(22.dp)) {
                val w = 2.dp.toPx()
                drawCircle(ink, radius = size.minDimension / 2 - w / 2, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            }
        }
        DayDot.EMPTY -> Box(Modifier.size(22.dp).border(1.5.dp, VaakTheme.colors.lineStrong, CircleShape))
    }
}

const val TAG_STREAK_RING = "streak_ring"
