package com.vaak.app.feature.words

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vaak.app.R
import com.vaak.app.feature.onboarding.Eyebrow
import com.vaak.app.feature.talk.common.InfoChip
import com.vaak.app.feature.talk.common.VaakAppBar
import com.vaak.app.nav.WordDetailRoute
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.repo.WordItem
import com.vaak.data.repo.WordRepository
import com.vaak.data.repo.WordSource
import com.vaak.data.tts.Speaker
import com.vaak.data.tts.SpeakerUi
import com.vaak.data.tts.TtsSupport
import com.vaak.domain.Clock
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class WordDetailUiState(
    val loading: Boolean = true,
    val word: WordItem? = null,
    val speaker: SpeakerUi = SpeakerUi(showSpeaker = false, showVoiceTip = false),
    val now: Instant = Instant.EPOCH,
)

/** S14 states 3 and 4: word detail with TTS (hidden when no voice, with a one-time tip). */
@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val words: WordRepository,
    private val settings: SettingsStore,
    private val speaker: Speaker,
    private val clock: Clock,
) : ViewModel() {
    val id = savedState.toRoute<WordDetailRoute>().wordId

    val state: StateFlow<WordDetailUiState> = combine(words.observeWord(id), speaker.voices, settings.ttsTipShown) { w, voices, tips ->
        val lang = w?.lang ?: Lang.EN
        WordDetailUiState(false, w, TtsSupport.ui(voices[lang], lang.code in tips), clock.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordDetailUiState())

    init {
        speaker.warmUp()
    }

    fun speak() {
        state.value.word?.let { speaker.speak(it.lemma, it.lang) }
    }

    fun dismissTip() {
        val lang = state.value.word?.lang ?: return
        viewModelScope.launch { settings.markTtsTipShown(lang) }
    }

    fun setInGym(inGym: Boolean) {
        viewModelScope.launch { words.setInGym(id, inGym) }
    }
}

data class WordDetailActions(
    val onBack: () -> Unit = {},
    val onSpeak: () -> Unit = {},
    val onOpenVoiceSettings: () -> Unit = {},
    val onDismissTip: () -> Unit = {},
    val onPractise: () -> Unit = {},
    val onSeeLine: (String) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onAddBack: () -> Unit = {},
)

@Composable
fun WordDetailScreen(onBack: () -> Unit, onPractise: (String) -> Unit, onSeeLine: (String) -> Unit, vm: WordDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removed = stringResource(R.string.word_removed)
    val undo = stringResource(R.string.undo)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        WordDetailContent(
            state,
            WordDetailActions(
                onBack = onBack,
                onSpeak = vm::speak,
                onOpenVoiceSettings = {
                    vm.dismissTip()
                    context.openVoiceSettings()
                },
                onDismissTip = vm::dismissTip,
                onPractise = { onPractise(vm.id) },
                onSeeLine = onSeeLine,
                // "Remove from Word Gym" shows a toast with Undo, no dialog (S14 notes).
                onRemove = {
                    vm.setInGym(false)
                    scope.launch {
                        if (snackbar.showSnackbar(removed, undo) == SnackbarResult.ActionPerformed) vm.setInGym(true)
                    }
                },
                onAddBack = { vm.setInGym(true) },
            ),
            Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDetailContent(state: WordDetailUiState, actions: WordDetailActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VaakAppBar(stringResource(R.string.word_detail_title), stringResource(R.string.word_back_cd), actions.onBack)
        val w = state.word ?: return@Column
        val hi = w.lang == Lang.HI
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).padding(bottom = VaakSpace.xxl)
                .testTag(TAG_WORD_DETAIL),
            verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        w.lemma,
                        style = if (hi) VaakTheme.type.hiHeadline.copy(fontSize = 34.sp, lineHeight = 48.sp) else MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    val sub = listOfNotNull(w.translit, posRes(w.pos, full = true)?.let { stringResource(it) }).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SpeakerSlot(state.speaker.showSpeaker, hearDescription(w.lemma, w.lang), actions.onSpeak)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val plain = MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                posRes(w.pos, full = !hi, hindi = hi)?.let { res ->
                    val label = if (hi) "${stringResource(res)} · ${stringResource(posRes(w.pos)!!)}" else stringResource(res)
                    InfoChip(label, plain.first, plain.second)
                }
                InfoChip(stringResource(registerRes(w.register)), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                w.tracks.firstOrNull()?.let(::goalTrackRes)?.let { res ->
                    val track = stringResource(res)
                    InfoChip(if (hi) stringResource(R.string.word_hindi_track, track) else track, plain.first, plain.second)
                }
            }
            if (state.speaker.showVoiceTip) VoiceTip(w.lang, actions.onOpenVoiceSettings, actions.onDismissTip)
            Block(stringResource(R.string.word_meaning)) {
                Text(w.meaning, style = if (hi) VaakTheme.type.hiBodyLarge else MaterialTheme.typography.bodyLarge)
                w.gloss?.let {
                    Text(
                        it,
                        style = if (hi) MaterialTheme.typography.bodySmall else VaakTheme.type.hiBodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Block(stringResource(R.string.word_example)) {
                Text("“${w.example}”", style = if (hi) VaakTheme.type.hiBodyLarge else VaakTheme.type.quote)
                w.exampleGloss?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val talkId = w.sourceSessionId
            if (w.source == WordSource.TALK && talkId != null) {
                VaakCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = VaakSpace.l, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.accentInk, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.word_from_talk, shortDay(w.addedAt.atZone(ZoneId.systemDefault()).toLocalDate())),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { actions.onSeeLine(talkId) }) { Text(stringResource(R.string.word_see_line)) }
                    }
                }
            }
            if (!hi) {
                Row(horizontalArrangement = Arrangement.spacedBy(VaakSpace.m)) {
                    Stat(stringResource(R.string.word_status), statusLabel(w), Modifier.weight(1f))
                    Stat(stringResource(R.string.word_next_review), nextReview(w, state.now), Modifier.weight(1f))
                }
            }
            if (w.inGym) {
                Button(onClick = actions.onPractise, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(TAG_PRACTISE)) {
                    Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.word_practise))
                }
                TextButton(onClick = actions.onRemove, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.word_remove)) }
            } else {
                TextButton(onClick = actions.onAddBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.word_add_back)) }
            }
        }
    }
}

@Composable
private fun nextReview(w: WordItem, now: Instant): String {
    val next = w.nextReview ?: return stringResource(R.string.word_next_first)
    val days = Duration.between(now, next).toDays().toInt()
    return if (days <= 0) stringResource(R.string.word_next_today) else pluralStringResource(R.plurals.word_next_in, days, days)
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    VaakCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(VaakSpace.l), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(title)
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    VaakCard(modifier) {
        Box(Modifier.padding(VaakSpace.l)) {
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

const val TAG_WORD_DETAIL = "word_detail"
const val TAG_PRACTISE = "word_practise"
