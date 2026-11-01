package com.vaak.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaak.app.R
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

/** S01 body: logo, promise, three perks and the year-of-birth card (US-001). */
@Composable
fun WelcomeBody(yearText: String, yearValid: Boolean, onYearChange: (String) -> Unit, onContinue: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaakSpace.m)) {
            Box(
                Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("V", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onPrimary)
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-9).dp, y = 9.dp).size(8.dp).clip(CircleShape).background(VaakTheme.colors.accent))
            }
            Column {
                Text(stringResource(R.string.welcome_brand), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.welcome_brand_hi), style = VaakTheme.type.hiBodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 29.sp, lineHeight = 33.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = VaakSpace.l, bottom = VaakSpace.s).semantics { heading() },
        )
        ObLead(stringResource(R.string.welcome_lead))
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Perk(Icons.Outlined.RecordVoiceOver, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, stringResource(R.string.welcome_perk_talk))
            Perk(Icons.Outlined.FavoriteBorder, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary, stringResource(R.string.welcome_perk_feedback))
            Perk(Icons.Outlined.Lock, VaakTheme.colors.accentTint, VaakTheme.colors.accentInk, stringResource(R.string.welcome_perk_private))
        }
        VaakCard(Modifier.fillMaxWidth().padding(top = VaakSpace.l)) {
            Column(Modifier.padding(VaakSpace.l)) {
                Text(
                    stringResource(R.string.age_question),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 10.dp).semantics { heading() },
                )
                Text(
                    stringResource(R.string.age_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                )
                OutlinedTextField(
                    value = yearText,
                    onValueChange = onYearChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.age_placeholder)) },
                    label = null,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (yearValid) onContinue() }),
                    modifier = Modifier.fillMaxWidth().testTag(TAG_YEAR),
                )
                Row(Modifier.padding(top = 6.dp, start = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    Text(stringResource(R.string.age_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Perk(icon: ImageVector, bg: Color, fg: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaakSpace.m)) {
        Box(Modifier.size(34.dp).clip(MaterialTheme.shapes.small).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun WelcomeCta(yearValid: Boolean, onContinue: () -> Unit) {
    CtaBar {
        PrimaryCta(stringResource(R.string.ob_continue), onContinue, enabled = yearValid, arrow = true, modifier = Modifier.testTag(TAG_CONTINUE))
        Text(
            stringResource(R.string.welcome_terms),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = VaakSpace.s),
        )
    }
}

/** S01 state 3: a kind, final stop. Nothing is stored except the local "blocked" flag. */
@Composable
fun BlockedBody() {
    MessageBlock(
        icon = Icons.Outlined.WavingHand,
        title = stringResource(R.string.blocked_title),
        body = stringResource(R.string.blocked_body),
        items = listOf(
            Icons.Outlined.Delete to stringResource(R.string.blocked_nothing_saved),
            Icons.Outlined.Cake to stringResource(R.string.blocked_turn_18),
        ),
        modifier = Modifier.padding(top = VaakSpace.l),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedCta(onClose: () -> Unit) {
    var why by rememberSaveable { mutableStateOf(false) }
    CtaBar {
        OutlineCta(stringResource(R.string.blocked_close), onClose, modifier = Modifier.padding(bottom = VaakSpace.xs))
        TextCta(stringResource(R.string.blocked_why), { why = true })
    }
    if (why) {
        ModalBottomSheet(onDismissRequest = { why = false }) {
            Column(Modifier.padding(start = VaakSpace.gutter, end = VaakSpace.gutter, bottom = VaakSpace.xxl)) {
                Text(stringResource(R.string.blocked_why), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.blocked_why_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = VaakSpace.xs, bottom = VaakSpace.m),
                )
                PrimaryCta(stringResource(R.string.blocked_why_ok), { why = false })
            }
        }
    }
}

const val TAG_YEAR = "ob_year"
const val TAG_CONTINUE = "ob_continue"
