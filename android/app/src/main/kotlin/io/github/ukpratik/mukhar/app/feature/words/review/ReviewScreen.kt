package io.github.ukpratik.mukhar.app.feature.words.review

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
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
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.Eyebrow
import io.github.ukpratik.mukhar.app.feature.talk.common.CenterState
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.talk.common.Skeleton
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharBanner
import io.github.ukpratik.mukhar.app.feature.talk.common.BannerTone
import io.github.ukpratik.mukhar.app.feature.talk.common.clock
import io.github.ukpratik.mukhar.app.feature.talk.common.rememberHaptics
import io.github.ukpratik.mukhar.app.feature.words.LineDiff
import io.github.ukpratik.mukhar.app.feature.words.diffText
import io.github.ukpratik.mukhar.app.feature.words.hearDescription
import io.github.ukpratik.mukhar.app.feature.words.intervalLabel
import io.github.ukpratik.mukhar.app.feature.words.posAndRegister
import io.github.ukpratik.mukhar.app.feature.words.shortDay
import io.github.ukpratik.mukhar.data.repo.ReviewCard
import io.github.ukpratik.mukhar.data.repo.ReviewMode
import io.github.ukpratik.mukhar.data.repo.WordSource
import io.github.ukpratik.mukhar.domain.fsrs.Rating
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.revise.CardType
import io.github.ukpratik.mukhar.domain.words.SentenceVerdict
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
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
                CenterState(Icons.Rounded.DoneAll, stringResource(R.string.review_empty_title), stringResource(R.string.review_empty_body), Modifier.padding(horizontal = MukharSpace.gutter))
                TextButton(onClick = actions.onClose, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.review_back_today)) }
            }
            else -> {
                TopBar(state, actions.onClose, showCount = true)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter),
                    verticalArrangement = Arrangement.spacedBy(MukharSpace.m),
                ) {
                    LinearProgressIndicator(
                        progress = { state.index.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        trackColor = MukharTheme.colors.track,
                        drawStopIndicator = {},
                    )
                    val offlineWord = card.word != null && !state.online && state.flipped
                    if (offlineWord) {
                        MukharBanner(Icons.Outlined.CloudOff, stringResource(R.string.review_offline_title), stringResource(R.string.review_offline_body), BannerTone.INFO, Modifier.testTag(TAG_OFFLINE))
                    }
                    when (state.sentence.phase) {
                        SentencePhase.IDLE -> CardBody(state, card, actions)
                        SentencePhase.RECORDING -> Listening(state, card, actions)
                        SentencePhase.CHECKING -> Checking(card)
                        SentencePhase.RESULT, SentencePhase.FAILED -> Verdict(state, card, actions)
                    }
                    Spacer(Modifier.height(MukharSpace.s))
                }
                if (state.flipped && state.sentence.phase != SentencePhase.RECORDING && state.sentence.phase != SentencePhase.CHECKING) {
                    Grades(state, card, { actions.onEvent(ReviewEvent.Grade(it)) }, Modifier.padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.l))
                } else if (!state.flipped) {
                    Button(
                        onClick = { actions.onEvent(ReviewEvent.Flip) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.l).heightIn(min = 56.dp).testTag(TAG_SHOW),
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
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Box(Modifier.widthIn(min = MukharSpace.touch), contentAlignment = Alignment.Center) {
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
    Box(Modifier.size(MukharSpace.touch)) {
        if (state.voices[lang] == true) {
            androidx.compose.material3.FilledTonalIconButton(onClick = { actions.onEvent(ReviewEvent.Speak(text, lang)) }) {
                Icon(Icons.Rounded.VolumeUp, description)
            }
        }
    }
}

@Composable
private fun FlashCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    MukharCard(modifier.fillMaxWidth(), onClick = onClick, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(MukharSpace.xl), content = content)
    }
}

