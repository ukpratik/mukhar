package io.github.ukpratik.mukhar.app.feature.talk.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.talk.common.CenterState
import io.github.ukpratik.mukhar.app.feature.talk.common.CreditPill
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoNote
import io.github.ukpratik.mukhar.app.feature.talk.common.SectionHead
import io.github.ukpratik.mukhar.app.feature.talk.common.StepState
import io.github.ukpratik.mukhar.app.feature.talk.common.StepsList
import io.github.ukpratik.mukhar.app.feature.talk.common.clock
import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote
import io.github.ukpratik.mukhar.domain.feedback.Alternative
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.TileShape

// ADR-008 results-screen pieces for on-device and own-key analysis.

/** "Offline analysis · approx." chip; tapping it explains what that means, in place. */
@Composable
fun OfflineChip(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val stateText = stringResource(if (open) R.string.a11y_expanded else R.string.a11y_collapsed)
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { open = !open }
                .heightIn(min = 48.dp).padding(vertical = 8.dp).semantics { stateDescription = stateText }.testTag(TAG_OFFLINE_CHIP),
        ) {
            InfoChip(
                stringResource(R.string.offline_chip),
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Rounded.PhoneAndroid,
            )
        }
        if (open) InfoNote(Icons.Rounded.Info, stringResource(R.string.offline_chip_explain), Modifier.testTag(TAG_OFFLINE_EXPLAIN))
    }
}

/** One line per analysis note (own-key fallbacks, offline transcript), so a fallback is never silent. */
@Composable
fun AnalysisNotes(notes: List<AnalysisNote>, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag(TAG_ANALYSIS_NOTES), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        notes.distinct().forEach { InfoNote(Icons.Rounded.Info, stringResource(noteText(it))) }
    }
}

fun noteText(note: AnalysisNote): Int = when (note) {
    AnalysisNote.STT_KEY_REJECTED -> R.string.note_stt_key_rejected
    AnalysisNote.STT_FALLBACK -> R.string.note_stt_fallback
    AnalysisNote.LLM_KEY_REJECTED -> R.string.note_llm_key_rejected
    AnalysisNote.LLM_FALLBACK -> R.string.note_llm_fallback
    AnalysisNote.LLM_UNGROUNDED -> R.string.note_llm_ungrounded
    AnalysisNote.OFFLINE_TRANSCRIPT -> R.string.note_offline_transcript
    AnalysisNote.SOFT_CAP_REACHED -> R.string.note_soft_cap
    AnalysisNote.AUDIO_ONLY -> R.string.note_audio_only
}

/** Replaces the grammar/rewrites section in local mode (ADR-008 copy). */
@Composable
fun GrammarNudge(onAddKey: () -> Unit, modifier: Modifier = Modifier) {
    DashedCard(modifier.padding(top = 10.dp, bottom = 10.dp).testTag(TAG_GRAMMAR_NUDGE)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(
                stringResource(R.string.grammar_nudge),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            TextButton(onClick = onAddKey, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.grammar_nudge_cta)) }
        }
    }
}

/** "Try instead": overused words with curated alternatives; a tap adds the word to the Word Gym (US-021). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordUpgrades(
    upgrades: List<WordUpgradeUi>,
    lang: Lang,
    collected: (String) -> Boolean,
    onWord: (UpgradedWord, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (upgrades.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag(TAG_WORD_UPGRADES)) {
        SectionHead(stringResource(R.string.try_instead_title), null, Modifier.padding(top = 4.dp))
        for (u in upgrades) {
            MukharCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = TileShape) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        pluralStringResource(R.plurals.overused_line, u.count, u.word, u.count),
                        style = feedbackStyle(lang),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in u.alternatives) AlternativeChip(a, collected(a.word)) { onWord(UpgradedWord(a.word, a.meaning, a.register), a.example) }
                    }
                    u.alternatives.firstOrNull()?.let { a ->
                        Text(
                            stringResource(R.string.try_instead_example, a.example),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlternativeChip(a: Alternative, added: Boolean, onClick: () -> Unit) {
    val register = stringResource(registerLabel(a.register))
    FilterChip(
        selected = added,
        onClick = onClick,
        label = { Text(stringResource(R.string.try_instead_chip, a.word, register)) },
        leadingIcon = if (added) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

fun registerLabel(r: Register): Int = when (r) {
    Register.FORMAL -> R.string.register_formal
    Register.CASUAL -> R.string.register_casual
    Register.POWER -> R.string.register_power
}

/**
 * No offline model and no speech-to-text key (ADR-008): the talk is saved; install the model or add a key, and it's
 * analysed from the saved audio through the queue.
 */
@Composable
fun ColumnScope.NeedsEngine(state: ReportUiState, onModels: () -> Unit, onKeys: () -> Unit, onRetry: () -> Unit, onLater: () -> Unit) {
    CenterState(Icons.Rounded.MicNone, stringResource(R.string.needs_engine_title), stringResource(R.string.needs_engine_body), live = false) {
        state.credit?.let { CreditPill(it, Modifier.padding(top = 16.dp)) }
        StepsList(
            listOf(
                stringResource(R.string.step_saved, clock(state.meta?.durationMs ?: 0)) to StepState.DONE,
                stringResource(R.string.step_transcript) to StepState.TODO,
                stringResource(R.string.step_measure) to StepState.TODO,
                stringResource(R.string.step_notes) to StepState.TODO,
            ),
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onModels, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(TAG_NEEDS_MODEL)) {
        Icon(Icons.Rounded.Download, null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.needs_engine_model))
    }
    OutlinedButton(onClick = onKeys, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 54.dp).testTag(TAG_NEEDS_KEY)) {
        Icon(Icons.Rounded.Key, null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.needs_engine_key))
    }
    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Icon(Icons.Rounded.Refresh, null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.try_again))
    }
    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keep_for_later)) }
    Text(
        stringResource(R.string.needs_engine_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

const val TAG_OFFLINE_CHIP = "report_offline_chip"
const val TAG_OFFLINE_EXPLAIN = "report_offline_explain"
const val TAG_ANALYSIS_NOTES = "report_analysis_notes"
const val TAG_GRAMMAR_NUDGE = "report_grammar_nudge"
const val TAG_WORD_UPGRADES = "report_word_upgrades"
const val TAG_NEEDS_MODEL = "report_needs_model"
const val TAG_NEEDS_KEY = "report_needs_key"
