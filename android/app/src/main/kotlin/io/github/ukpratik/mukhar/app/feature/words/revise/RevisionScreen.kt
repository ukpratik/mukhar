package io.github.ukpratik.mukhar.app.feature.words.revise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharAppBar
import io.github.ukpratik.mukhar.app.feature.talk.common.reducedMotion
import io.github.ukpratik.mukhar.app.feature.words.SpeakerSlot
import io.github.ukpratik.mukhar.app.feature.words.deck.ActionRow
import io.github.ukpratik.mukhar.app.feature.words.deck.RoundAction
import io.github.ukpratik.mukhar.app.feature.words.deck.RoundStyle
import io.github.ukpratik.mukhar.app.feature.words.deck.SnackbarSpace
import io.github.ukpratik.mukhar.app.feature.words.deck.TodayCounts
import io.github.ukpratik.mukhar.app.feature.words.deck.WordCard
import io.github.ukpratik.mukhar.app.feature.words.hearDescription
import io.github.ukpratik.mukhar.app.feature.words.posRes
import io.github.ukpratik.mukhar.app.nav.RevisionRoute
import io.github.ukpratik.mukhar.data.repo.DeckCard
import io.github.ukpratik.mukhar.data.repo.ReviewCard
import io.github.ukpratik.mukhar.data.repo.ReviewMode
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.data.tts.Speaker
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.revise.CardType
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class RevisionDone(val total: Int, val knew: Int, val dueTomorrow: Int)

data class RevisionUiState(
    val loading: Boolean = true,
    val cards: List<ReviewCard> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val knew: Int = 0,
    val voices: Map<Lang, Boolean> = emptyMap(),
    /** 5 word cards practised today: the streak day counts (D-055). */
    val countsToday: Boolean = false,
    /** Set when the last card is answered (or there was nothing to revise). */
    val done: RevisionDone? = null,
) {
    val card: ReviewCard? get() = cards.getOrNull(index)
    val total: Int get() = cards.size
}

/**
 * Words → Revise (D-055; replaces the Word Gym): learnt words one card at a time. The word (or its meaning) first,
 * tap to see the answer, then Knew it (FSRS Good) or Again (FSRS Again).
 */
@HiltViewModel
class RevisionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val words: WordRepository,
    private val speaker: Speaker,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedState.toRoute<RevisionRoute>()
    private val lang = route.lang?.let { code -> Lang.entries.firstOrNull { it.code == code } }
    private val _state = MutableStateFlow(RevisionUiState())
    val state: StateFlow<RevisionUiState> = _state.asStateFlow()
    private var shownAt: Instant = clock.now()

    init {
        speaker.warmUp()
        viewModelScope.launch { speaker.voices.collect { v -> _state.update { it.copy(voices = v) } } }
        viewModelScope.launch {
            val cards = when (route.set) {
                RevisionRoute.SET_STARRED -> words.queueDeck(lang, starred = true, null, null)
                RevisionRoute.SET_DONE -> words.queueDeck(lang, starred = false, route.from?.let(LocalDate::parse), route.to?.let(LocalDate::parse))
                else -> words.queue(ReviewMode.GYM, lang = lang)
            }
            _state.update { it.copy(loading = false, cards = cards) }
            if (cards.isEmpty()) finish()
            shownAt = clock.now()
        }
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    fun answer(knew: Boolean) {
        val card = _state.value.card ?: return
        // Advance at once; the grade is written in the background.
        _state.update { it.copy(index = it.index + 1, revealed = false, knew = it.knew + if (knew) 1 else 0) }
        val ms = Duration.between(shownAt, clock.now()).toMillis()
        shownAt = clock.now()
        viewModelScope.launch {
            val credit = words.gradeWord(card.id, knew, ms)
            if (credit != null) _state.update { it.copy(countsToday = true) }
            if (_state.value.index >= _state.value.total) finish()
        }
    }

    private suspend fun finish() {
        val tomorrow = words.dueTomorrow()
        _state.update { it.copy(done = RevisionDone(it.total, it.knew, tomorrow)) }
    }

    fun speak(text: String, lang: Lang) = speaker.speak(text, lang)
}

