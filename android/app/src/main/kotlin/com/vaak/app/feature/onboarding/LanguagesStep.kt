package com.vaak.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vaak.app.R
import com.vaak.data.prefs.GoalTrack
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

fun GoalTrack.label(): Int = when (this) {
    GoalTrack.PROFESSIONAL -> R.string.track_professional
    GoalTrack.INTERVIEWS -> R.string.track_interviews
    GoalTrack.PUBLIC_SPEAKING -> R.string.track_public_speaking
    GoalTrack.CONTENT_CREATION -> R.string.track_content_creation
    GoalTrack.WORD_POWER -> R.string.track_word_power
    GoalTrack.EVERYDAY -> R.string.track_everyday
}

private fun GoalTrack.icon(): ImageVector = when (this) {
    GoalTrack.PROFESSIONAL -> Icons.Outlined.WorkOutline
    GoalTrack.INTERVIEWS -> Icons.Outlined.Handshake
    GoalTrack.PUBLIC_SPEAKING -> Icons.Outlined.Campaign
    GoalTrack.CONTENT_CREATION -> Icons.Outlined.Videocam
    GoalTrack.WORD_POWER -> Icons.AutoMirrored.Outlined.MenuBook
    GoalTrack.EVERYDAY -> Icons.Outlined.Forum
}

/** S02: optional first name (on the device only, D-021), language, 1 to 3 goals (US-002). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagesBody(
    state: OnboardingUiState,
    onName: (String) -> Unit,
    onLang: (LangChoice) -> Unit,
    onToggleTrack: (GoalTrack) -> Unit,
) {
    Column {
        ObHeadline(stringResource(R.string.profile_title))
        ObLead(stringResource(R.string.profile_lead))
        ObQuestion(stringResource(R.string.profile_name_q), note = stringResource(R.string.ob_optional), modifier = Modifier.padding(top = 0.dp))
        val nameHint = stringResource(R.string.profile_name_hint)
        OutlinedTextField(
            value = state.firstName,
            onValueChange = onName,
            singleLine = true,
            placeholder = { Text(nameHint) },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameHint },
        )

        ObQuestion(stringResource(R.string.profile_lang_q))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(
                LangChoice.EN to stringResource(R.string.lang_en),
                LangChoice.HI to stringResource(R.string.lang_hi),
                LangChoice.BOTH to stringResource(R.string.lang_both),
            )
            options.forEachIndexed { i, (choice, label) ->
                SegmentedButton(
                    selected = state.langChoice == choice,
                    onClick = { onLang(choice) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) {
                    Text(label, style = if (choice == LangChoice.HI) VaakTheme.type.hiBodyMedium else MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(Modifier.padding(top = VaakSpace.s, start = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Text(stringResource(R.string.profile_lang_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val picked = state.tracks.size
        ObQuestion(
            stringResource(R.string.profile_goals_q),
            note = if (picked == 0) stringResource(R.string.profile_goals_pick) else stringResource(R.string.profile_goals_count, picked),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalTrack.entries.forEach { track ->
                val selected = track in state.tracks
                val dim = state.trackLimitHit && !selected
                FilterChip(
                    selected = selected,
                    onClick = { onToggleTrack(track) },
                    label = { Text(stringResource(track.label()), style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Rounded.Check else track.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.alpha(if (dim) 0.6f else 1f),
                )
            }
        }
        if (state.trackLimitHit) {
            Row(
                Modifier.padding(top = VaakSpace.s).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Text(stringResource(R.string.profile_goals_limit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (picked > 0) {
            val firstTalk = if (GoalTrack.INTERVIEWS in state.tracks) R.string.profile_first_talk_interviews else R.string.profile_first_talk_other
            InfoNote(
                Icons.Outlined.Mic,
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(stringResource(R.string.profile_first_talk)) }
                    append(" ")
                    append(stringResource(firstTalk))
                },
                modifier = Modifier.padding(top = VaakSpace.m),
            )
        }
    }
}

@Composable
fun LanguagesCta(enabled: Boolean, onContinue: () -> Unit) {
    CtaBar {
        PrimaryCta(
            text = if (enabled) stringResource(R.string.ob_continue) else stringResource(R.string.profile_pick_goal),
            onClick = onContinue,
            enabled = enabled,
            arrow = true,
        )
    }
}
