package io.github.ukpratik.mukhar.app.feature.words.deck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.Eyebrow
import io.github.ukpratik.mukhar.app.feature.talk.common.ArtCircle
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharAppBar
import io.github.ukpratik.mukhar.app.feature.talk.common.reducedMotion
import io.github.ukpratik.mukhar.app.feature.words.SpeakerSlot
import io.github.ukpratik.mukhar.app.feature.words.hearDescription
import io.github.ukpratik.mukhar.app.feature.words.posRes
import io.github.ukpratik.mukhar.app.feature.words.registerRes
import io.github.ukpratik.mukhar.app.nav.DeckRoute
import io.github.ukpratik.mukhar.data.repo.DeckCard
import io.github.ukpratik.mukhar.data.repo.DeckState
import io.github.ukpratik.mukhar.data.repo.DeckUndo
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.data.tts.Speaker
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.words.DeckChoice
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

data class DeckUiState(
    val loading: Boolean = true,
    val deck: DeckState? = null,
    val voices: Map<Lang, Boolean> = emptyMap(),
) {
    val top: DeckCard? get() = deck?.cards?.firstOrNull()

    /** The goal screen shows once the day's goal is met, until "Keep going". */
    val showGoal: Boolean get() = deck?.let { it.goalMet && !it.keepGoing && it.remaining > 0 } == true
}

/** A decision just made, for the snackbar and its Undo. */
data class DeckDecided(val lemma: String, val choice: DeckChoice, val undo: DeckUndo)

/** US-081: one new word at a time; Got it, Star, Later or Not for me. */
@HiltViewModel
class DeckViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val words: WordRepository,
    private val speaker: Speaker,
) : ViewModel() {
    private val lang = savedState.toRoute<DeckRoute>().lang?.let { code -> Lang.entries.firstOrNull { it.code == code } }

    val state: StateFlow<DeckUiState> = combine(words.observeDeck(lang), speaker.voices) { d, v -> DeckUiState(false, d, v) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckUiState())

    private val _decided = Channel<DeckDecided>(Channel.BUFFERED)
    val decided: Flow<DeckDecided> = _decided.receiveAsFlow()

    init {
        speaker.warmUp()
    }

    fun decide(card: DeckCard, choice: DeckChoice) {
        viewModelScope.launch { words.decide(card.id, choice)?.let { _decided.send(DeckDecided(card.lemma, choice, it)) } }
    }

    fun undo(u: DeckUndo) {
        viewModelScope.launch { words.undo(u) }
    }

    fun keepGoing() = words.keepGoing()

    fun speak(text: String, lang: Lang) = speaker.speak(text, lang)
}

data class DeckActions(
    val onBack: () -> Unit = {},
    val onDecide: (DeckCard, DeckChoice) -> Unit = { _, _ -> },
    val onSpeak: (String, Lang) -> Unit = { _, _ -> },
    val onKeepGoing: () -> Unit = {},
    val onOpenGym: () -> Unit = {},
)

@Composable
fun DeckScreen(onBack: () -> Unit, onOpenGym: () -> Unit, vm: DeckViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val done = stringResource(R.string.deck_toast_done)
    val star = stringResource(R.string.deck_toast_star)
    val later = stringResource(R.string.deck_toast_later)
    val discard = stringResource(R.string.deck_toast_discard)
    LaunchedEffect(vm) {
        vm.decided.collect { d ->
            val text = when (d.choice) {
                DeckChoice.DONE -> done
                DeckChoice.STARRED -> star
                DeckChoice.LATER -> later
                DeckChoice.DISCARDED -> discard
            }.format(d.lemma)
            // A new decision replaces the last snackbar; its Undo stays with its own card.
            launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar(text, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undo(d.undo)
            }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        DeckContent(
            state,
            DeckActions(onBack = onBack, onDecide = vm::decide, onSpeak = vm::speak, onKeepGoing = vm::keepGoing, onOpenGym = onOpenGym),
            Modifier.padding(padding),
        )
    }
}

/** Stateless: progress, the card stack and the four actions, or the goal / empty screens. */
@Composable
fun DeckContent(state: DeckUiState, actions: DeckActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(TAG_DECK)) {
        MukharAppBar(stringResource(R.string.deck_title), stringResource(R.string.deck_back_cd), actions.onBack)
        val deck = state.deck ?: return@Column
        Progress(deck)
        when {
            state.showGoal -> GoalMet(deck, actions)
            deck.cards.isEmpty() -> Empty(actions)
            else -> Stack(state, actions)
        }
    }
}

