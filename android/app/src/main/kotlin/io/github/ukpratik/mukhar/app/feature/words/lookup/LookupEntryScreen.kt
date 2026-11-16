package io.github.ukpratik.mukhar.app.feature.words.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.Eyebrow
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharAppBar
import io.github.ukpratik.mukhar.app.feature.words.SpeakerSlot
import io.github.ukpratik.mukhar.app.feature.words.hearDescription
import io.github.ukpratik.mukhar.app.feature.words.posRes
import io.github.ukpratik.mukhar.app.feature.words.registerRes
import io.github.ukpratik.mukhar.app.nav.LookupEntryRoute
import io.github.ukpratik.mukhar.data.byok.WordExplanation
import io.github.ukpratik.mukhar.data.dictionary.DictionaryRepository
import io.github.ukpratik.mukhar.data.dictionary.ExplainResult
import io.github.ukpratik.mukhar.data.dictionary.LookupEntry
import io.github.ukpratik.mukhar.data.ideas.IdeasFailure
import io.github.ukpratik.mukhar.data.repo.LookupStatus
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.data.tts.Speaker
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The AI part of the page: nothing asked yet, asking, the answer, or why it failed. */
sealed interface AiUi {
    data object Idle : AiUi
    data object Loading : AiUi
    data class Done(val explanation: WordExplanation) : AiUi
    data class Failed(val reason: IdeasFailure) : AiUi
}

data class LookupEntryUiState(
    val loading: Boolean = true,
    val entry: LookupEntry? = null,
    val status: LookupStatus = LookupStatus(saved = false, starred = false),
    val voice: Boolean = false,
    val hasKey: Boolean = false,
    val ai: AiUi = AiUi.Idle,
) {
    /** Something to save: Mukhar's card, a dictionary meaning, or (for a word only the AI knows) its answer. */
    val canSave: Boolean get() = entry?.toLookupWord((ai as? AiUi.Done)?.explanation) != null
}

/** D-056: a looked-up word's page. */
@HiltViewModel
class LookupEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dictionary: DictionaryRepository,
    private val words: WordRepository,
    private val speaker: Speaker,
) : ViewModel() {
    private val route = savedState.toRoute<LookupEntryRoute>()
    private val entry = MutableStateFlow<Pair<Boolean, LookupEntry?>>(true to null)
    private val ai = MutableStateFlow<AiUi>(AiUi.Idle)
    private val hasKey = MutableStateFlow(false)

    val state: StateFlow<LookupEntryUiState> = combine(entry, words.observeLookupStatus(route.id), speaker.voices, ai, hasKey) { (loading, e), status, voices, a, k ->
        LookupEntryUiState(loading, e, status, voices[e?.lang ?: Lang.EN] == true, k, a)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LookupEntryUiState())

    init {
        speaker.warmUp()
        viewModelScope.launch {
            val found = dictionary.entry(route.id)
                ?: route.word?.let { w -> LookupEntry.unknown(if (route.id.startsWith("hi:")) Lang.HI else Lang.EN, w) }
            entry.value = false to found
            hasKey.value = dictionary.hasKey()
            found?.let { dictionary.cachedExplanation(it.id) }?.let { ai.value = AiUi.Done(it) }
        }
    }

    fun speak(text: String) {
        entry.value.second?.let { speaker.speak(text, it.lang) }
    }

    fun explain() {
        val e = entry.value.second ?: return
        if (ai.value == AiUi.Loading) return
        ai.value = AiUi.Loading
        viewModelScope.launch {
            ai.value = when (val r = dictionary.explain(e)) {
                is ExplainResult.Ok -> AiUi.Done(r.explanation)
                ExplainResult.NoKey -> AiUi.Idle.also { hasKey.value = false }
                is ExplainResult.Failed -> AiUi.Failed(r.reason)
            }
        }
    }

    /** Add to my words (not starred), or set the star (adding the word if needed). Returns false when there's nothing to save. */
    fun save(starred: Boolean): Boolean {
        val word = entry.value.second?.toLookupWord((ai.value as? AiUi.Done)?.explanation) ?: return false
        viewModelScope.launch { words.saveLookup(word, starred) }
        return true
    }
}

data class LookupEntryActions(
    val onBack: () -> Unit = {},
    val onSpeak: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onStar: (Boolean) -> Unit = {},
    val onOpenSaved: () -> Unit = {},
    val onExplain: () -> Unit = {},
    val onAddKey: () -> Unit = {},
    /** Another word to look up: a Hindi word for an English one, a synonym, the AI's "did you mean". */
    val onLookUp: (Lang, String) -> Unit = { _, _ -> },
)