data class RevisionActions(
    val onBack: () -> Unit = {},
    val onReveal: () -> Unit = {},
    val onAnswer: (Boolean) -> Unit = {},
    val onSpeak: (String, Lang) -> Unit = { _, _ -> },
    val onLearn: () -> Unit = {},
)

@Composable
fun RevisionScreen(onBack: () -> Unit, onLearn: () -> Unit, vm: RevisionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    RevisionContent(state, RevisionActions(onBack, vm::reveal, vm::answer, vm::speak, onLearn))
}

@Composable
fun RevisionContent(state: RevisionUiState, actions: RevisionActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(TAG_REVISION)) {
        MukharAppBar(stringResource(R.string.revision_title), stringResource(R.string.deck_back_cd), actions.onBack)
        if (state.loading) return@Column
        val done = state.done
        val card = state.card
        when {
            done != null && done.total == 0 -> Finished(state, actions, empty = true)
            done != null || card == null -> Finished(state, actions, empty = false)
            else -> {
                Progress(state)
                Card(state, card, actions)
            }
        }
    }
}

@Composable
private fun Progress(state: RevisionUiState) {
    val n = (state.index + 1).coerceAtMost(state.total)
    val counts = stringResource(R.string.deck_today_counts)
    val a11y = stringResource(R.string.revision_progress_a11y, n, state.total) + if (state.countsToday) ". $counts" else ""
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.s)
            .semantics(mergeDescendants = true) { contentDescription = a11y; liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.revision_progress, n, state.total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag(TAG_REVISION_PROGRESS),
            )
            if (state.countsToday) TodayCounts()
        }
        LinearProgressIndicator(
            progress = { if (state.total == 0) 1f else state.index.toFloat() / state.total },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
private fun Card(state: RevisionUiState, card: ReviewCard, actions: RevisionActions) {
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val word = card.toDeckCard()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val offset = remember(card.id, state.index) { Animatable(0f) }
        val leaving = remember(card.id, state.index) { mutableStateOf(false) }
        fun answer(knew: Boolean) {
            if (leaving.value) return
            leaving.value = true
            scope.launch {
                if (!still) offset.animateTo(if (knew) width * 1.3f else -width * 1.3f, tween(200))
                actions.onAnswer(knew)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = MukharSpace.gutter)) {
                val swipe = Modifier
                    .graphicsLayer { translationX = offset.value; rotationZ = offset.value / width * 10f }
                    .pointerInput(card.id, state.index, state.revealed) {
                        // Swiping answers only once the answer is showing.
                        if (!state.revealed) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    offset.value > width * 0.3f -> answer(true)
                                    offset.value < -width * 0.3f -> answer(false)
                                    else -> scope.launch { offset.animateTo(0f, tween(180)) }
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } },
                        ) { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + dx) }
                        }
                    }
                if (state.revealed || word == null) {
                    if (word != null) {
                        WordCard(word, state.voices[word.lang] == true, actions.onSpeak, swipe.testTag(TAG_REVISION_ANSWER))
                    } else {
                        Prompt(card.back, null, card.lang, voice = false, actions, swipe)
                    }
                } else {
                    val meaningFirst = card.type == CardType.MEANING_WORD
                    Prompt(
                        text = if (meaningFirst) word.meaning else word.lemma,
                        sub = if (meaningFirst) word.gloss else listOfNotNull(word.translit, posRes(word.pos, full = true)?.let { stringResource(it) }).joinToString(" · ").ifEmpty { null },
                        lang = word.lang,
                        voice = !meaningFirst && state.voices[word.lang] == true,
                        actions = actions,
                        modifier = swipe,
                        question = stringResource(if (meaningFirst) R.string.revision_ask_word else R.string.revision_ask_meaning),
                        big = !meaningFirst,
                    )
                }
            }
            SnackbarSpace(null)
            if (state.revealed) {
                ActionRow {
                    RoundAction(
                        Icons.Rounded.Replay, stringResource(R.string.revision_again), stringResource(R.string.revision_again_cd, word?.lemma.orEmpty()),
                        RoundStyle.OUTLINED, { answer(false) }, Modifier.weight(1f).testTag(TAG_REVISION_AGAIN),
                    )
                    RoundAction(
                        Icons.Rounded.Check, stringResource(R.string.revision_knew), stringResource(R.string.revision_knew_cd, word?.lemma.orEmpty()),
                        RoundStyle.PRIMARY, { answer(true) }, Modifier.weight(1f).testTag(TAG_REVISION_KNEW), size = 68.dp,
                        tint = MukharTheme.colors.accent,
                    )
                }
            } else {
                Button(
                    onClick = actions.onReveal,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MukharSpace.gutter).padding(top = 4.dp, bottom = 16.dp)
                        .heightIn(min = 56.dp).testTag(TAG_REVISION_REVEAL),
                ) {
                    Text(stringResource(if (card.type == CardType.MEANING_WORD) R.string.revision_show_word else R.string.revision_show_meaning))
                }
            }
        }
    }
}