@Composable
private fun Progress(deck: DeckState) {
    val a11y = pluralStringResource(R.plurals.deck_progress_a11y, deck.goal, deck.decidedToday, deck.goal)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.s)
            .semantics(mergeDescendants = true) { contentDescription = a11y; liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            if (deck.goalMet) pluralStringResource(R.plurals.deck_progress_extra, deck.decidedToday, deck.decidedToday) else pluralStringResource(R.plurals.deck_progress, deck.goal, deck.decidedToday, deck.goal),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_DECK_PROGRESS),
        )
        LinearProgressIndicator(
            progress = { if (deck.goal <= 0) 1f else (deck.decidedToday.toFloat() / deck.goal).coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
private fun Stack(state: DeckUiState, actions: DeckActions) {
    val top = state.top ?: return
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        // A fresh offset per card, so the next card always starts in the middle.
        val offset = remember(top.id) { Animatable(0f) }
        val leaving = remember(top.id) { mutableStateOf(false) }
        fun go(choice: DeckChoice) {
            // One decision per card, even with a double tap or a swipe during the exit animation.
            if (leaving.value) return
            leaving.value = true
            scope.launch {
                if (!still) {
                    val toRight = choice == DeckChoice.DONE || choice == DeckChoice.STARRED
                    offset.animateTo(if (toRight) width * 1.3f else -width * 1.3f, tween(EXIT_MS))
                }
                actions.onDecide(top, choice)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = MukharSpace.gutter)) {
                // The card behind, slightly smaller, so the deck reads as a stack.
                state.deck?.cards?.getOrNull(1)?.let { next ->
                    androidx.compose.runtime.key(next.id) {
                        WordCard(
                            next, voice = false, onSpeak = { _, _ -> },
                            modifier = Modifier.graphicsLayer {
                                val p = (abs(offset.value) / width).coerceIn(0f, 1f)
                                scaleX = 0.94f + 0.06f * p
                                scaleY = scaleX
                                alpha = 0.6f + 0.4f * p
                            }.clearAndSetSemantics {},
                            hidden = true,
                        )
                    }
                }
                val customActions = listOf(
                    DeckChoice.DONE to stringResource(R.string.deck_done),
                    DeckChoice.STARRED to stringResource(R.string.deck_star),
                    DeckChoice.LATER to stringResource(R.string.deck_later),
                    DeckChoice.DISCARDED to stringResource(R.string.deck_discard),
                ).map { (c, label) -> CustomAccessibilityAction(label) { go(c); true } }
                WordCard(
                    top,
                    voice = state.voices[top.lang] == true,
                    onSpeak = actions.onSpeak,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = offset.value
                            rotationZ = offset.value / width * MAX_TILT
                        }
                        .pointerInput(top.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val x = offset.value
                                    when {
                                        x > width * SWIPE_FRACTION -> go(DeckChoice.DONE)
                                        x < -width * SWIPE_FRACTION -> go(DeckChoice.LATER)
                                        else -> scope.launch { offset.animateTo(0f, tween(BACK_MS)) }
                                    }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(0f, tween(BACK_MS)) } },
                            ) { change, dx ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + dx) }
                            }
                        }
                        .semantics { this.customActions = customActions }
                        .testTag(TAG_DECK_CARD),
                )
            }
            Text(
                stringResource(R.string.deck_swipe_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = MukharSpace.s, start = MukharSpace.gutter, end = MukharSpace.gutter),
            )
            Buttons(top) { go(it) }
        }
    }
}

/** The four actions, always visible with text (never gesture-only). */
@Composable
private fun Buttons(card: DeckCard, onChoice: (DeckChoice) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MukharSpace.gutter, vertical = MukharSpace.m),
        verticalArrangement = Arrangement.spacedBy(MukharSpace.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
            val discardCd = stringResource(R.string.deck_discard_cd, card.lemma)
            OutlinedButton(
                onClick = { onChoice(DeckChoice.DISCARDED) },
                modifier = Modifier.weight(1f).heightIn(min = MukharSpace.touch).testTag(TAG_DECK_DISCARD).semantics { contentDescription = discardCd },
            ) {
                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.deck_discard), maxLines = 1)
            }
            val laterCd = stringResource(R.string.deck_later_cd, card.lemma)
            OutlinedButton(
                onClick = { onChoice(DeckChoice.LATER) },
                modifier = Modifier.weight(1f).heightIn(min = MukharSpace.touch).testTag(TAG_DECK_LATER).semantics { contentDescription = laterCd },
            ) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.deck_later), maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
            val starCd = stringResource(R.string.deck_star_cd, card.lemma)
            FilledTonalButton(
                onClick = { onChoice(DeckChoice.STARRED) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(TAG_DECK_STAR).semantics { contentDescription = starCd },
            ) {
                Icon(Icons.Rounded.Star, null, tint = MukharTheme.colors.accentInk)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.deck_star), maxLines = 1)
            }
            val doneCd = stringResource(R.string.deck_done_cd, card.lemma)
            Button(
                onClick = { onChoice(DeckChoice.DONE) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(TAG_DECK_DONE).semantics { contentDescription = doneCd },
            ) {
                Icon(Icons.Rounded.Check, null, tint = MukharTheme.colors.accent)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.deck_done), maxLines = 1)
            }
        }
    }
}

