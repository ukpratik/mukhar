package io.github.ukpratik.mukhar.app.feature.talk.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.talk.common.clock
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.metrics.WpmBand
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.text.mixedScript
import io.github.ukpratik.mukhar.ui.theme.NotoDeva
import io.github.ukpratik.mukhar.ui.theme.TileShape
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharTheme

// S11 building blocks (design-system §6: metric tile, pace band, transcript spans, glow/grow card, better-line row).

@Composable
fun metricLabel(kind: MetricKind): String = stringResource(
    when (kind) {
        MetricKind.PACE -> R.string.metric_pace
        MetricKind.FILLERS -> R.string.metric_fillers
        MetricKind.PAUSES -> R.string.metric_pauses
        MetricKind.REPEATS -> R.string.metric_repeats
        MetricKind.VARIETY -> R.string.metric_variety
    },
)

/** `.metrics`: 2 columns; 1 column above 1.5× font scale so tiles never truncate. */
@Composable
fun MetricsGrid(tiles: List<MetricTileUi>, onInfo: (MetricKind) -> Unit, modifier: Modifier = Modifier) {
    val oneColumn = LocalDensity.current.fontScale > 1.5f
    val metricsCd = stringResource(R.string.metrics_cd)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Heading navigation lands here; nothing is drawn.
        Spacer(Modifier.fillMaxWidth().height(1.dp).semantics { heading(); contentDescription = metricsCd })
        val rows = mutableListOf<List<MetricTileUi>>()
        var pending = mutableListOf<MetricTileUi>()
        for (t in tiles) {
            if (t.wide || oneColumn) {
                if (pending.isNotEmpty()) rows += pending.toList().also { pending = mutableListOf() }
                rows += listOf(t)
            } else {
                pending += t
                if (pending.size == 2) rows += pending.toList().also { pending = mutableListOf() }
            }
        }
        if (pending.isNotEmpty()) rows += pending.toList()
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (t in row) MetricTile(t, onInfo, Modifier.weight(1f))
                if (row.size == 1 && !row.first().wide && !oneColumn) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(t: MetricTileUi, onInfo: (MetricKind) -> Unit, modifier: Modifier) {
    val label = metricLabel(t.kind)
    val spoken = metricSpoken(t, label)
    if (t.off) {
        val dash = MukharTheme.colors.lineStrong
        Column(
            modifier
                .clip(TileShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawBehind {
                    drawRoundRect(
                        dash, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.not_scored), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }
    MukharCard(modifier = modifier, shape = TileShape) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 12.dp).semantics(mergeDescendants = true) { contentDescription = spoken }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                // A real 48 dp box: the tile's clip would cut M3's expanded touch area at the card edge (NFR-008).
                IconButton(onClick = { onInfo(t.kind) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Info, stringResource(R.string.about_metric_cd, label), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            MetricBody(t)
        }
    }
}

/**
 * What TalkBack reads for a tile ("Pace. 142 words per minute. Comfortable band."): the tile's description
 * replaces its separate texts, and the ⓘ button stays its own stop.
 */
@Composable
private fun metricSpoken(t: MetricTileUi, label: String): String {
    if (t.off) return stringResource(R.string.a11y_metric_pair, label, stringResource(R.string.not_scored))
    if (t.locked) return stringResource(R.string.a11y_metric_pair, label, stringResource(R.string.variety_locked))
    val unit = when (t.unit) {
        MetricUnit.WPM -> stringResource(R.string.a11y_unit_wpm)
        null -> null
        else -> stringResource(R.string.a11y_unit_per_min)
    }
    val value = listOfNotNull(t.value, unit).joinToString(" ")
    val delta = t.delta?.let { deltaContent(it).second }
    val better = if (t.good && t.delta != null && t.delta !is MetricDelta.Band && t.delta != MetricDelta.HindiProvisional) {
        stringResource(R.string.a11y_metric_better)
    } else {
        null
    }
    return listOfNotNull(label, value.ifEmpty { null }, delta, better).joinToString(". ")
}

@Composable
private fun MetricBody(t: MetricTileUi) {
            if (t.locked) {
                Text(stringResource(R.string.variety_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(t.value.orEmpty(), style = MukharTheme.type.metric, color = MaterialTheme.colorScheme.onSurface)
                    t.unit?.let {
                        Text(
                            stringResource(if (it == MetricUnit.WPM) R.string.unit_wpm else R.string.unit_per_min),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            t.bandPosition?.let { PaceBand(it, Modifier.padding(top = 8.dp, end = 10.dp)) }
            t.delta?.let { DeltaRow(it, t.good) }
}

/** `.band`: slow · comfortable · fast with an indigo marker (custom Canvas). */
@Composable
fun PaceBand(position: Float, modifier: Modifier = Modifier) {
    val track = MukharTheme.colors.track
    val ok = MukharTheme.colors.bandOk
    val marker = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, h), r)
        drawRect(ok, Offset(size.width * 0.25f, top), Size(size.width * (0.667f - 0.25f), h))
        val x = size.width * position
        drawRoundRect(marker, Offset(x - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height), CornerRadius(3.dp.toPx()))
    }
}

@Composable
private fun DeltaRow(delta: MetricDelta, good: Boolean) {
    val (icon, text) = deltaContent(delta)
    val color = if (good) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(top = 3.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = color)
    }
}

@Composable
private fun deltaContent(delta: MetricDelta): Pair<ImageVector, String> {
    return when (delta) {
        is MetricDelta.Band -> {
            val band = stringResource(
                when (delta.band) {
                    WpmBand.SLOW -> R.string.band_slow
                    WpmBand.COMFORTABLE -> R.string.band_comfortable
                    WpmBand.FAST -> R.string.band_fast
                },
            )
            val icon = if (delta.band == WpmBand.COMFORTABLE) Icons.Rounded.CheckCircle else if (delta.band == WpmBand.FAST) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward
            icon to if (delta.provisional) stringResource(R.string.band_provisional, band) else stringResource(R.string.band_label, band)
        }
        is MetricDelta.Down -> Icons.Rounded.ArrowDownward to stringResource(R.string.delta_down_avg, delta.from)
        is MetricDelta.Up -> Icons.Rounded.ArrowUpward to stringResource(R.string.delta_up_avg, delta.from)
        MetricDelta.Same -> Icons.Rounded.HorizontalRule to stringResource(R.string.delta_same)
        is MetricDelta.VarietyUp -> Icons.Rounded.ArrowUpward to stringResource(R.string.delta_variety_up, delta.from)
        is MetricDelta.VarietyDown -> Icons.Rounded.ArrowDownward to stringResource(R.string.delta_variety_down, delta.from)
        MetricDelta.HindiProvisional -> Icons.Rounded.Science to stringResource(R.string.variety_hi)
    }
}

/** Body text style by the talk's language (design-system §9.2: Hindi content uses the `hi*` styles). */
@Composable
fun transcriptStyle(lang: Lang): TextStyle = if (lang == Lang.HI) MukharTheme.type.hiBodyLarge else MaterialTheme.typography.bodyLarge

@Composable
fun feedbackStyle(lang: Lang): TextStyle = if (lang == Lang.HI) MukharTheme.type.hiBodyMedium else MaterialTheme.typography.bodyMedium

/** The superscript "?" after an unsure word: the non-colour marker (design-system §10.3). */
private val UnsureMark = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em, fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold)

/**
 * Transcript paragraph as plain `SpanStyle`s (R1): filler = accent tint + bold + solid underline in accent-ink,
 * repeat = brand-2 + underline, unsure = faint + a superscript "?" (+ italic for Latin), glow quote = green
 * highlight, grow quote = indigo highlight + underline. Spans are whole tokens, so nukta and ZWJ clusters are
 * never split.
 */
@Composable
fun transcriptText(tokens: List<TokenUi>, range: IntRange, lang: Lang): AnnotatedString {
    val accentTint = MukharTheme.colors.accentTint
    val accentInk = MukharTheme.colors.accentInk
    val faint = MukharTheme.colors.faint
    val glowMark = MukharTheme.colors.glowMark
    val growMark = MaterialTheme.colorScheme.primaryContainer
    val repeat = MaterialTheme.colorScheme.secondary
    return remember(tokens, range, lang, accentTint, faint) {
        buildAnnotatedString {
            for (i in range) {
                val t = tokens[i]
                val inGlow = t.glow && i > range.first && tokens[i - 1].glow
                val inGrow = t.grow && i > range.first && tokens[i - 1].grow
                if (i > range.first) {
                    // Keep the quote highlight continuous across the space between two quoted words.
                    when {
                        inGlow -> withStyle(SpanStyle(background = glowMark)) { append(' ') }
                        inGrow -> withStyle(SpanStyle(background = growMark, textDecoration = TextDecoration.Underline)) { append(' ') }
                        else -> append(' ')
                    }
                }
                var style = SpanStyle()
                if (t.glow) style = style.merge(SpanStyle(background = glowMark))
                if (t.grow) style = style.merge(SpanStyle(background = growMark, textDecoration = TextDecoration.Underline))
                if (t.unsure) style = style.merge(SpanStyle(color = faint, fontStyle = if (lang == Lang.HI) FontStyle.Normal else FontStyle.Italic))
                if (t.repeat) style = style.merge(SpanStyle(color = repeat, textDecoration = TextDecoration.Underline))
                if (t.filler) {
                    style = style.merge(SpanStyle(background = accentTint, color = accentInk, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
                }
                withStyle(style) {
                    append(t.text)
                    if (t.unsure) withStyle(UnsureMark) { append('?') }
                }
            }
        }
    }
}

/** The words TalkBack reads in place of the colours: "(filler)", "(repeat)", "(unsure)", and where a quote starts. */
data class TranscriptMarks(val filler: String, val repeat: String, val unsure: String, val glow: String, val grow: String)

@Composable
fun transcriptMarks() = TranscriptMarks(
    filler = stringResource(R.string.a11y_tx_filler),
    repeat = stringResource(R.string.a11y_tx_repeat),
    unsure = stringResource(R.string.a11y_tx_unsure),
    glow = stringResource(R.string.a11y_tx_glow),
    grow = stringResource(R.string.a11y_tx_grow),
)

/** Spoken transcript paragraph; Devanagari runs carry `localeList = hi` so TalkBack switches to a Hindi voice. */
fun transcriptA11yText(tokens: List<TokenUi>, range: IntRange, lang: Lang, marks: TranscriptMarks): AnnotatedString = buildAnnotatedString {
    for (i in range) {
        val t = tokens[i]
        if (i > range.first) append(' ')
        if (t.glow && (i == range.first || !tokens[i - 1].glow)) append(marks.glow).also { append(' ') }
        if (t.grow && (i == range.first || !tokens[i - 1].grow)) append(marks.grow).also { append(' ') }
        if (lang == Lang.HI) append(mixedScript(t.text)) else append(t.text)
        if (t.filler) append(' ').append(marks.filler)
        if (t.repeat) append(' ').append(marks.repeat)
        if (t.unsure) append(' ').append(marks.unsure)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptLegend(t: TranscriptUi, lang: Lang) {
    val accentTint = MukharTheme.colors.accentTint
    val accentInk = MukharTheme.colors.accentInk
    @Composable
    fun item(sample: AnnotatedString, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(sample, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    FlowRow(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (t.hasFiller) {
            val sample = stringResource(if (lang == Lang.HI) R.string.legend_sample_filler_hi else R.string.legend_sample_filler)
            item(AnnotatedString(sample, SpanStyle(background = accentTint, color = accentInk, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)), stringResource(R.string.legend_filler))
        }
        if (t.hasRepeat) item(AnnotatedString(stringResource(R.string.legend_sample_repeat), SpanStyle(color = MaterialTheme.colorScheme.secondary, textDecoration = TextDecoration.Underline)), stringResource(R.string.legend_repeat))
        if (t.hasUnsure) {
            val faint = MukharTheme.colors.faint
            val sample = stringResource(R.string.legend_sample_unsure)
            val unsure = buildAnnotatedString {
                withStyle(SpanStyle(color = faint, fontStyle = if (lang == Lang.HI) FontStyle.Normal else FontStyle.Italic)) {
                    append(sample)
                    withStyle(UnsureMark) { append('?') }
                }
            }
            item(unsure, stringResource(R.string.legend_unsure))
        }
        if (t.hasGlow) item(AnnotatedString(stringResource(R.string.legend_sample_quote), SpanStyle(background = MukharTheme.colors.glowMark)), stringResource(R.string.legend_glow))
        if (t.hasGrow) {
            item(AnnotatedString(stringResource(R.string.legend_sample_quote), SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, textDecoration = TextDecoration.Underline)), stringResource(R.string.legend_grow))
        }
    }
}

@Composable
fun TranscriptCard(t: TranscriptUi, lang: Lang, expanded: Boolean, modifier: Modifier = Modifier) {
    val visible = if (expanded) t.paragraphs else t.paragraphs.take(ReportMapper.COLLAPSED_PARAGRAPHS)
    val marks = transcriptMarks()
    MukharCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            for (p in visible) {
                val spoken = remember(t.tokens, p, lang, marks) { transcriptA11yText(t.tokens, p, lang, marks) }
                Text(
                    transcriptText(t.tokens, p, lang), style = transcriptStyle(lang), color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp).clearAndSetSemantics { text = spoken },
                )
            }
        }
    }
}

fun transcriptRange(t: TranscriptUi, expanded: Boolean): Pair<String, String> {
    val count = if (expanded) t.paragraphs.size else minOf(t.paragraphs.size, ReportMapper.COLLAPSED_PARAGRAPHS)
    if (count == 0) return clock(0) to clock(0)
    return clock(t.startsMs.first()) to clock(t.endsMs[count - 1])
}

/** `.tag` pill on a glow or grow card; Hindi talks get a Devanagari subtitle (section chrome stays English in R1). */
@Composable
private fun FeedbackTag(icon: ImageVector, english: String, short: String, hindi: String, lang: Lang, container: Color, content: Color) {
    val spoken = if (lang == Lang.HI) {
        buildAnnotatedString {
            append("$short · ")
            append(mixedScript(hindi))
        }
    } else {
        AnnotatedString(english)
    }
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.clearAndSetSemantics { heading(); text = spoken }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            val tagStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.72.sp)
            if (lang == Lang.HI) {
                Text("${short.uppercase()} · ", style = tagStyle)
                Text(mixedScript(hindi), style = tagStyle.copy(fontFamily = NotoDeva, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp))
            } else {
                Text(english.uppercase(), style = tagStyle)
            }
        }
    }
}

@Composable
private fun Quote(text: String, lang: Lang, onClick: (() -> Unit)?) {
    val line = MukharTheme.colors.line
    val style = if (lang == Lang.HI) MukharTheme.type.hiBodyMedium.copy(fontSize = 16.sp) else MukharTheme.type.quote
    val showInTranscript = stringResource(R.string.a11y_show_in_transcript)
    val quoted = "“${text.trim().trimEnd(',')}”"
    Text(
        if (lang == Lang.HI) mixedScript(quoted) else AnnotatedString(quoted),
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = 4.dp, bottom = 2.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = showInTranscript, role = Role.Button, onClick = onClick) else Modifier)
            // The tap area includes the spacing around the quote, so it is at least 48 dp tall.
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp)
            .drawBehind { drawRect(line, size = Size(2.dp.toPx(), size.height)) }
            .padding(start = 12.dp),
    )
}