/** The question side: the word (or its meaning) and what to recall. Tapping it shows the answer. */
@Composable
private fun Prompt(
    text: String,
    sub: String?,
    lang: Lang,
    voice: Boolean,
    actions: RevisionActions,
    modifier: Modifier,
    question: String? = null,
    big: Boolean = true,
) {
    val hi = lang == Lang.HI
    MukharCard(modifier.fillMaxSize().testTag(TAG_REVISION_PROMPT), onClick = actions.onReveal) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MukharSpace.l),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            question?.let { Eyebrow(it, Modifier.padding(bottom = MukharSpace.m)) }
            Text(
                text,
                style = when {
                    big && hi -> MukharTheme.type.hiHeadline.copy(fontSize = 38.sp, lineHeight = 54.sp)
                    big -> MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 46.sp)
                    hi -> MukharTheme.type.hiBodyLarge.copy(fontSize = 22.sp, lineHeight = 34.sp)
                    else -> MaterialTheme.typography.headlineSmall
                },
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            sub?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (voice) {
                SpeakerSlot(true, hearDescription(text, lang), { actions.onSpeak(text, lang) }, Modifier.padding(top = MukharSpace.m))
            }
        }
    }
}

@Composable
private fun Finished(state: RevisionUiState, actions: RevisionActions, empty: Boolean) {
    val done = state.done
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MukharSpace.gutter).testTag(if (empty) TAG_REVISION_EMPTY else TAG_REVISION_DONE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtCircle(Icons.Outlined.TaskAlt)
        Text(
            stringResource(if (empty) R.string.revision_empty_title else R.string.revision_done_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp).semantics { heading() },
        )
        val body = when {
            done == null -> ""
            empty -> pluralStringResource(R.plurals.revision_empty_body, done.dueTomorrow, done.dueTomorrow)
            else -> pluralStringResource(R.plurals.revision_done_body, done.total, done.total, done.knew)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (!empty && done != null && done.dueTomorrow > 0) {
            Text(
                pluralStringResource(R.plurals.revision_tomorrow, done.dueTomorrow, done.dueTomorrow),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.countsToday) TodayCounts(Modifier.padding(top = MukharSpace.m))
        Button(onClick = actions.onLearn, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 52.dp).testTag(TAG_REVISION_LEARN)) {
            Text(stringResource(R.string.revision_learn))
        }
        TextButton(onClick = actions.onBack, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.deck_back_to_words)) }
    }
}

/** The card's word as a deck card, so the answer side looks exactly like a new word. Null for a card without a word. */
private fun ReviewCard.toDeckCard(): DeckCard? {
    val w = word ?: return null
    return DeckCard(
        id = w.id, lang = w.lang, lemma = w.lemma, translit = w.translit, pos = w.pos, register = w.register, meaning = w.meaning,
        gloss = w.gloss.orEmpty(), example = w.example, exampleGloss = w.exampleGloss,
    )
}

const val TAG_REVISION = "revision"
const val TAG_REVISION_PROGRESS = "revision_progress"
const val TAG_REVISION_PROMPT = "revision_prompt"
const val TAG_REVISION_ANSWER = "revision_answer"
const val TAG_REVISION_REVEAL = "revision_reveal"
const val TAG_REVISION_AGAIN = "revision_again"
const val TAG_REVISION_KNEW = "revision_knew"
const val TAG_REVISION_DONE = "revision_done"
const val TAG_REVISION_EMPTY = "revision_empty"
const val TAG_REVISION_LEARN = "revision_learn"