/** The card: word, pronunciation, both meanings, example. [hidden] = the card behind (not read or tappable). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordCard(card: DeckCard, voice: Boolean, onSpeak: (String, Lang) -> Unit, modifier: Modifier = Modifier, hidden: Boolean = false) {
    val hi = card.lang == Lang.HI
    MukharCard(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = !hidden).padding(MukharSpace.l),
            verticalArrangement = Arrangement.spacedBy(MukharSpace.m),
        ) {
            if (card.comingBack) {
                InfoChip(stringResource(R.string.deck_coming_back), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.lemma,
                        style = if (hi) MukharTheme.type.hiHeadline.copy(fontSize = 34.sp, lineHeight = 48.sp) else MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    // Pronunciation: the transliteration for Hindi, and the voice for both.
                    val sub = listOfNotNull(card.translit, posRes(card.pos, full = true)?.let { stringResource(it) }).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!hidden) SpeakerSlot(voice, hearDescription(card.lemma, card.lang), { onSpeak(card.lemma, card.lang) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(stringResource(registerRes(card.register)), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
            }
            // Both meanings: English first for English words, Hindi first for Hindi words.
            Section(stringResource(if (hi) R.string.deck_meaning_hi_word else R.string.deck_meaning_en)) {
                Text(card.meaning, style = if (hi) MukharTheme.type.hiBodyLarge else MaterialTheme.typography.bodyLarge)
            }
            Section(stringResource(if (hi) R.string.deck_meaning_en_word else R.string.deck_meaning_hi)) {
                Text(card.gloss, style = if (hi) MaterialTheme.typography.bodyLarge else MukharTheme.type.hiBodyLarge)
            }
            Section(stringResource(R.string.deck_example)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("“${card.example}”", style = if (hi) MukharTheme.type.hiBodyLarge else MukharTheme.type.quote)
                        card.exampleGloss?.let {
                            Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!hidden) SpeakerSlot(voice, stringResource(R.string.deck_hear_example_cd), { onSpeak(card.example, card.lang) })
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(title)
        content()
    }
}

@Composable
private fun GoalMet(deck: DeckState, actions: DeckActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MukharSpace.gutter).testTag(TAG_DECK_GOAL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtCircle(Icons.Outlined.Celebration)
        Text(
            stringResource(R.string.deck_goal_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp).semantics { heading() },
        )
        Text(
            pluralStringResource(R.plurals.deck_goal_body, deck.decidedToday, deck.decidedToday),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(onClick = actions.onOpenGym, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 52.dp)) {
            Text(stringResource(R.string.deck_open_gym))
        }
        OutlinedButton(onClick = actions.onKeepGoing, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = MukharSpace.touch).testTag(TAG_DECK_KEEP_GOING)) {
            Text(stringResource(R.string.deck_keep_going))
        }
        TextButton(onClick = actions.onBack, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.deck_back_to_words)) }
    }
}

@Composable
private fun Empty(actions: DeckActions) {
    Column(
        Modifier.fillMaxSize().padding(MukharSpace.gutter).testTag(TAG_DECK_EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtCircle(Icons.AutoMirrored.Outlined.MenuBook)
        Text(
            stringResource(R.string.deck_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp).semantics { heading() },
        )
        Text(
            stringResource(R.string.deck_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        TextButton(onClick = actions.onBack, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.deck_back_to_words)) }
    }
}

private const val EXIT_MS = 220
private const val BACK_MS = 180
private const val SWIPE_FRACTION = 0.3f
private const val MAX_TILT = 12f

const val TAG_DECK = "deck"
const val TAG_DECK_PROGRESS = "deck_progress"
const val TAG_DECK_CARD = "deck_card"
const val TAG_DECK_DONE = "deck_done"
const val TAG_DECK_STAR = "deck_star"
const val TAG_DECK_LATER = "deck_later"
const val TAG_DECK_DISCARD = "deck_discard"
const val TAG_DECK_GOAL = "deck_goal"
const val TAG_DECK_KEEP_GOING = "deck_keep_going"
const val TAG_DECK_EMPTY = "deck_empty"
