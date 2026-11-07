package io.github.ukpratik.mukhar.app.feature.talk.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharSpinner
import io.github.ukpratik.mukhar.data.byok.SpeakingIdeas
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.domain.ideas.GuideTrack
import io.github.ukpratik.mukhar.domain.ideas.SpeakingGuide
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Prompt
import io.github.ukpratik.mukhar.domain.prompt.Track
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme

/**
 * The "Need ideas?" sheet (US-078). [onOpenKeys] is null where leaving isn't sensible (while recording): the locked
 * line is then plain text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakingIdeasSheet(
    prompt: Prompt,
    track: Track,
    onDismiss: () -> Unit,
    onOpenKeys: (() -> Unit)?,
    vm: IdeasViewModel = hiltViewModel(),
) {
    LaunchedEffect(prompt.id, track) { vm.load(prompt, track) }
    val state by vm.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        IdeasContent(state, vm::onEvent, onOpenKeys?.let { open -> { onDismiss(); open() } })
    }
}

/** Stateless sheet body: guardrail, style chips, the free guide, then the AI card. */
@Composable
fun IdeasContent(state: IdeasUiState, onEvent: (IdeasEvent) -> Unit, onOpenKeys: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.xl)
            .navigationBarsPadding().testTag(TAG_IDEAS),
        verticalArrangement = Arrangement.spacedBy(MukharSpace.m),
    ) {
        Text(
            stringResource(R.string.ideas_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.ideas_guardrail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.tracks.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tracks.forEach { t ->
                    FilterChip(
                        selected = t == state.track,
                        onClick = { onEvent(IdeasEvent.SelectTrack(t)) },
                        label = { Text(stringResource(guideTrackLabel(t))) },
                        leadingIcon = if (t == state.track) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                        modifier = Modifier.testTag("ideas_track_${t.key}"),
                    )
                }
            }
        }
        state.guide?.let { FreeGuide(it) }
        AiCard(state, onEvent, onOpenKeys)
    }
}