@Composable
private fun headStyle(lang: Lang, size: Int = 34) = if (lang == Lang.HI) {
    MukharTheme.type.hiHeadline.copy(fontSize = size.sp, lineHeight = (size * 1.45).sp)
} else {
    MaterialTheme.typography.headlineLarge.copy(fontSize = size.sp, lineHeight = (size * 1.15).sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun bodyStyle(lang: Lang) = if (lang == Lang.HI) MukharTheme.type.hiBodyMedium else MaterialTheme.typography.bodyMedium

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
            // One sentence for TalkBack; the speaker inside is cleared with the rest, so it's offered as an action.
            val hear = hearDescription(lemma, card.lang)
            val canHear = !meaningFirst && state.voices[card.lang] == true
            val flip = { actions.onEvent(ReviewEvent.Flip) }
            FlashCard(
                Modifier.heightIn(min = 300.dp).testTag(TAG_FRONT).clearAndSetSemantics {
                    contentDescription = cd
                    role = Role.Button
                    onClick { flip(); true }
                    if (canHear) customActions = listOf(CustomAccessibilityAction(hear) { actions.onEvent(ReviewEvent.Speak(lemma, card.lang)); true })
                },
                onClick = flip,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TypeEyebrow(state, card) }
                    if (!meaningFirst) Speaker(state, lemma, card.lang, hearDescription(lemma, card.lang), actions)
                }
                if (meaningFirst) {
                    Text(card.front, style = if (card.lang == Lang.HI) MukharTheme.type.hiHeadline else MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
                } else {
                    Text(lemma, style = headStyle(card.lang), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp))
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    if (w?.source == WordSource.TALK) {
                        InfoChip(stringResource(R.string.today_word_from_talk), MukharTheme.colors.accentTint, MukharTheme.colors.accentInk, Modifier.padding(top = 12.dp), Icons.Rounded.Mic)
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
            Column(verticalArrangement = Arrangement.spacedBy(MukharSpace.m)) {
                FlashCard(Modifier.testTag(TAG_BACK).semantics(mergeDescendants = true) {}) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TypeEyebrow(state, card) }
                        Speaker(state, lemma, card.lang, hearDescription(lemma, card.lang), actions)
                    }
                    Text(lemma, style = headStyle(card.lang), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 14.dp), color = MukharTheme.colors.lineStrong)
                    Text(
                        w?.meaning ?: if (meaningFirst) card.front else card.back,
                        style = bodyStyle(card.lang).copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    w?.gloss?.let {
                        Text(it, style = if (card.lang == Lang.HI) MaterialTheme.typography.bodySmall else MukharTheme.type.hiBodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    w?.example?.takeIf(String::isNotBlank)?.let { ex ->
                        Row(Modifier.padding(top = 10.dp)) {
                            Box(Modifier.width(2.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Spacer(Modifier.width(12.dp))
                            Text("“$ex”", style = if (card.lang == Lang.HI) MukharTheme.type.hiBodyMedium else MukharTheme.type.quote)
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
@OptIn(ExperimentalLayoutApi::class)
private fun Listening(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    MiniCard(card)
    // "Recording started" once (design-system §10.4); the ticking clock is not read, so it never re-announces.
    val started = stringResource(R.string.recording_started)
    Text("", Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = started })
    MukharCard(Modifier.fillMaxWidth().testTag(TAG_LISTENING)) {
        Column(Modifier.padding(MukharSpace.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(MukharTheme.colors.accent))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.sentence_listening, card.word?.lemma.orEmpty()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    clock(state.sentence.elapsedMs),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            Text(stringResource(R.string.sentence_keyterm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { actions.onEvent(ReviewEvent.StopSentence) }, modifier = Modifier.weight(1f).heightIn(min = MukharSpace.touch)) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sentence_stop))
                }
                TextButton(onClick = { actions.onEvent(ReviewEvent.CancelSentence) }, modifier = Modifier.heightIn(min = MukharSpace.touch)) { Text(stringResource(R.string.sentence_cancel)) }
            }
        }
    }
}

@Composable
private fun Checking(card: ReviewCard) {
    MiniCard(card)
    MukharCard(Modifier.fillMaxWidth().testTag(TAG_CHECKING)) {
        Column(Modifier.padding(MukharSpace.l)) {
            Text(
                stringResource(R.string.sentence_checking),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Skeleton(0.9f)
            Skeleton(0.6f)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Verdict(state: ReviewUiState, card: ReviewCard, actions: ReviewActions) {
    MiniCard(card)
    val r = state.sentence.result
    if (r == null) {
        val failure = state.sentence.failure
        MukharBanner(
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
        SentenceVerdict.ALMOST -> Triple(Icons.Outlined.EditNote, MukharTheme.colors.accentTint, MukharTheme.colors.accentInk)
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
            FlowRow {
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
    MukharCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Eyebrow(title, Modifier.semantics { heading() })
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
            style = if (card.lang == Lang.HI) MukharTheme.type.hiPrompt else MukharTheme.type.prompt,
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
        // The group label stays (it says whether the grade is the user's own call); the traversal group keeps
        // the four buttons together in focus order.
        val grades = Modifier.fillMaxWidth().semantics { contentDescription = group; isTraversalGroup = true }.testTag(TAG_GRADES)
        if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
            // 2 × 2 at large text so labels and intervals never truncate (design-system §10.5).
            Column(grades, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Rating.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { rating -> GradeButton(rating, state.intervals[rating], rating == suggested, { onGrade(rating) }, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(grades, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rating.entries.forEach { rating ->
                    GradeButton(rating, state.intervals[rating], rating == suggested, { onGrade(rating) }, Modifier.weight(1f))
                }
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
    // The suggested grade is marked by a check and a thicker border, not by colour alone (design-system §10.3).
    // The click is declared in the same block that clears the children, so it's never lost.
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp).testTag("grade_${rating.name.lowercase()}")
            .clearAndSetSemantics { contentDescription = cd; role = Role.Button; onClick { onClick(); true } },
        shape = MaterialTheme.shapes.medium,
        color = if (suggested) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (suggested) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (suggested) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
            Text(short, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Done(mode: ReviewMode, d: DoneUi, actions: ReviewActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.done_close_cd)) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.l).testTag(TAG_DONE),
        verticalArrangement = Arrangement.spacedBy(MukharSpace.m),
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
                Surface(shape = CircleShape, color = MukharTheme.colors.accentTint, contentColor = MukharTheme.colors.accentInk, modifier = Modifier.padding(top = 12.dp).testTag(TAG_DONE_CREDIT)) {
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
        // One sentence with the counts (a bare "What you revised" hid them).
        val title = stringResource(R.string.done_tally_a11y)
        val words = pluralStringResource(R.plurals.done_tally_words, d.words, d.words)
        val tally = if (mode == ReviewMode.REVISE) {
            stringResource(
                R.string.done_tally_revise_cd, title, words,
                pluralStringResource(R.plurals.done_tally_fixes, d.fixes, d.fixes),
                pluralStringResource(R.plurals.done_tally_lines, d.lines, d.lines),
            )
        } else {
            stringResource(R.string.done_tally_gym_cd, title, words)
        }
        MukharCard(Modifier.fillMaxWidth().testTag(TAG_DONE_TALLY).clearAndSetSemantics { contentDescription = tally }) {
            Column(Modifier.padding(horizontal = MukharSpace.l, vertical = 4.dp)) {
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
        Spacer(Modifier.height(MukharSpace.l))
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

/** Above this font scale the grade buttons go 2 × 2. */
private const val LARGE_FONT_SCALE = 1.3f
const val TAG_DONE_TALLY = "review_done_tally"
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
