package com.vaak.app.feature.words

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vaak.app.R
import com.vaak.app.feature.onboarding.Eyebrow
import com.vaak.app.feature.talk.common.ArtCircle
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.repo.WordItem
import com.vaak.data.repo.WordRepository
import com.vaak.data.repo.WordSource
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

enum class WordsSection { BANK, LINES }

enum class SourceFilter { ALL, TALKS, CURATED }

data class SavedLineUi(val id: String, val original: String, val better: String, val lang: Lang)

data class WordBankUiState(
    val loading: Boolean = true,
    val section: WordsSection = WordsSection.BANK,
    val words: List<WordItem> = emptyList(),
    val source: SourceFilter = SourceFilter.ALL,
    val tracks: Set<String> = emptySet(),
    /** One chip per goal track the user picked (S14 state 2). */
    val trackChips: List<String> = emptyList(),
    val gymDue: Int = 0,
    val lines: List<SavedLineUi> = emptyList(),
    val dailyCount: Int = 3,
) {
    val visible: List<WordItem>
        get() = words.filter { w ->
            when (source) {
                SourceFilter.ALL -> true
                SourceFilter.TALKS -> w.source == WordSource.TALK
                SourceFilter.CURATED -> w.source == WordSource.CURATED
            } && (tracks.isEmpty() || w.tracks.any { it in tracks })
        }
    val talkCount: Int get() = words.count { it.source == WordSource.TALK }
    val gymMinutes: Int get() = ((gymDue + 3) / 4).coerceIn(1, 5)
}

sealed interface WordBankEvent {
    data class Section(val section: WordsSection) : WordBankEvent
    data class Source(val filter: SourceFilter) : WordBankEvent
    data class ToggleTrack(val track: String) : WordBankEvent
}

/** S14 (US-020, US-021): words from the user's talks first, then curated words; filters; saved lines. */
@HiltViewModel
class WordBankViewModel @Inject constructor(words: WordRepository, settings: SettingsStore) : ViewModel() {
    private val ui = MutableStateFlow(WordBankUiState(loading = false))

    val state: StateFlow<WordBankUiState> = combine(
        ui, words.observeBank(), words.observeGymDue(), words.observeSavedLines(), settings.settings,
    ) { u, bank, due, lines, s ->
        u.copy(
            loading = false,
            words = bank,
            gymDue = due,
            lines = lines.map { SavedLineUi(it.id, it.front, it.back, Lang.fromCode(it.lang)) },
            trackChips = com.vaak.data.prefs.GoalTrack.entries.filter { it in s.tracks }.map { it.key },
            dailyCount = s.dailyNewWords,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordBankUiState())

    fun onEvent(e: WordBankEvent) {
        when (e) {
            is WordBankEvent.Section -> ui.update { it.copy(section = e.section) }
            is WordBankEvent.Source -> ui.update { it.copy(source = e.filter) }
            is WordBankEvent.ToggleTrack -> ui.update { it.copy(tracks = if (e.track in it.tracks) it.tracks - e.track else it.tracks + e.track) }
        }
    }

    init {
        // The day's words are picked on first open of the day (Today usually does it first).
        viewModelScope.launch { words.ensureDailyWords() }
    }
}

data class WordBankActions(
    val onEvent: (WordBankEvent) -> Unit = {},
    val onOpenGym: () -> Unit = {},
    val onOpenWord: (String) -> Unit = {},
    val onTalk: () -> Unit = {},
)

@Composable
fun WordsScreen(onOpenGym: () -> Unit, onOpenWord: (String) -> Unit, onTalk: () -> Unit, vm: WordBankViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    WordBankContent(state, WordBankActions(vm::onEvent, onOpenGym, onOpenWord, onTalk))
}

/** S14 states 1 (empty) and 2 (list with filters), plus the Saved lines segment. Stateless. */
@Composable
fun WordBankContent(state: WordBankUiState, actions: WordBankActions, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = VaakSpace.gutter).padding(top = VaakSpace.m, bottom = VaakSpace.xxl).testTag(TAG_WORD_BANK),
        verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        if (state.loading) return@Column
        Text(
            stringResource(R.string.words_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        val sectionLabel = stringResource(R.string.words_section_a11y)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = sectionLabel; isTraversalGroup = true }) {
            WordsSection.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = state.section == s,
                    onClick = { actions.onEvent(WordBankEvent.Section(s)) },
                    shape = SegmentedButtonDefaults.itemShape(i, WordsSection.entries.size),
                ) { Text(stringResource(if (s == WordsSection.BANK) R.string.words_seg_bank else R.string.words_seg_lines)) }
            }
        }
        when (state.section) {
            WordsSection.BANK -> Bank(state, actions)
            WordsSection.LINES -> Lines(state)
        }
    }
}

