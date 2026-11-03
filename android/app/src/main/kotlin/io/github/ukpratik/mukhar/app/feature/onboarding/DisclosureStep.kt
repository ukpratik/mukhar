package io.github.ukpratik.mukhar.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.ui.theme.MukharSpace

/** S04, Play's prominent disclosure: what is sent, to whom, why, and that nothing is stored on our server. */
@Composable
fun DisclosureBody(analyticsOptIn: Boolean, onAnalytics: (Boolean) -> Unit, onOpenPolicy: () -> Unit = {}) {
    Column {
        Eyebrow(stringResource(R.string.disclosure_eyebrow))
        ObHeadline(stringResource(R.string.disclosure_title))
        ObLead(stringResource(R.string.disclosure_lead))
        Column(Modifier.padding(top = MukharSpace.l), verticalArrangement = Arrangement.spacedBy(MukharSpace.l)) {
            FlowItem(
                Icons.Outlined.GraphicEq, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary,
                stringResource(R.string.disclosure_audio_title), stringResource(R.string.disclosure_audio_body),
                Icons.Outlined.Mic to stringResource(R.string.disclosure_audio_what),
            )
            FlowItem(
                Icons.AutoMirrored.Outlined.Notes, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary,
                stringResource(R.string.disclosure_ai_title), stringResource(R.string.disclosure_ai_body),
                Icons.Outlined.Description to stringResource(R.string.disclosure_ai_what),
            )
            FlowItem(
                Icons.Outlined.CloudOff, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary,
                stringResource(R.string.disclosure_server_title), stringResource(R.string.disclosure_server_body), null,
            )
        }
        AnalyticsSwitchRow(analyticsOptIn, onAnalytics, Modifier.padding(top = MukharSpace.l))
        // Looks like a link, so it is one: opens the privacy policy in the browser.
        Row(
            Modifier.padding(top = MukharSpace.xs).fillMaxWidth().heightIn(min = MukharSpace.touch)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.disclosure_policy_open), onClick = onOpenPolicy)
                .testTag(TAG_POLICY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MukharSpace.xs),
        ) {
            Text(
                stringResource(R.string.disclosure_policy),
                style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FlowItem(
    icon: ImageVector,
    bg: Color,
    fg: Color,
    title: String,
    body: String,
    what: Pair<ImageVector, String>?,
) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            if (what != null) {
                Row(
                    Modifier.padding(top = 6.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = MukharSpace.s, vertical = MukharSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MukharSpace.xs),
                ) {
                    Icon(what.first, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Text(what.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The optional usage and crash-reports switch: OFF by default, independent of Agree (D-026, D-028). */
@Composable
fun AnalyticsSwitchRow(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    // The label and the body are both read: the body says what is shared (consent copy is never hidden).
    val a11y = stringResource(R.string.a11y_label_detail, stringResource(R.string.disclosure_analytics_a11y), stringResource(R.string.disclosure_analytics_body))
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = a11y }
            .padding(horizontal = 14.dp, vertical = MukharSpace.m)
            .testTag(TAG_ANALYTICS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MukharSpace.m),
    ) {
        Icon(Icons.Outlined.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.disclosure_analytics_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.disclosure_analytics_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun DisclosureCta(onAgree: () -> Unit, onNotNow: () -> Unit) {
    CtaBar {
        PrimaryCta(stringResource(R.string.disclosure_agree), onAgree, modifier = Modifier.testTag(TAG_AGREE))
        TextCta(stringResource(R.string.disclosure_not_now), onNotNow)
    }
}

/** S04 state 2: respected without guilt; talks stay off, offline features work. */
@Composable
fun DeclinedBody() {
    MessageBlock(
        icon = Icons.Outlined.VerifiedUser,
        title = stringResource(R.string.declined_title),
        body = stringResource(R.string.declined_body),
        items = listOf(
            Icons.Outlined.FitnessCenter to stringResource(R.string.declined_offline),
            Icons.Outlined.LocalFireDepartment to stringResource(R.string.declined_streak),
            Icons.Outlined.ToggleOn to stringResource(R.string.declined_turn_on),
        ),
    )
}

@Composable
fun DeclinedCta(onContinue: () -> Unit, onChangedMind: () -> Unit) {
    CtaBar {
        PrimaryCta(stringResource(R.string.ob_continue), onContinue, arrow = true)
        TextCta(stringResource(R.string.declined_changed_mind), onChangedMind)
    }
}

const val TAG_ANALYTICS = "disclosure_analytics"
const val TAG_AGREE = "disclosure_agree"
const val TAG_POLICY = "disclosure_policy"
