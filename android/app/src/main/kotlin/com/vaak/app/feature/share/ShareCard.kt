package com.vaak.app.feature.share

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.core.graphics.toColorInt
import com.vaak.app.R
import com.vaak.app.feature.progress.oneDecimal
import com.vaak.app.feature.progress.twoDecimals
import com.vaak.domain.model.Lang
import com.vaak.domain.share.ShareMetric
import com.vaak.domain.share.ShareMetricKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the share image shows (US-071): baseline vs. latest numbers, an optional streak, an optional first name and
 * the app name. It is built only from metrics, dates and counts, so no transcript text can reach it.
 */
data class ShareCardModel(
    val lang: Lang,
    val from: LocalDate,
    val to: LocalDate,
    val weeks: Int,
    val rows: List<ShareMetric>,
    val streak: Int?,
    val firstName: String?,
)

/** Every string drawn on the card, in order. The preview's description and the tests read the same list. */
data class ShareCardText(
    val eyebrow: String,
    val headline: String,
    val rows: List<Triple<String, String, String>>,
    val streak: String?,
    val brand: String,
    val tagline: String,
) {
    fun all(): List<String> = listOf(eyebrow, headline) + rows.flatMap { listOf(it.first, it.second, it.third) } +
        listOfNotNull(streak) + listOf(brand, tagline)

    companion object {
        /** Hindi practice gets a Devanagari card (S21 state 3); numbers stay Latin digits. */
        fun of(m: ShareCardModel, res: Resources): ShareCardText {
            val hi = m.lang == Lang.HI
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
            val name = m.firstName?.trim()?.takeIf(String::isNotEmpty)
            val headline = when {
                hi && name != null -> res.getQuantityString(R.plurals.share_headline_hi_name, m.weeks, name, m.weeks)
                hi -> res.getQuantityString(R.plurals.share_headline_hi, m.weeks, m.weeks)
                name != null -> res.getQuantityString(R.plurals.share_headline_name, m.weeks, name, m.weeks)
                else -> res.getQuantityString(R.plurals.share_headline, m.weeks, m.weeks)
            }
            val rows = m.rows.map { r ->
                val label = res.getString(
                    when (r.kind) {
                        ShareMetricKind.FILLERS -> if (hi) R.string.share_fillers_hi else R.string.share_fillers
                        ShareMetricKind.PACE -> if (hi) R.string.share_pace_hi else R.string.share_pace
                        ShareMetricKind.VARIETY -> if (hi) R.string.share_variety_hi else R.string.share_variety
                    },
                )
                val f: (Double) -> String = when (r.kind) {
                    ShareMetricKind.FILLERS -> ::oneDecimal
                    ShareMetricKind.PACE -> { v -> Math.round(v).toString() }
                    ShareMetricKind.VARIETY -> ::twoDecimals
                }
                Triple(label, f(r.before), f(r.after))
            }
            return ShareCardText(
                eyebrow = "${m.from.format(fmt)} → ${m.to.format(fmt)}",
                headline = headline,
                rows = rows,
                streak = m.streak?.takeIf { it > 0 }?.let { res.getString(R.string.share_streak, it) },
                brand = res.getString(R.string.app_name),
                tagline = res.getString(R.string.share_tagline),
            )
        }
    }
}

/**
 * Draws the card to a 1080 × 1350 (4:5) bitmap with fixed brand colours, so it looks the same in light and dark
 * (S21 notes). Plain Canvas: no extra dependency.
 */
object ShareCardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    private val HERO = "#2B2766".toColorInt()
    private val ON_HERO = "#FFFFFF".toColorInt()
    private val ON_HERO_2 = "#D9D6F5".toColorInt()
    private val ACCENT = "#F2A531".toColorInt()
    private val ON_ACCENT = "#231A05".toColorInt()
    private const val ROW_FILL = 0x14FFFFFF

    fun render(context: Context, text: ShareCardText, hindi: Boolean): Bitmap {
        val bmp = createBitmap(WIDTH, HEIGHT)
        val c = Canvas(bmp)
        val serif = runCatching { ResourcesCompat.getFont(context, com.vaak.ui.R.font.fraunces_500) }.getOrNull() ?: Typeface.SERIF
        val serifBold = runCatching { ResourcesCompat.getFont(context, com.vaak.ui.R.font.fraunces_600) }.getOrNull() ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        c.drawColor(HERO)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(WIDTH - 120f, 60f, 520f, intArrayOf(0x66F2A531, 0x00F2A531), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(WIDTH - 120f, 60f, 520f, glow)

        val pad = 96f
        var y = 140f
        val eyebrow = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_HERO_2; textSize = 34f; typeface = sansBold; letterSpacing = 0.08f }
        c.drawText(text.eyebrow.uppercase(Locale.UK), pad, y, eyebrow)
        y += 40f

        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_HERO; textSize = if (hindi) 66f else 76f; typeface = if (hindi) sansBold else serif }
        val layout = StaticLayout.Builder.obtain(text.headline, 0, text.headline.length, head, (WIDTH - 2 * pad).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, if (hindi) 1.4f else 1.1f).build()
        c.withTranslation(pad, y) { layout.draw(this) }
        y += layout.height + 64f

        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_HERO_2; textSize = 40f; typeface = if (hindi) sansBold else sans }
        val before = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_HERO_2; textSize = 54f; typeface = serif; textAlign = Paint.Align.RIGHT }
        val after = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_HERO; textSize = 54f; typeface = serifBold; textAlign = Paint.Align.RIGHT }
        val arrow = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; textSize = 44f; typeface = sans; textAlign = Paint.Align.RIGHT }
        val rowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROW_FILL }
        val rowH = 128f
        text.rows.forEach { (k, a, b) ->
            c.drawRoundRect(RectF(pad, y, WIDTH - pad, y + rowH), 36f, 36f, rowFill)
            val base = y + rowH / 2 + 18f
            c.drawText(k, pad + 36f, base, label)
            val right = WIDTH - pad - 36f
            c.drawText(b, right, base, after)
            val bw = after.measureText(b)
            c.drawText("→", right - bw - 16f, base, arrow)
            val aw = arrow.measureText("→")
            c.drawText(a, right - bw - 16f - aw - 16f, base, before)
            y += rowH + 24f
        }

        val footY = HEIGHT - 120f
        text.streak?.let { s ->
            val pill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_ACCENT; textSize = 38f; typeface = sansBold }
            val w = pill.measureText(s) + 72f
            c.drawRoundRect(RectF(pad, footY - 52f, pad + w, footY + 28f), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
            c.drawText(s, pad + 36f, footY + 2f, pill)
        }
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_HERO; textSize = 60f; typeface = serifBold; textAlign = Paint.Align.RIGHT }
        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_HERO_2; textSize = 26f; typeface = sansBold; letterSpacing = 0.12f; textAlign = Paint.Align.RIGHT }
        c.drawText(text.brand, WIDTH - pad, footY - 6f, brand)
        c.drawText(text.tagline.uppercase(Locale.UK), WIDTH - pad, footY + 34f, tag)
        return bmp
    }
}
