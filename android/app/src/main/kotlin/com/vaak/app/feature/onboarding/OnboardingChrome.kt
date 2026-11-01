package com.vaak.app.feature.onboarding

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaak.app.R
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

/** 56 dp top row: back (or a 48 dp gap) + the 6-segment progress + "n of 6" (S01 to S06 chrome). */
@Composable
fun ObTopBar(
    step: Int?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backLabel: String = stringResource(R.string.ob_back),
) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp).padding(start = VaakSpace.xs, end = VaakSpace.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel, tint = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Spacer(Modifier.width(VaakSpace.touch))
        }
        if (step != null) {
            val total = OnboardingStep.TOTAL
            val a11y = stringResource(R.string.ob_step_a11y, step, total)
            Row(
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = a11y
                    progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 1f..total.toFloat())
                },
                horizontalArrangement = Arrangement.spacedBy(VaakSpace.xs),
            ) {
                for (i in 1..total) {
                    val color = when {
                        i < step -> MaterialTheme.colorScheme.primary
                        i == step -> VaakTheme.colors.accent
                        else -> VaakTheme.colors.track
                    }
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
            Text(
                text = stringResource(R.string.ob_step, step, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
fun ObHeadline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 6.dp, bottom = VaakSpace.s).semantics { heading() },
    )
}

@Composable
fun ObLead(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** A question label with an optional muted note on the right ("Optional", "Pick 1 to 3"). */
@Composable
fun ObQuestion(text: String, modifier: Modifier = Modifier, note: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 22.dp, bottom = VaakSpace.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The sticky CTA bar (design §6): stacked full-width buttons. */
@Composable
fun CtaBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(start = VaakSpace.gutter, end = VaakSpace.gutter, top = VaakSpace.m, bottom = VaakSpace.l),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** 54 dp primary button; [leading] icons are saffron on indigo (`primary-icon`), the arrow trails. */
@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    arrow: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = VaakTheme.colors.track,
            disabledContentColor = VaakTheme.colors.faint,
        ),
    ) {
        CtaContent(text, leading, if (arrow && enabled) Icons.AutoMirrored.Rounded.ArrowForward else null, VaakTheme.colors.primaryIcon)
    }
}

/** The saffron button: only Start talk and Record (design §6). */
@Composable
fun AccentCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VaakTheme.colors.accent, contentColor = VaakTheme.colors.onAccent),
    ) {
        CtaContent(text, leading, null, VaakTheme.colors.onAccent)
    }
}

@Composable
fun OutlineCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = VaakSpace.touch)) {
        CtaContent(text, leading, null, null)
    }
}

@Composable
fun TextCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = VaakSpace.touch)) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowScope.CtaContent(text: String, leading: ImageVector?, trailing: ImageVector?, iconTint: androidx.compose.ui.graphics.Color?) {
    if (leading != null) {
        Icon(leading, contentDescription = null, tint = iconTint ?: androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(VaakSpace.s))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    if (trailing != null) {
        Spacer(Modifier.width(VaakSpace.s))
        Icon(trailing, contentDescription = null, tint = iconTint ?: androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(22.dp))
    }
}

/** Centred message (blocked, declined, denied): 88 dp tinted icon, serif title, one line, a list card. */
@Composable
fun MessageBlock(
    icon: ImageVector,
    title: String,
    body: String,
    items: List<Pair<ImageVector, String>>,
    modifier: Modifier = Modifier,
    footer: String? = null,
) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp).semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = VaakSpace.s),
        )
        if (items.isNotEmpty()) {
            VaakCard(Modifier.fillMaxWidth().padding(top = VaakSpace.xl), shape = com.vaak.ui.theme.TileShape) {
                Column(Modifier.padding(horizontal = VaakSpace.l, vertical = VaakSpace.s)) {
                    items.forEach { (i, t) -> IconLine(i, t) }
                }
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = VaakSpace.m),
            )
        }
    }
}

@Composable
fun IconLine(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** `sunken` info note with a brand icon (design `.info-note`). */
@Composable
fun InfoNote(icon: ImageVector, text: AnnotatedString, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = VaakSpace.m, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(VaakSpace.s),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoNote(icon: ImageVector, text: String, modifier: Modifier = Modifier) = InfoNote(icon, AnnotatedString(text), modifier)

/** A string resource with `<b>` kept as bold. */
@Composable
fun annotatedResource(@StringRes id: Int): AnnotatedString {
    val text = androidx.compose.ui.platform.LocalResources.current.getText(id)
    if (text !is Spanned) return AnnotatedString(text.toString())
    return buildAnnotatedString {
        append(text.toString())
        text.getSpans(0, text.length, StyleSpan::class.java).forEach { span ->
            if (span.style == Typeface.BOLD) {
                addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), text.getSpanStart(span), text.getSpanEnd(span))
            }
        }
    }
}

/** The UI locale, read observably (lint NonObservableLocale). */
@Composable
fun currentLocale(): java.util.Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