@Composable
private fun textStyle(lang: Lang, base: TextStyle = MaterialTheme.typography.bodyMedium): TextStyle =
    if (lang == Lang.HI) MukharTheme.type.hiBodyMedium else base

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeGuide(g: SpeakingGuide) {
    val body = textStyle(g.lang)
    MukharCard(Modifier.fillMaxWidth().testTag(TAG_IDEAS_GUIDE)) {
        Column(Modifier.padding(MukharSpace.l), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.ideas_guide_title, g.structureName))
            Text(g.structureLine, style = body.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
            g.steps.forEachIndexed { i, step ->
                Row(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        (i + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MukharTheme.colors.accentInk,
                        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).background(MukharTheme.colors.accentTint)
                            .widthIn(min = 24.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(step.label, style = body.copy(fontWeight = FontWeight.SemiBold))
                        Text(step.cue, style = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        step.forThisPrompt?.let {
                            Text(stringResource(R.string.ideas_for_this_prompt_line, it), style = body.copy(fontStyle = FontStyle.Italic))
                        }
                    }
                }
            }
            if (g.promptPoints.isNotEmpty()) {
                SectionLabel(stringResource(R.string.ideas_for_this_prompt))
                g.promptPoints.forEach { Text("• $it", style = body) }
            }
            g.tip?.let { Text(it, style = body.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(g.pace, style = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel(stringResource(R.string.ideas_opening))
            Text("“${g.opening}”", style = if (g.lang == Lang.HI) MukharTheme.type.hiBodyLarge else MukharTheme.type.quote, color = MaterialTheme.colorScheme.onSurface)
            if (g.wordsToTry.isNotEmpty()) {
                SectionLabel(stringResource(R.string.ideas_words))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.wordsToTry.forEach { WordPill(it, g.lang) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
}

@Composable
private fun WordPill(word: String, lang: Lang) {
    Text(
        word,
        style = textStyle(lang, MaterialTheme.typography.labelLarge),
        color = MukharTheme.colors.accentInk,
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(MukharTheme.colors.accentTint).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiCard(state: IdeasUiState, onEvent: (IdeasEvent) -> Unit, onOpenKeys: (() -> Unit)?) {
    val lang = state.prompt?.lang ?: Lang.EN
    MukharCard(Modifier.fillMaxWidth().testTag(TAG_IDEAS_AI)) {
        Column(Modifier.padding(MukharSpace.l).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val ai = state.ai) {
                AiIdeasUi.Locked -> Row(
                    Modifier.fillMaxWidth().heightIn(min = MukharSpace.touch)
                        .let { if (onOpenKeys != null) it.clickable(role = Role.Button, onClick = onOpenKeys) else it }
                        .testTag(TAG_IDEAS_LOCKED).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ideas_locked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AiIdeasUi.Ready -> WriteButton(R.string.ideas_write, onEvent)
                AiIdeasUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(TAG_IDEAS_LOADING)) {
                    MukharSpinner(size = 22.dp, strokeWidth = 3.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ideas_writing), style = MaterialTheme.typography.bodyMedium)
                }
                is AiIdeasUi.Failed -> {
                    Text(stringResource(failureRes(ai.reason)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(TAG_IDEAS_FAILED))
                    if (ai.reason != IdeasFailure.PAUSED && ai.reason != IdeasFailure.KEY_REJECTED) WriteButton(R.string.ideas_try_again, onEvent)
                }
                is AiIdeasUi.Shown -> {
                    AiIdeas(ai.ideas, lang)
                    WriteButton(R.string.ideas_regenerate, onEvent, icon = Icons.Rounded.Refresh, outlined = true)
                }
            }
        }
    }
}

@Composable
private fun WriteButton(label: Int, onEvent: (IdeasEvent) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.AutoAwesome, outlined: Boolean = false) {
    val content: @Composable () -> Unit = {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
    val m = Modifier.fillMaxWidth().heightIn(min = MukharSpace.touch).testTag(TAG_IDEAS_WRITE)
    if (outlined) OutlinedButton(onClick = { onEvent(IdeasEvent.Write) }, modifier = m) { content() } else Button(onClick = { onEvent(IdeasEvent.Write) }, modifier = m) { content() }
    Text(stringResource(R.string.ideas_cost), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.AiIdeas(ideas: SpeakingIdeas, lang: Lang) {
    val body = textStyle(lang)
    SectionLabel(stringResource(R.string.ideas_ai_angles))
    ideas.angles.forEach { Text("• $it", style = body) }
    SectionLabel(stringResource(R.string.ideas_ai_outline))
    ideas.outline.forEachIndexed { i, s ->
        Text("${i + 1}. ${s.label}: ${s.cue}", style = body, modifier = Modifier.semantics(mergeDescendants = true) {})
    }
    SectionLabel(stringResource(R.string.ideas_ai_opening))
    Text("“${ideas.opening}”", style = if (lang == Lang.HI) MukharTheme.type.hiBodyLarge else MukharTheme.type.quote)
    SectionLabel(stringResource(R.string.ideas_ai_phrases))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ideas.phrases.forEach { WordPill(it, lang) }
    }
    if (ideas.words.isNotEmpty()) {
        SectionLabel(stringResource(R.string.ideas_words))
        ideas.words.forEach { w ->
            Text("${w.word}: ${w.meaning}", style = body, modifier = Modifier.semantics(mergeDescendants = true) {})
        }
    }
}

fun guideTrackLabel(t: GuideTrack): Int = when (t) {
    GuideTrack.PROFESSIONAL -> R.string.track_professional
    GuideTrack.INTERVIEWS -> R.string.track_interviews
    GuideTrack.PUBLIC_SPEAKING -> R.string.track_public_speaking
    GuideTrack.CONTENT_CREATION -> R.string.track_content_creation
    GuideTrack.WORD_POWER -> R.string.track_word_power
    GuideTrack.EVERYDAY -> R.string.track_everyday
}

private fun failureRes(f: IdeasFailure): Int = when (f) {
    IdeasFailure.OFFLINE -> R.string.ideas_fail_offline
    IdeasFailure.KEY_REJECTED -> R.string.ideas_fail_key
    IdeasFailure.REFUSED -> R.string.ideas_fail_refused
    IdeasFailure.BUSY -> R.string.ideas_fail_busy
    IdeasFailure.BAD_RESPONSE -> R.string.ideas_fail_bad
    IdeasFailure.PAUSED -> R.string.ideas_fail_paused
}

const val TAG_IDEAS = "ideas_sheet"
const val TAG_IDEAS_GUIDE = "ideas_guide"
const val TAG_IDEAS_AI = "ideas_ai"
const val TAG_IDEAS_LOCKED = "ideas_locked"
const val TAG_IDEAS_WRITE = "ideas_write"
const val TAG_IDEAS_LOADING = "ideas_loading"
const val TAG_IDEAS_FAILED = "ideas_failed"
const val TAG_NEED_IDEAS = "need_ideas"
const val TAG_PEEK_IDEAS = "peek_ideas"
