package com.vaak.app.feature.words.review

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.onboarding.Eyebrow
import com.vaak.app.feature.talk.common.CenterState
import com.vaak.app.feature.talk.common.InfoChip
import com.vaak.app.feature.talk.common.Skeleton
import com.vaak.app.feature.talk.common.VaakBanner
import com.vaak.app.feature.talk.common.BannerTone
import com.vaak.app.feature.talk.common.clock
import com.vaak.app.feature.talk.common.rememberHaptics
import com.vaak.app.feature.words.LineDiff
import com.vaak.app.feature.words.diffText
import com.vaak.app.feature.words.hearDescription
import com.vaak.app.feature.words.intervalLabel
import com.vaak.app.feature.words.posAndRegister
import com.vaak.app.feature.words.shortDay
import com.vaak.data.repo.ReviewCard
import com.vaak.data.repo.ReviewMode
import com.vaak.data.repo.WordSource
import com.vaak.domain.fsrs.Rating
import com.vaak.domain.model.Lang
import com.vaak.domain.revise.CardType
import com.vaak.domain.words.SentenceVerdict
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import java.time.Duration
import java.time.ZoneId

data class ReviewActions(
    val onEvent: (ReviewEvent) -> Unit = {},
    val onClose: () -> Unit = {},
    /** "Say it in a sentence": the screen asks for the mic first, then sends [ReviewEvent.StartSentence]. */
    val onSay: () -> Unit = {},
    val onTurnOnTalks: () -> Unit = {},
)

@Composable
fun ReviewScreen(onClose: () -> Unit, onTurnOnTalks: () -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val counts = stringResource(R.string.review_today_counts)
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.onEvent(ReviewEvent.StartSentence)
    }
    LaunchedEffect(vm) {
        vm.effect.collect { e ->
            when (e) {
                ReviewEffect.Confirm -> haptics.confirm()
                ReviewEffect.TodayCounts -> snackbar.showSnackbar(counts)
            }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        ReviewContent(
            state,
            ReviewActions(
                onEvent = vm::onEvent,
                onClose = onClose,
                onSay = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.onEvent(ReviewEvent.StartSentence) else mic.launch(Manifest.permission.RECORD_AUDIO)
                },
                onTurnOnTalks = onTurnOnTalks,
            ),
            Modifier.padding(padding),
        )
    }
}

