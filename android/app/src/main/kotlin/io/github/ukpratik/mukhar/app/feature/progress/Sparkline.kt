package io.github.ukpratik.mukhar.app.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.ukpratik.mukhar.ui.theme.MukharTheme

/**
 * `.spark` (S16): a line with a soft area under it and a dot on the latest value, drawn with Canvas (no chart
 * library). Weeks without a talk are gaps the line steps over. Decorative: the tile carries a sentence summary.
 */
@Composable
fun Sparkline(points: List<Double?>, modifier: Modifier = Modifier, fixedMin: Double? = null, fixedMax: Double? = null) {
    val line = MaterialTheme.colorScheme.secondary
    val area = MaterialTheme.colorScheme.primaryContainer
    val dot = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val values = points.withIndex().filter { it.value != null }.map { it.index to it.value!! }
        if (values.isEmpty()) return@Canvas
        val min = fixedMin ?: values.minOf { it.second }
        val max = fixedMax ?: values.maxOf { it.second }
        val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val pad = 4.dp.toPx()
        val h = size.height - 2 * pad
        val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
        fun xy(i: Int, v: Double): Offset {
            val x = if (points.size > 1) i * stepX else size.width / 2
            // A flat line sits in the middle rather than on the floor.
            val y = if (max - min < 1e-9) size.height / 2 else pad + (h * (1 - (v - min) / span)).toFloat()
            return Offset(x, y)
        }
        val pts = values.map { (i, v) -> xy(i, v) }
        val stroke = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            addPath(stroke)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(fill, area)
        drawPath(stroke, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(dot, radius = 3.dp.toPx(), center = pts.last())
    }
}

/**
 * "Talks a week" bars with a dashed goal line. Weeks that met the goal are glow-coloured and also taller than the
 * goal line, so colour isn't the only signal (S16 notes).
 */
@Composable
fun WeekBars(counts: List<Int>, met: List<Boolean>, goal: Int, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.secondary
    val good = MaterialTheme.colorScheme.tertiary
    val goalLine = MukharTheme.colors.lineStrong
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        if (counts.isEmpty()) return@Canvas
        val top = maxOf(counts.max(), goal, 1).toFloat()
        val slot = size.width / counts.size
        val w = (slot * BAR_FRACTION).coerceAtMost(18.dp.toPx())
        counts.forEachIndexed { i, c ->
            val barH = size.height * (c / top)
            if (c > 0) {
                drawRoundRect(
                    color = if (met.getOrElse(i) { false }) good else bar,
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - barH),
                    size = Size(w, barH),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        val gy = size.height * (1 - goal / top)
        drawLine(
            goalLine,
            Offset(0f, gy),
            Offset(size.width, gy),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
    }
}

private const val BAR_FRACTION = 0.7f