/** A feedback card with the 5 dp left stripe (`.fb`). */
@Composable
private fun StripeCard(stripe: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    MukharCard(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp)) {
        Box(Modifier.drawBehind { drawRect(stripe, size = Size(5.dp.toPx(), size.height)) }.padding(16.dp)) {
            Column { content() }
        }
    }
}

@Composable
fun GlowCard(g: GlowUi, lang: Lang, onQuoteTap: () -> Unit) {
    StripeCard(MaterialTheme.colorScheme.tertiary) {
        FeedbackTag(
            Icons.Rounded.Star, stringResource(R.string.glow_tag), stringResource(R.string.glow_tag_short), stringResource(R.string.glow_tag_hi), lang,
            MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary,
        )
        if (g.quote != null) Quote(g.quote, lang, onQuoteTap) else Spacer(Modifier.height(10.dp))
        Text(g.note, style = feedbackStyle(lang), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Grow card: tag, quote, then a Why/How grid; a quoted line inside "How" gets the `.try` highlight. */
@Composable
fun GrowCard(g: GrowUi, lang: Lang, onQuoteTap: () -> Unit, tagOverride: String? = null) {
    StripeCard(MaterialTheme.colorScheme.primary) {
        if (tagOverride != null) {
            Surface(
                shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clearAndSetSemantics { heading(); text = AnnotatedString(tagOverride) },
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NorthEast, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tagOverride.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
            Text(tryText(g.how), style = feedbackStyle(lang), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp))
            return@StripeCard
        }
        FeedbackTag(
            Icons.Rounded.NorthEast, stringResource(R.string.grow_tag), stringResource(R.string.grow_tag_short), stringResource(R.string.grow_tag_hi), lang,
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary,
        )
        if (g.quote != null) Quote(g.quote, lang, onQuoteTap) else Spacer(Modifier.height(10.dp))
        val labelStyle = if (lang == Lang.HI) {
            MaterialTheme.typography.labelMedium.copy(fontFamily = NotoDeva, fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.72.sp)
        }
        val stack = LocalDensity.current.fontScale > 1.5f
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WhyHowRow(stringResource(if (lang == Lang.HI) R.string.why_hi else R.string.why), lang, labelStyle, stack) {
                Text(g.why, style = feedbackStyle(lang), color = MaterialTheme.colorScheme.onSurface)
            }
            WhyHowRow(stringResource(if (lang == Lang.HI) R.string.how_hi else R.string.how), lang, labelStyle, stack) {
                Text(tryText(g.how), style = feedbackStyle(lang), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** One Why/How line read as one TalkBack stop; the label stacks above the text past 1.5× font scale. */
@Composable
private fun WhyHowRow(label: String, lang: Lang, labelStyle: TextStyle, stack: Boolean, body: @Composable () -> Unit) {
    val shown = if (lang == Lang.HI) mixedScript(label) else AnnotatedString(label.uppercase())
    val labelText = @Composable {
        Text(
            shown, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (stack) Modifier else Modifier.widthIn(min = 44.dp).padding(top = 3.dp, end = 4.dp),
        )
    }
    if (stack) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            labelText()
            body()
        }
    } else {
        Row(Modifier.semantics(mergeDescendants = true) {}) {
            labelText()
            body()
        }
    }
}

private val QUOTED = Regex("[\"“]([^\"”]+)[\"”]")

/** Highlights a quoted example line (`.try`: brand tint, brand text, bold). */
@Composable
private fun tryText(text: String): AnnotatedString {
    val tint = MaterialTheme.colorScheme.primaryContainer
    val brand = MaterialTheme.colorScheme.primary
    return remember(text, tint) {
        buildAnnotatedString {
            var last = 0
            for (m in QUOTED.findAll(text)) {
                append(text.substring(last, m.range.first))
                withStyle(SpanStyle(background = tint, color = brand, fontWeight = FontWeight.Bold)) { append(m.value) }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

/** `.bv`: was (muted, quote icon) → now (semibold) with tappable upgraded words and a Save line toggle. */
@Composable
fun BetterRow(
    b: BetterUi,
    lang: Lang,
    collected: (UpgradedWord) -> Boolean,
    saved: Boolean,
    onWord: (UpgradedWord) -> Unit,
    onSave: () -> Unit,
) {
    val tint = MukharTheme.colors.accentTint
    val ink = MukharTheme.colors.accentInk
    val brand = MaterialTheme.colorScheme.primary
    val onBrand = MaterialTheme.colorScheme.onPrimary
    val now = buildAnnotatedString {
        val rest = b.rewrite
        var cursor = 0
        val placed = mutableListOf<Pair<IntRange, UpgradedWord>>()
        for (w in b.words) {
            val at = rest.indexOf(w.word, ignoreCase = true)
            if (at >= 0) placed += (at until at + w.word.length) to w
        }
        placed.sortBy { it.first.first }
        for ((range, w) in placed) {
            if (range.first < cursor) continue
            append(rest.substring(cursor, range.first))
            val added = collected(w)
            val style = if (added) SpanStyle(background = brand, color = onBrand) else SpanStyle(background = tint, color = ink)
            withLink(LinkAnnotation.Clickable(w.word, TextLinkStyles(style)) { onWord(w) }) {
                append(rest.substring(range.first, range.last + 1))
                append(if (added) " ✓" else " +")
            }
            cursor = range.last + 1
        }
        append(rest.substring(cursor))
    }
    val unplaced = b.words.filter { b.rewrite.indexOf(it.word, ignoreCase = true) < 0 }
    val deva = lang == Lang.HI
    val wasSpoken = mixedScript(stringResource(R.string.a11y_you_said, b.quote.trim().trimEnd(',')))
    val nowSpoken = mixedScript(stringResource(R.string.a11y_try_instead, b.rewrite))
    val wordActions = b.words.map { w ->
        val label = stringResource(if (collected(w)) R.string.added_word_cd else R.string.add_word_cd, w.word)
        CustomAccessibilityAction(label) { onWord(w); true }
    }
    MukharCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = TileShape) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(Modifier.clearAndSetSemantics { text = wasSpoken }) {
                Icon(Icons.Rounded.FormatQuote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    b.quote.trim().trimEnd(','),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp, lineHeight = if (deva) 24.sp else 21.sp, fontFamily = if (deva) NotoDeva else MaterialTheme.typography.bodySmall.fontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                now,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = if (deva) 31.sp else 29.sp, fontFamily = if (deva) NotoDeva else MaterialTheme.typography.bodyLarge.fontFamily),
                color = MaterialTheme.colorScheme.onSurface,
                // Upgraded words are custom actions here, so the TalkBack actions menu adds them one by one.
                modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { text = nowSpoken; customActions = wordActions },
            )
            for (w in unplaced) {
                val added = collected(w)
                val cd = stringResource(if (added) R.string.added_word_cd else R.string.add_word_cd, w.word)
                TextButton(
                    onClick = { onWord(w) },
                    modifier = Modifier.semantics { contentDescription = cd; toggleableState = ToggleableState(added) },
                ) { Text(w.word + if (added) " ✓" else " +") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val color = if (saved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                Row(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape)
                        .toggleable(value = saved, role = Role.Switch, onValueChange = { onSave() })
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (saved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(if (saved) R.string.saved_line else R.string.save_line), style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}

@Composable
fun betterCount(count: Int): String = pluralStringResource(R.plurals.better_count, count, count)

/** `.rate`: three 48 dp radio buttons (US-016). */
@Composable
fun RatingRow(selected: Int?, onRate: (Int) -> Unit) {
    MukharCard(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), shape = TileShape) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rate_question), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            val cd = stringResource(R.string.rate_cd)
            Row(Modifier.selectableGroup().semantics { contentDescription = cd }) {
                RateButton(1, selected, Icons.Outlined.ThumbDown, Icons.Rounded.ThumbDown, stringResource(R.string.rate_no), onRate)
                RateButton(2, selected, Icons.Outlined.ThumbUp, Icons.Rounded.ThumbUp, stringResource(R.string.rate_yes), onRate)
                RateButton(3, selected, Icons.Rounded.StarBorder, Icons.Rounded.Star, stringResource(R.string.rate_great), onRate)
            }
        }
    }
}

@Composable
private fun RateButton(value: Int, selected: Int?, icon: ImageVector, filled: ImageVector, label: String, onRate: (Int) -> Unit) {
    val on = selected == value
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = on, role = Role.RadioButton, onClick = { onRate(value) })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (on) filled else icon, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ReportLink(onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Flag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.report_link), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Report reasons (US-016) and their API keys. */
enum class ReportReason(val key: String, val label: Int) {
    TRANSCRIPT("wrong_transcript", R.string.reason_transcript),
    ADVICE("wrong_advice", R.string.reason_advice),
    OFFENSIVE("offensive", R.string.reason_offensive),
    OTHER("other", R.string.reason_other),
}

/** S11 state 4: reasons, optional note, consent (default off), send. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(onDismiss: () -> Unit, onSend: (ReportReason, Boolean, String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
            Text(stringResource(R.string.report_sheet_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.report_lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            val reasonCd = stringResource(R.string.report_reason_cd)
            Column(Modifier.selectableGroup().semantics { contentDescription = reasonCd }) {
                for (r in ReportReason.entries) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = reason == r, role = Role.RadioButton, onClick = { reason = r }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(r.label), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                placeholder = { Text(stringResource(R.string.report_note)) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                minLines = 2,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .toggleable(value = consent, role = Role.Checkbox, onValueChange = { consent = it })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Checkbox(checked = consent, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.report_consent_bold)) }
                        append(' ')
                        append(stringResource(R.string.report_consent_rest))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { reason?.let { onSend(it, consent, note) } },
                enabled = reason != null,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 54.dp),
            ) { Text(stringResource(R.string.send_report)) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** The metric ⓘ explainer: one line plus the own-average rule (US-013). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricInfoSheet(kind: MetricKind, onDismiss: () -> Unit) {
    val text = stringResource(
        when (kind) {
            MetricKind.PACE -> R.string.info_pace
            MetricKind.FILLERS -> R.string.info_fillers
            MetricKind.PAUSES -> R.string.info_pauses
            MetricKind.REPEATS -> R.string.info_repeats
            MetricKind.VARIETY -> R.string.info_variety
        },
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
            Text(metricLabel(kind), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.info_compare), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.close)) }
        }
    }
}

/** A dashed-border card (`.retry`). */
@Composable
fun DashedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dash = MukharTheme.colors.lineStrong
    Box(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .drawBehind {
                drawRoundRect(dash, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))), cornerRadius = CornerRadius(22.dp.toPx()))
            }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