/** S13 (every state) and S15 (word, fix, saved line, done). Stateless; tested on Robolectric. */
@Composable
fun ReviewContent(state: ReviewUiState, actions: ReviewActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(TAG_REVIEW)) {
        if (state.loading) return@Column
        val done = state.done
        val card = state.card
        when {
            done != null -> Done(state.mode, done, actions)
            card == null -> {
                TopBar(state, actions.onClose, showCount = false)
                CenterState(Icons.Rounded.DoneAll, stringResource(R.string.review_empty_title), stringResource(R.string.review_empty_body), Modifier.padding(horizontal = VaakSpace.gutter))
                TextButton(onClick = actions.onClose, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.review_back_today)) }
            }
            else -> {
                TopBar(state, actions.onClose, showCount = true)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter),
                    verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
                ) {
                    LinearProgressIndicator(
                        progress = { state.index.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        trackColor = VaakTheme.colors.track,
                        drawStopIndicator = {},
                    )
                    val offlineWord = card.word != null && !state.online && state.flipped
                    if (offlineWord) {
                        VaakBanner(Icons.Outlined.CloudOff, stringResource(R.string.review_offline_title), stringResource(R.string.review_offline_body), BannerTone.INFO, Modifier.testTag(TAG_OFFLINE))
                    }
                    when (state.sentence.phase) {
                        SentencePhase.IDLE -> CardBody(state, card, actions)
                        SentencePhase.RECORDING -> Listening(state, card, actions)
                        SentencePhase.CHECKING -> Checking(card)
                        SentencePhase.RESULT, SentencePhase.FAILED -> Verdict(state, card, actions)
                    }
                    Spacer(Modifier.height(VaakSpace.s))
                }
                if (state.flipped && state.sentence.phase != SentencePhase.RECORDING && state.sentence.phase != SentencePhase.CHECKING) {
                    Grades(state, card, { actions.onEvent(ReviewEvent.Grade(it)) }, Modifier.padding(horizontal = VaakSpace.gutter).padding(bottom = VaakSpace.l))
                } else if (!state.flipped) {
                    Button(
                        onClick = { actions.onEvent(ReviewEvent.Flip) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = VaakSpace.gutter).padding(bottom = VaakSpace.l).heightIn(min = 56.dp).testTag(TAG_SHOW),
                    ) {
                        Text(
                            stringResource(
                                when (card.type) {
                                    CardType.MEANING_WORD -> R.string.review_show_word
                                    CardType.MISTAKE_FIX -> R.string.review_show_fix
                                    else -> R.string.review_show
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: ReviewUiState, onClose: () -> Unit, showCount: Boolean) {
    val gym = state.mode == ReviewMode.GYM
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, stringResource(if (gym) R.string.review_close_cd else R.string.revise_close_cd))
        }
        Text(
            stringResource(if (gym) R.string.gym_title else R.string.revise_bar_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box(Modifier.width(VaakSpace.touch), contentAlignment = Alignment.Center) {
            if (showCount) {
                val n = (state.index + 1).coerceAtMost(state.total)
                val cd = stringResource(R.string.review_count_cd, n, state.total)
                Text(
                    "$n/${state.total}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = cd },
                )
            }
        }
    }
}

@Composable
private fun TypeEyebrow(state: ReviewUiState, card: ReviewCard) {
    val revise = state.mode == ReviewMode.REVISE
    val text = when (card.type) {
        CardType.WORD_MEANING -> stringResource(if (revise) R.string.revise_type_word else R.string.review_type_word_meaning)
        CardType.MEANING_WORD -> stringResource(if (revise) R.string.revise_type_word else R.string.review_type_meaning_word)
        CardType.MISTAKE_FIX -> stringResource(R.string.revise_type_fix)
        CardType.SAVED_LINE -> stringResource(R.string.revise_type_line)
    }
    if (revise) {
        val icon = when (card.type) {
            CardType.MISTAKE_FIX -> Icons.Outlined.Build
            CardType.SAVED_LINE -> Icons.Outlined.BookmarkAdded
            else -> Icons.AutoMirrored.Outlined.MenuBook
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Eyebrow(text)
        }
    } else {
        Eyebrow(text)
    }
}

@Composable
private fun Speaker(state: ReviewUiState, text: String, lang: Lang, description: String, actions: ReviewActions) {
    // Hidden, not greyed, without a voice for the language (US-020); the slot keeps its size.
    Box(Modifier.size(VaakSpace.touch)) {
        if (state.voices[lang] == true) {
            androidx.compose.material3.FilledTonalIconButton(onClick = { actions.onEvent(ReviewEvent.Speak(text, lang)) }) {
                Icon(Icons.Rounded.VolumeUp, description)
            }
        }
    }
}

@Composable
private fun FlashCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    VaakCard(modifier.fillMaxWidth(), onClick = onClick, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(VaakSpace.xl), content = content)
    }
}

@Composable
private fun headStyle(lang: Lang, size: Int = 34) = if (lang == Lang.HI) {
    VaakTheme.type.hiHeadline.copy(fontSize = size.sp, lineHeight = (size * 1.45).sp)
} else {
    MaterialTheme.typography.headlineLarge.copy(fontSize = size.sp, lineHeight = (size * 1.15).sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun bodyStyle(lang: Lang) = if (lang == Lang.HI) VaakTheme.type.hiBodyMedium else MaterialTheme.typography.bodyMedium

@Composable
private fun CardBody(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    when (card.type) {
        CardType.WORD_MEANING, CardType.MEANING_WORD -> WordCard(state, card, actions)
        CardType.MISTAKE_FIX -> FixCard(state, card, actions)
        CardType.SAVED_LINE -> LineCard(state, card, actions)
    }
}

@Composable
private fun WordCard(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    val w = card.word
    val lemma = w?.lemma ?: if (card.type == CardType.WORD_MEANING) card.front else card.back
    val meta = w?.let { posAndRegister(it) }.orEmpty()
    val meaningFirst = card.type == CardType.MEANING_WORD
    Crossfade(state.flipped, label = "flip") { flipped ->
        if (!flipped) {
            val cd = if (meaningFirst) stringResource(R.string.review_front_mw_cd, card.front) else stringResource(R.string.review_front_cd, lemma, meta)
            FlashCard(
                Modifier.heightIn(min = 300.dp).testTag(TAG_FRONT).clearAndSetSemantics { contentDescription = cd; role = Role.Button },
                onClick = { actions.onEvent(ReviewEvent.Flip) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TypeEyebrow(state, card) }
                    if (!meaningFirst) Speaker(state, lemma, card.lang, hearDescription(lemma, card.lang), actions)
                }
                if (meaningFirst) {
                    Text(card.front, style = if (card.lang == Lang.HI) VaakTheme.type.hiHeadline else MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
                } else {
                    Text(lemma, style = headStyle(card.lang), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    if (w?.source == WordSource.TALK) {
                        InfoChip(stringResource(R.string.today_word_from_talk), VaakTheme.colors.accentTint, VaakTheme.colors.accentInk, Modifier.padding(top = 12.dp), Icons.Rounded.Mic)
                    }
                }
                Spacer(Modifier.weight(1f, fill = false).heightIn(min = 28.dp))
                Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (meaningFirst) R.string.review_hint_word else R.string.review_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VaakSpace.m)) {
                FlashCard(Modifier.testTag(TAG_BACK).semantics(mergeDescendants = true) {}) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TypeEyebrow(state, card) }
                        Speaker(state, lemma, card.lang, hearDescription(lemma, card.lang), actions)
                    }
                    Text(lemma, style = headStyle(card.lang), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 14.dp), color = VaakTheme.colors.lineStrong)
                    Text(
                        w?.meaning ?: if (meaningFirst) card.front else card.back,
                        style = bodyStyle(card.lang).copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    w?.gloss?.let {
                        Text(it, style = if (card.lang == Lang.HI) MaterialTheme.typography.bodySmall else VaakTheme.type.hiBodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    w?.example?.takeIf(String::isNotBlank)?.let { ex ->
                        Row(Modifier.padding(top = 10.dp)) {
                            Box(Modifier.width(2.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Spacer(Modifier.width(12.dp))
                            Text("“$ex”", style = if (card.lang == Lang.HI) VaakTheme.type.hiBodyMedium else VaakTheme.type.quote)
                        }
                    }
                }
                SayRow(state, actions)
            }
        }
    }
}

/** The "Say it in a sentence" entry (or why it isn't there). */
@Composable
private fun SayRow(state: ReviewUiState, actions: ReviewActions) {
    when {
        state.card?.word == null || !state.online -> Unit
        !state.talksEnabled -> TextButton(onClick = actions.onTurnOnTalks, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.review_talks_off))
        }
        state.quota.exhausted -> Text(
            stringResource(R.string.sentence_done_for_today),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(TAG_CHECKS_DONE),
        )
        else -> FilledTonalButton(onClick = actions.onSay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(TAG_SAY)) {
            Icon(Icons.Rounded.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.review_say))
        }
    }
}

@Composable
private fun MiniCard(card: ReviewCard) {
    FlashCard {
        Eyebrow(stringResource(R.string.sentence_with))
        Text(card.word?.lemma ?: card.front, style = headStyle(card.lang, 26), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Listening(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    MiniCard(card)
    VaakCard(Modifier.fillMaxWidth().testTag(TAG_LISTENING)) {
        Column(Modifier.padding(VaakSpace.l).semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(VaakTheme.colors.accent))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.sentence_listening, card.word?.lemma.orEmpty()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(clock(state.sentence.elapsedMs), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
            }
            Text(stringResource(R.string.sentence_keyterm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = { actions.onEvent(ReviewEvent.StopSentence) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sentence_stop))
                }
                TextButton(onClick = { actions.onEvent(ReviewEvent.CancelSentence) }) { Text(stringResource(R.string.sentence_cancel)) }
            }
        }
    }
}

@Composable
private fun Checking(card: ReviewCard) {
    MiniCard(card)
    VaakCard(Modifier.fillMaxWidth().testTag(TAG_CHECKING)) {
        Column(Modifier.padding(VaakSpace.l)) {
            Text(stringResource(R.string.sentence_checking), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Skeleton(0.9f)
            Skeleton(0.6f)
        }
    }
}

@Composable
private fun Verdict(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    MiniCard(card)
    val r = state.sentence.result
    if (r == null) {
        val failure = state.sentence.failure
        VaakBanner(
            if (failure == SentenceFailure.OFFLINE) Icons.Outlined.CloudOff else Icons.Outlined.Lightbulb,
            stringResource(
                when (failure) {
                    SentenceFailure.OFFLINE -> R.string.review_offline_title
                    SentenceFailure.QUOTA -> R.string.sentence_done_for_today
                    SentenceFailure.TOO_SHORT -> R.string.sentence_too_short
                    else -> R.string.sentence_failed
                },
            ),
            if (failure == SentenceFailure.OFFLINE) stringResource(R.string.review_offline_body) else null,
            BannerTone.INFO,
            Modifier.testTag(TAG_VERDICT),
        )
        if (failure == SentenceFailure.TOO_SHORT || failure == SentenceFailure.ERROR) {
            TextButton(onClick = { actions.onEvent(ReviewEvent.RetrySentence) }) { Text(stringResource(R.string.sentence_again)) }
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Eyebrow(stringResource(R.string.sentence_you_said))
        Text("“${r.transcript}”", style = bodyStyle(card.lang), modifier = Modifier.padding(top = 4.dp))
    }
    val (icon, bg, fg) = when (r.verdict) {
        SentenceVerdict.NICELY_USED -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
        SentenceVerdict.ALMOST -> Triple(Icons.Outlined.EditNote, VaakTheme.colors.accentTint, VaakTheme.colors.accentInk)
        // Neutral indigo, never red (S13 state 5).
        SentenceVerdict.NOT_YET -> Triple(Icons.Outlined.Lightbulb, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
    }
    Row(Modifier.fillMaxWidth().testTag(TAG_VERDICT).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                stringResource(
                    when (r.verdict) {
                        SentenceVerdict.NICELY_USED -> R.string.sentence_ok_title
                        SentenceVerdict.ALMOST -> R.string.sentence_almost_title
                        SentenceVerdict.NOT_YET -> R.string.sentence_no_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    when (r.verdict) {
        SentenceVerdict.ALMOST -> if (r.corrected.isNotBlank()) {
            FixBox(stringResource(R.string.sentence_try_like)) { Text(diffText(LineDiff.diff(r.transcript, r.corrected).second), style = bodyStyle(card.lang).copy(fontWeight = FontWeight.SemiBold)) }
            Row {
                TextButton(onClick = { actions.onEvent(ReviewEvent.RetrySentence) }) {
                    Icon(Icons.Rounded.Replay, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.sentence_again))
                }
                if (state.voices[card.lang] == true) {
                    val cd = stringResource(R.string.sentence_hear_cd)
                    TextButton(onClick = { actions.onEvent(ReviewEvent.Speak(r.corrected, card.lang)) }, modifier = Modifier.semantics { contentDescription = cd }) {
                        Icon(Icons.Rounded.VolumeUp, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sentence_hear))
                    }
                }
            }
        }
        SentenceVerdict.NOT_YET -> {
            val example = r.example.ifBlank { card.word?.example.orEmpty() }
            if (example.isNotBlank()) FixBox(stringResource(R.string.sentence_how_used)) { Text("“$example”", style = bodyStyle(card.lang).copy(fontWeight = FontWeight.SemiBold)) }
            if (state.canCheck) {
                FilledTonalButton(onClick = actions.onSay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Rounded.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sentence_try_another))
                }
            }
        }
        SentenceVerdict.NICELY_USED -> Unit
    }
    // The counter appears from check 3 (S13 notes).
    if (r.quota.used >= QUOTA_SHOWN_FROM) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(TAG_QUOTA)) {
            Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.sentence_quota, r.quota.used, r.quota.limit), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FixBox(title: String, content: @Composable () -> Unit) {
    VaakCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Eyebrow(title)
            content()
        }
    }
}

@Composable
private fun FixCard(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    val (said, instead) = LineDiff.diff(card.front, card.back)
    FlashCard(
        Modifier.testTag(if (state.flipped) TAG_BACK else TAG_FRONT),
        onClick = if (state.flipped) null else ({ actions.onEvent(ReviewEvent.Flip) }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TypeEyebrow(state, card) }
            if (state.flipped) Speaker(state, card.back, card.lang, stringResource(R.string.fix_hear_cd), actions)
        }
        Eyebrow(stringResource(R.string.fix_you_said), Modifier.padding(top = 10.dp))
        Text(diffText(said), style = bodyStyle(card.lang).copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Crossfade(state.flipped, label = "fix") { flipped ->
            if (flipped) {
                Column {
                    Eyebrow(stringResource(R.string.fix_say_instead), Modifier.padding(top = 12.dp))
                    Text(diffText(instead), style = bodyStyle(card.lang).copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                    Text(
                        stringResource(R.string.fix_from, shortDay(card.createdAt.atZone(ZoneId.systemDefault()).toLocalDate())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.review_hint_fix), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (state.flipped) Text(stringResource(R.string.fix_say_aloud), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LineCard(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    FlashCard(Modifier.testTag(TAG_BACK)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TypeEyebrow(state, card) }
            Speaker(state, card.back, card.lang, stringResource(R.string.line_hear_cd), actions)
        }
        Text(
            "“${card.back}”",
            style = if (card.lang == Lang.HI) VaakTheme.type.hiPrompt else VaakTheme.type.prompt,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.line_better_of, card.front),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    Text(stringResource(R.string.line_read_aloud), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Grades(state: ReviewUiState, card: ReviewCard, onGrade: (Rating) -> Unit, modifier: Modifier = Modifier) {
    val suggested = state.sentence.result?.verdict?.suggested
    val selfGrade = card.word != null && (!state.online || !state.talksEnabled)
    val question = when {
        card.type == CardType.SAVED_LINE -> R.string.review_line_grade_q
        selfGrade -> R.string.review_grade_self_q
        else -> R.string.review_grade_q
    }
    val group = stringResource(if (selfGrade) R.string.review_grade_self_group else R.string.review_grade_group)
    Column(modifier.fillMaxWidth()) {
        if (state.sentence.result == null) {
            Text(
                stringResource(question),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().semantics { contentDescription = group }.testTag(TAG_GRADES), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rating.entries.forEach { rating ->
                GradeButton(rating, state.intervals[rating], rating == suggested, { onGrade(rating) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GradeButton(rating: Rating, interval: Duration?, suggested: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(
        when (rating) {
            Rating.AGAIN -> R.string.grade_again
            Rating.HARD -> R.string.grade_hard
            Rating.GOOD -> R.string.grade_good
            Rating.EASY -> R.string.grade_easy
        },
    )
    val short = interval?.let { intervalLabel(it) }.orEmpty()
    val spoken = interval?.let { intervalLabel(it, spoken = true) }.orEmpty()
    val cd = stringResource(if (suggested) R.string.grade_sugg_cd else R.string.grade_cd, label, spoken)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp).clearAndSetSemantics { contentDescription = cd; role = Role.Button }.testTag("grade_${rating.name.lowercase()}"),
        shape = MaterialTheme.shapes.medium,
        color = if (suggested) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, if (suggested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.primary)
            Text(short, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Done(mode: ReviewMode, d: DoneUi, actions: ReviewActions) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.done_close_cd)) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).padding(bottom = VaakSpace.l).testTag(TAG_DONE),
        verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DoneAll, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(44.dp))
            }
            Text(
                stringResource(if (mode == ReviewMode.REVISE) R.string.done_title_revise else R.string.done_title_gym),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.done_body, pluralStringResource(R.plurals.done_cards, d.cards, d.cards), elapsed(d.elapsedMs)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (d.streakDay != null) {
                Surface(shape = CircleShape, color = VaakTheme.colors.accentTint, contentColor = VaakTheme.colors.accentInk, modifier = Modifier.padding(top = 12.dp).testTag(TAG_DONE_CREDIT)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.done_credit, d.streakDay), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Text(stringResource(R.string.done_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
        }
        val tally = stringResource(R.string.done_tally_a11y)
        VaakCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = tally }) {
            Column(Modifier.padding(horizontal = VaakSpace.l, vertical = 4.dp)) {
                TallyRow(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.done_words), d.words)
                if (mode == ReviewMode.REVISE) {
                    TallyRow(Icons.Outlined.Build, stringResource(R.string.done_fixes), d.fixes)
                    TallyRow(Icons.Outlined.BookmarkAdded, stringResource(R.string.done_lines), d.lines)
                }
            }
        }
        if (d.dueTomorrow > 0) {
            Text(pluralStringResource(R.plurals.done_next, d.dueTomorrow, d.dueTomorrow), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(VaakSpace.l))
        Button(onClick = actions.onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.review_back_today)) }
        // "Keep going" only when more items are due (S15 notes).
        if (d.more > 0) {
            TextButton(onClick = { actions.onEvent(ReviewEvent.KeepGoing) }, modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.done_keep_going, d.more, d.more))
            }
        }
    }
}

@Composable
private fun TallyRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, n: Int) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(n.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun elapsed(ms: Long): String {
    val s = (ms / 1000).toInt()
    return if (s < 60) stringResource(R.string.done_time_s, s) else stringResource(R.string.done_time, s / 60, s % 60)
}

private const val QUOTA_SHOWN_FROM = 3
const val TAG_REVIEW = "review"
const val TAG_FRONT = "review_front"
const val TAG_BACK = "review_back"
const val TAG_SHOW = "review_show"
const val TAG_GRADES = "review_grades"
const val TAG_SAY = "review_say"
const val TAG_CHECKS_DONE = "review_checks_done"
const val TAG_OFFLINE = "review_offline"
const val TAG_LISTENING = "review_listening"
const val TAG_CHECKING = "review_checking"
const val TAG_VERDICT = "review_verdict"
const val TAG_QUOTA = "review_quota"
const val TAG_DONE = "review_done"
const val TAG_DONE_CREDIT = "review_done_credit"