@Composable
fun LookupEntryScreen(
    onBack: () -> Unit,
    onOpenSaved: (String) -> Unit,
    onAddKey: () -> Unit,
    onLookUp: (Lang, String) -> Unit,
    vm: LookupEntryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val added = stringResource(R.string.entry_added_toast)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LookupEntryContent(
            state,
            LookupEntryActions(
                onBack = onBack,
                onSpeak = vm::speak,
                onAdd = { if (vm.save(starred = false)) scope.launch { snackbar.showSnackbar(added) } },
                onStar = { star -> if (vm.save(star) && star && !state.status.saved) scope.launch { snackbar.showSnackbar(added) } },
                onOpenSaved = { state.entry?.let { onOpenSaved(it.id) } },
                onExplain = vm::explain,
                onAddKey = onAddKey,
                onLookUp = onLookUp,
            ),
            Modifier.padding(padding),
        )
    }
}

/** Stateless word page: the word, Mukhar's card, dictionary meanings, Hindi words, stronger words, the AI, and Add/Star. */
@Composable
fun LookupEntryContent(state: LookupEntryUiState, actions: LookupEntryActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(TAG_ENTRY)) {
        MukharAppBar(stringResource(R.string.entry_title), stringResource(R.string.lookup_back_cd), actions.onBack)
        if (state.loading) return@Column
        val e = state.entry
        if (e == null) {
            Text(
                stringResource(R.string.entry_not_found),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(MukharSpace.gutter).testTag(TAG_ENTRY_NOT_FOUND),
            )
            return@Column
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.l),
            verticalArrangement = Arrangement.spacedBy(MukharSpace.m),
        ) {
            Header(e, state, actions)
            e.curated?.let { MukharWordCard(e, it, state.voice, actions) }
            HindiWords(e, actions)
            DictionaryMeanings(e)
            Stronger(e)
            Ai(state, actions)
            Sources(e, state.ai is AiUi.Done)
        }
        SaveBar(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(e: LookupEntry, state: LookupEntryUiState, actions: LookupEntryActions) {
    val hi = e.lang == Lang.HI
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                e.word,
                style = if (hi) MukharTheme.type.hiHeadline.copy(fontSize = 34.sp, lineHeight = 48.sp) else MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }.testTag(TAG_ENTRY_WORD),
            )
            val sub = listOfNotNull(e.translit, e.ipa?.let { "/$it/" }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpeakerSlot(state.voice, hearDescription(e.word, e.lang), { actions.onSpeak(e.word) })
    }
    val pos = (e.curated?.pos ?: e.dict?.senses?.firstOrNull()?.pos)?.let { posRes(it, full = true) }
    if (e.curated != null || pos != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (e.curated != null) InfoChip(stringResource(R.string.lookup_mukhar_badge), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
            pos?.let { InfoChip(stringResource(it), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
            e.curated?.let { InfoChip(stringResource(registerRes(it.register)), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Mukhar's own card: the simple meaning, the other language, and an example to hear. */
@Composable
private fun MukharWordCard(e: LookupEntry, c: io.github.ukpratik.mukhar.data.content.CuratedWord, voice: Boolean, actions: LookupEntryActions) {
    val hi = e.lang == Lang.HI
    MukharCard(Modifier.fillMaxWidth().testTag(TAG_ENTRY_MUKHAR)) {
        Column(Modifier.padding(MukharSpace.l), verticalArrangement = Arrangement.spacedBy(MukharSpace.m)) {
            Section(stringResource(if (hi) R.string.deck_meaning_hi_word else R.string.deck_meaning_en)) {
                Text(c.meaning, style = if (hi) MukharTheme.type.hiBodyLarge else MaterialTheme.typography.bodyLarge)
            }
            if (c.gloss.isNotBlank()) {
                Section(stringResource(if (hi) R.string.deck_meaning_en_word else R.string.deck_meaning_hi)) {
                    Text(c.gloss, style = if (hi) MaterialTheme.typography.bodyLarge else MukharTheme.type.hiBodyLarge)
                }
            }
            Section(stringResource(R.string.deck_example)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("“${c.example}”", style = if (hi) MukharTheme.type.hiBodyLarge else MukharTheme.type.quote)
                        c.exampleGloss?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    SpeakerSlot(voice, stringResource(R.string.deck_hear_example_cd), { actions.onSpeak(c.example) })
                }
            }
        }
    }
}

/** The dictionary's meanings (numbered) and English word forms. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictionaryMeanings(e: LookupEntry) {
    val d = e.dict ?: return
    val hi = e.lang == Lang.HI
    if (d.senses.isNotEmpty()) {
        MukharCard(Modifier.fillMaxWidth().testTag(TAG_ENTRY_MEANINGS)) {
            Column(Modifier.padding(MukharSpace.l), verticalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
                Eyebrow(stringResource(if (hi) R.string.entry_in_english else R.string.entry_meanings))
                // The part of speech is in the header; per meaning only when the word is more than one (run: verb, noun).
                val mixed = d.senses.map { it.pos }.distinct().size > 1
                d.senses.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row {
                        if (d.senses.size > 1) {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (mixed) posRes(s.pos)?.let {
                                Text(stringResource(it), style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(s.definition, style = MaterialTheme.typography.bodyLarge)
                            s.example?.let { ex ->
                                Text("“$ex”", style = if (hi) MukharTheme.type.hiBodyMedium else MukharTheme.type.quote.copy(fontSize = 15.sp, lineHeight = 22.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            s.exampleGloss?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (s.synonyms.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.entry_also, s.synonyms.joinToString(", ")),
                                    style = if (hi) MukharTheme.type.hiBodyMedium else MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (d.forms.isNotEmpty() && !hi) {
                    Text(stringResource(R.string.entry_forms, d.forms.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Hindi words for an English word, right under the word: for this app's learners it's often the first thing they
 * want. Each opens its own page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HindiWords(e: LookupEntry, actions: LookupEntryActions) {
    val d = e.dict ?: return
    if (d.hindi.isNotEmpty() && e.curated == null) {
        val cd = stringResource(R.string.entry_hindi_words_cd, e.word)
        Column(Modifier.testTag(TAG_ENTRY_HINDI), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(stringResource(R.string.entry_in_hindi))
            FlowRow(Modifier.semantics { contentDescription = cd }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.hindi.forEach { w ->
                    AssistChip(onClick = { actions.onLookUp(Lang.HI, w) }, label = { Text(w, style = MukharTheme.type.hiBodyMedium) })
                }
            }
        }
    }
}

/** Mukhar's "say it better" words for this headword, when the synonym book has it. */
@Composable
private fun Stronger(e: LookupEntry) {
    if (e.better.isEmpty()) return
    val hi = e.lang == Lang.HI
    MukharCard(Modifier.fillMaxWidth().testTag(TAG_ENTRY_STRONGER)) {
        Column(Modifier.padding(MukharSpace.l), verticalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
            Eyebrow(stringResource(R.string.entry_stronger))
            e.better.forEach { a ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.word, style = if (hi) MukharTheme.type.hiBodyLarge else MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        InfoChip(stringResource(registerRes(a.register.key)), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(a.meaning, style = if (hi) MukharTheme.type.hiBodyMedium else MaterialTheme.typography.bodySmall)
                    Text("“${a.example}”", style = if (hi) MukharTheme.type.hiBodyMedium else MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Explain with AI" on the user's own key; without a key, one line pointing to Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ai(state: LookupEntryUiState, actions: LookupEntryActions) {
    val e = state.entry ?: return
    when (val ai = state.ai) {
        is AiUi.Done -> AiAnswer(e, ai.explanation, actions)
        AiUi.Loading -> Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(TAG_ENTRY_AI_LOADING), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.entry_ai_loading), style = MaterialTheme.typography.bodyMedium)
        }
        else -> {
            if (!state.hasKey) {
                Column(Modifier.testTag(TAG_ENTRY_AI_NO_KEY)) {
                    Text(stringResource(R.string.entry_ai_no_key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = actions.onAddKey, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(stringResource(R.string.entry_ai_add_key))
                    }
                }
                return
            }
            (ai as? AiUi.Failed)?.let {
                Text(
                    stringResource(failureRes(it.reason)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = actions.onExplain, modifier = Modifier.heightIn(min = MukharSpace.touch).testTag(TAG_ENTRY_AI_BUTTON)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (ai is AiUi.Failed) R.string.entry_ai_retry else R.string.entry_ai_button))
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.entry_ai_cost), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiAnswer(e: LookupEntry, x: WordExplanation, actions: LookupEntryActions) {
    val hi = e.lang == Lang.HI
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MukharTheme.colors.accentTint,
        modifier = Modifier.fillMaxWidth().testTag(TAG_ENTRY_AI),
    ) {
        Column(Modifier.padding(MukharSpace.l), verticalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MukharTheme.colors.accentInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.entry_ai_title), style = MaterialTheme.typography.labelLarge, color = MukharTheme.colors.accentInk, modifier = Modifier.semantics { heading() })
            }
            if (!x.isWord) {
                Text(stringResource(R.string.entry_ai_not_word), style = MaterialTheme.typography.bodyLarge)
                if (x.didYouMean.isNotBlank()) {
                    TextButton(onClick = { actions.onLookUp(e.lang, x.didYouMean) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(stringResource(R.string.entry_ai_did_you_mean, x.didYouMean))
                    }
                }
                return@Column
            }
            if (x.pronunciation.isNotBlank()) Text(stringResource(R.string.entry_ai_say_it, x.pronunciation), style = MaterialTheme.typography.bodyMedium)
            Section(stringResource(R.string.entry_ai_meaning_en)) { Text(x.meaningEn, style = MaterialTheme.typography.bodyLarge) }
            Section(stringResource(R.string.entry_ai_meaning_hi)) { Text(x.meaningHi, style = MukharTheme.type.hiBodyLarge) }
            if (x.examples.isNotEmpty()) {
                Section(stringResource(R.string.entry_ai_examples)) {
                    x.examples.forEach { ex ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                                Text("“${ex.sentence}”", style = if (hi) MukharTheme.type.hiBodyLarge else MukharTheme.type.quote.copy(fontSize = 15.sp, lineHeight = 22.sp))
                                if (ex.translation.isNotBlank()) {
                                    Text(ex.translation, style = if (hi) MaterialTheme.typography.bodySmall else MukharTheme.type.hiBodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (x.usageNote.isNotBlank()) Section(stringResource(R.string.entry_ai_usage)) { Text(x.usageNote, style = MaterialTheme.typography.bodyMedium) }
            if (x.synonyms.isNotEmpty()) {
                Section(stringResource(R.string.entry_ai_related)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        x.synonyms.forEach { w -> AssistChip(onClick = { actions.onLookUp(e.lang, w) }, label = { Text(w) }) }
                    }
                }
            }
            Text(stringResource(R.string.entry_ai_caution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Sources(e: LookupEntry, ai: Boolean) {
    if (e.dict == null) return
    Text(
        stringResource(if (e.lang == Lang.HI) R.string.entry_source_hi else R.string.entry_source_en),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = if (ai) 0.dp else 4.dp),
    )
}

/** Add to my words, or "In my words" (opens the word) with the star. */
@Composable
private fun SaveBar(state: LookupEntryUiState, actions: LookupEntryActions) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MukharSpace.gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.status.saved) {
                FilledTonalButton(onClick = actions.onOpenSaved, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(TAG_ENTRY_SAVED)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.entry_saved))
                }
            } else {
                Button(onClick = actions.onAdd, enabled = state.canSave, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(TAG_ENTRY_ADD)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.entry_add))
                }
            }
            Spacer(Modifier.width(8.dp))
            val starred = state.status.starred
            IconToggleButton(
                checked = starred,
                onCheckedChange = actions.onStar,
                enabled = state.canSave || state.status.saved,
                modifier = Modifier.size(MukharSpace.touch).testTag(TAG_ENTRY_STAR),
            ) {
                Icon(
                    if (starred) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(if (starred) R.string.entry_unstar_cd else R.string.entry_star_cd),
                    tint = if (starred) MukharTheme.colors.accentInk else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private fun failureRes(reason: IdeasFailure): Int = when (reason) {
    IdeasFailure.OFFLINE -> R.string.entry_ai_fail_offline
    IdeasFailure.KEY_REJECTED -> R.string.entry_ai_fail_key
    IdeasFailure.REFUSED -> R.string.entry_ai_fail_refused
    IdeasFailure.BUSY -> R.string.entry_ai_fail_busy
    IdeasFailure.BAD_RESPONSE -> R.string.entry_ai_fail_bad
    IdeasFailure.PAUSED -> R.string.entry_ai_fail_paused
}

const val TAG_ENTRY = "lookup_entry"
const val TAG_ENTRY_WORD = "lookup_entry_word"
const val TAG_ENTRY_NOT_FOUND = "lookup_entry_not_found"
const val TAG_ENTRY_MUKHAR = "lookup_entry_mukhar"
const val TAG_ENTRY_MEANINGS = "lookup_entry_meanings"
const val TAG_ENTRY_HINDI = "lookup_entry_hindi"
const val TAG_ENTRY_STRONGER = "lookup_entry_stronger"
const val TAG_ENTRY_AI = "lookup_entry_ai"
const val TAG_ENTRY_AI_BUTTON = "lookup_entry_ai_button"
const val TAG_ENTRY_AI_LOADING = "lookup_entry_ai_loading"
const val TAG_ENTRY_AI_NO_KEY = "lookup_entry_ai_no_key"
const val TAG_ENTRY_ADD = "lookup_entry_add"
const val TAG_ENTRY_SAVED = "lookup_entry_saved"
const val TAG_ENTRY_STAR = "lookup_entry_star"