@Composable
private fun GymRow(state: WordBankUiState, onOpen: () -> Unit) {
    val due = state.gymDue
    val a11y = if (due > 0) {
        stringResource(R.string.words_gym_a11y, due, state.gymMinutes)
    } else {
        stringResource(R.string.words_gym_nothing_a11y)
    }
    // One sentence; the click is declared in the clearing block so it's kept with the label.
    VaakCard(
        Modifier.fillMaxWidth().testTag(TAG_GYM_ROW).clearAndSetSemantics {
            contentDescription = a11y
            if (due > 0) {
                role = Role.Button
                onClick { onOpen(); true }
            }
        },
        onClick = if (due > 0) onOpen else null,
    ) {
        Row(Modifier.padding(VaakSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp).clip(MaterialTheme.shapes.medium)
                    .background(if (due > 0) VaakTheme.colors.accentTint else MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (due > 0) {
                    Text(due.toString(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = VaakTheme.colors.accentInk)
                } else {
                    Icon(Icons.Outlined.FitnessCenter, null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (due > 0) pluralStringResource(R.plurals.words_gym_due, due, due) else stringResource(R.string.words_gym),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                )
                Text(
                    if (due > 0) stringResource(R.string.words_gym_due_sub, state.gymMinutes) else stringResource(if (state.words.isEmpty()) R.string.words_gym_nothing else R.string.words_gym_caught_up),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (due > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bank(state: WordBankUiState, actions: WordBankActions) {
    GymRow(state, actions.onOpenGym)
    if (state.words.isEmpty()) {
        Empty(state, actions.onTalk)
        return
    }
    val filterLabel = stringResource(R.string.words_filter_a11y)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = filterLabel; isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(VaakSpace.s),
    ) {
        SourceFilter.entries.forEach { f ->
            FilterChip(
                selected = state.source == f,
                onClick = { actions.onEvent(WordBankEvent.Source(f)) },
                leadingIcon = selectedCheck(state.source == f),
                label = {
                    Text(
                        stringResource(
                            when (f) {
                                SourceFilter.ALL -> R.string.words_filter_all
                                SourceFilter.TALKS -> R.string.words_filter_talks
                                SourceFilter.CURATED -> R.string.words_filter_curated
                            },
                        ),
                    )
                },
            )
        }
        state.trackChips.forEach { t ->
            goalTrackRes(t)?.let { res ->
                FilterChip(
                    selected = t in state.tracks,
                    onClick = { actions.onEvent(WordBankEvent.ToggleTrack(t)) },
                    label = { Text(stringResource(res)) },
                    leadingIcon = selectedCheck(t in state.tracks),
                )
            }
        }
    }
    val visible = state.visible
    Text(
        when (state.source) {
            SourceFilter.TALKS -> stringResource(R.string.words_count_talks, visible.size, state.words.size)
            SourceFilter.CURATED -> stringResource(R.string.words_count_curated, visible.size, state.words.size)
            SourceFilter.ALL -> stringResource(R.string.words_count_all, visible.size)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp).testTag(TAG_WORDS_COUNT).semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (visible.isEmpty()) {
        Text(stringResource(R.string.words_none_match), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    VaakCard(Modifier.fillMaxWidth()) {
        visible.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WordRow(w) { actions.onOpenWord(w.id) }
        }
    }
}

/** Selection shown by a check, not by colour alone (design-system §10.3). */
private fun selectedCheck(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordRow(w: WordItem, onClick: () -> Unit) {
    val meta = posAndRegister(w)
    val status = statusLabel(w)
    val a11y = stringResource(R.string.word_row_a11y, w.lemma, meta, w.meaning, status)
    Row(
        Modifier.fillMaxWidth().testTag("word_${w.id}").clickable(role = Role.Button, onClick = onClick).clearAndSetSemantics { contentDescription = a11y }
            .padding(horizontal = VaakSpace.l, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // Wraps the meta under the lemma at large text instead of squeezing it.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.Bottom) {
                Text(
                    w.lemma,
                    style = if (w.lang == Lang.HI) VaakTheme.type.hiBodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (w.lang == Lang.HI) w.gloss ?: w.meaning else w.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (w.source == WordSource.TALK) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.accentInk, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.word_src_talk, shortDay(w.addedAt.atZone(ZoneId.systemDefault()).toLocalDate())),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = VaakTheme.colors.accentInk,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(w)
    }
}

@Composable
private fun Empty(state: WordBankUiState, onTalk: () -> Unit) {
    val names = state.trackChips.mapNotNull { goalTrackRes(it) }.map { stringResource(it) }
    val goals = when (names.size) {
        0 -> stringResource(R.string.words_empty_goals)
        1 -> names[0]
        else -> stringResource(R.string.words_and, names.dropLast(1).joinToString(", "), names.last())
    }
    Column(Modifier.fillMaxWidth().padding(top = VaakSpace.xl).testTag(TAG_WORDS_EMPTY), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtCircle(Icons.AutoMirrored.Outlined.MenuBook)
        Text(
            stringResource(R.string.words_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 18.dp).semantics { heading() },
        )
        Text(
            pluralStringResource(R.plurals.words_empty_body, state.dailyCount, state.dailyCount, goals),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(onClick = onTalk, modifier = Modifier.padding(top = 16.dp).heightIn(min = VaakSpace.touch)) {
            Icon(Icons.Rounded.Mic, null, tint = VaakTheme.colors.accent)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.words_empty_cta))
        }
        Text(
            stringResource(R.string.words_empty_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Lines(state: WordBankUiState) {
    if (state.lines.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = VaakSpace.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            ArtCircle(Icons.Outlined.BookmarkAdded)
            Text(
                stringResource(R.string.words_lines_empty_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.words_lines_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }
    VaakCard(Modifier.fillMaxWidth()) {
        state.lines.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = VaakSpace.l, vertical = 14.dp).semantics(mergeDescendants = true) {}) {
                Text(
                    "“${l.better}”",
                    style = if (l.lang == Lang.HI) VaakTheme.type.hiBodyMedium else VaakTheme.type.quote,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Eyebrow(stringResource(R.string.line_better_of, l.original), Modifier.padding(top = 4.dp))
            }
        }
    }
}

const val TAG_WORD_BANK = "word_bank"
const val TAG_GYM_ROW = "word_gym_row"
const val TAG_WORDS_EMPTY = "words_empty"
const val TAG_WORDS_COUNT = "words_count"
