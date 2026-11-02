package com.vaak.app.feature.talk.prompt

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.talk.common.InfoChip
import com.vaak.app.feature.talk.common.difficultyLabel
import com.vaak.app.feature.talk.common.rememberHaptics
import com.vaak.app.feature.talk.common.trackLabel
import com.vaak.app.feature.soundlab.SoundLabEntryCard // M6
import com.vaak.domain.model.Lang
import com.vaak.domain.prompt.Prompt
import com.vaak.ui.theme.NotoDeva
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

/** S08 Prompt picker (the Practice tab, US-010). Today's Talk skips this screen (D-020). */
@Composable
fun PromptScreen(onRecord: (Prompt, String) -> Unit, onOpenSoundLab: (() -> Unit)? = null, vm: PromptViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    PromptContent(
        state = state,
        onEvent = { e ->
            if (e == PromptEvent.Shuffle) haptics.tick()
            vm.onEvent(e)
        },
        onRecord = { state.prompt?.let { onRecord(it, state.track.key) } },
        onOpenSoundLab = onOpenSoundLab,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptContent(state: PromptUiState, onEvent: (PromptEvent) -> Unit, onRecord: () -> Unit, onOpenSoundLab: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).padding(top = 18.dp, bottom = 24.dp),
    ) {
        Text(stringResource(R.string.studio_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.studio_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))

        if (state.langs.size > 1) {
            val cd = stringResource(R.string.studio_lang_cd)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = cd }) {
                state.langs.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = state.lang == l,
                        onClick = { onEvent(PromptEvent.SelectLang(l)) },
                        shape = SegmentedButtonDefaults.itemShape(i, state.langs.size),
                    ) {
                        Text(stringResource(if (l == Lang.HI) R.string.lang_hi_native else R.string.lang_en), fontFamily = if (l == Lang.HI) NotoDeva else null)
                    }
                }
            }
        }

        Text(
            stringResource(R.string.studio_track).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 6.dp),
        )
        val trackCd = stringResource(R.string.studio_track_cd)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).selectableGroup().semantics { contentDescription = trackCd },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (t in state.tracks) {
                FilterChip(selected = state.track == t, onClick = { onEvent(PromptEvent.SelectTrack(t)) }, label = { Text(stringResource(trackLabel(t))) })
            }
        }

        AnimatedContent(
            targetState = state.prompt,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 12 }) togetherWith (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 12 })
            },
            label = "prompt",
        ) { prompt ->
            if (prompt != null) PromptCard(prompt, state, onEvent)
        }

        Row(Modifier.padding(start = 2.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (state.fresh) Icons.Rounded.History else Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(
                    when {
                        state.fresh -> R.string.no_repeat_line
                        state.premium -> R.string.time_line_premium
                        else -> R.string.time_line_free
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shuffleCd = stringResource(R.string.shuffle_cd)
            OutlinedButton(
                onClick = { onEvent(PromptEvent.Shuffle) },
                modifier = Modifier.height(54.dp).widthIn(min = 54.dp).semantics { contentDescription = shuffleCd },
            ) { Icon(Icons.Rounded.Shuffle, null) }
            Button(onClick = onRecord, enabled = state.prompt != null, modifier = Modifier.weight(1f).height(54.dp)) {
                Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.primaryIcon)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.record))
            }
        }
        // M6: Sound Lab entry (S22, US-070)
        if (onOpenSoundLab != null) SoundLabEntryCard(onOpenSoundLab, Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun PromptCard(prompt: Prompt, state: PromptUiState, onEvent: (PromptEvent) -> Unit) {
    val hi = prompt.lang == Lang.HI
    val cd = stringResource(R.string.prompt_section)
    VaakCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).semantics { contentDescription = cd; liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.prompt_eyebrow, stringResource(difficultyLabel(prompt.difficulty))).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    state.fresh -> InfoChip(stringResource(R.string.new_prompt), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary, icon = Icons.Rounded.Autorenew)
                    prompt.prep.isNotEmpty() -> InfoChip(stringResource(R.string.prep_chip), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                prompt.text,
                style = if (hi) VaakTheme.type.hiPrompt.copy(fontSize = 21.sp) else VaakTheme.type.prompt,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
            val translit = prompt.translit
            if (hi && state.transliteration && translit != null) {
                Text(translit, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            HorizontalDivider(Modifier.padding(top = 12.dp), color = VaakTheme.colors.lineStrong)
            if (hi) {
                SwitchRow(stringResource(R.string.translit_toggle), stringResource(R.string.translit_toggle_cd), state.transliteration) { onEvent(PromptEvent.ToggleTranslit(it)) }
            }
            SwitchRow(stringResource(R.string.structure_hint), stringResource(R.string.structure_hint), state.structureHint) { onEvent(PromptEvent.ToggleStructure(it)) }
            if (state.structureHint && prompt.prep.size == 4) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("P", "R", "E", "P").zip(prompt.prep).forEach { (letter, line) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                letter,
                                style = MaterialTheme.typography.labelSmall,
                                color = VaakTheme.colors.accentInk,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).background(VaakTheme.colors.accentTint).widthIn(min = 22.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (hi) NotoDeva else MaterialTheme.typography.bodySmall.fontFamily, lineHeight = if (hi) 21.sp else 19.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, cd: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = cd })
    }
}
