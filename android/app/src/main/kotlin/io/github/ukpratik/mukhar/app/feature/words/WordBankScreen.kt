package io.github.ukpratik.mukhar.app.feature.words

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
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
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.Eyebrow
import io.github.ukpratik.mukhar.app.feature.talk.common.ArtCircle
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.repo.WordItem
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.data.repo.WordSource
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.words.DailyWordPicker
import io.github.ukpratik.mukhar.domain.words.WordHistory
import io.github.ukpratik.mukhar.ui.text.mixedScript
import io.github.ukpratik.mukhar.ui.theme.NotoDeva
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
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

enum class WordsSection { BANK, HISTORY, LINES }

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
    val dailyCount: Int = DailyWordPicker.DEFAULT_COUNT,
    /** Practice languages; tabs show only when there are two (US-075). */
    val langs: List<Lang> = listOf(Lang.EN),
    /** The selected tab's language, or null with one language (then nothing is filtered). */
    val tab: Lang? = null,
    /** Learnt-words history for the tab (US-077), newest day first. */
    val history: List<WordRepository.HistoryDay> = emptyList(),
    /** Languages whose curated list is used up (US-076). */
    val exhausted: Set<Lang> = emptySet(),
    val today: LocalDate = LocalDate.now(),
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

    /** The language whose "you've met every word" note applies on this tab. */
    val exhaustedHere: Lang? get() = (tab ?: langs.singleOrNull())?.takeIf { it in exhausted }
}

sealed interface WordBankEvent {
    data class Section(val section: WordsSection) : WordBankEvent
    data class Source(val filter: SourceFilter) : WordBankEvent
    data class ToggleTrack(val track: String) : WordBankEvent
    data class Tab(val lang: Lang) : WordBankEvent
}

/**
 * S14 (US-020, US-021, US-075, US-077): words from the user's talks first, then curated words; filters; saved
 * lines; the learnt-words history. With both languages, a tab per language filters all of it and the Word Gym count;
 * the last tab is remembered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WordBankViewModel @Inject constructor(private val words: WordRepository, private val settings: SettingsStore) : ViewModel() {
    private val ui = MutableStateFlow(WordBankUiState(loading = false))

    private val tab = combine(settings.settings, settings.wordsTab) { s, t ->
        val langs = s.practiceLangs
        langs to (if (langs.size < 2) null else t?.takeIf { it in langs } ?: langs.first())
    }.distinctUntilChanged()

    private data class PerTab(val langs: List<Lang>, val tab: Lang?, val due: Int, val history: List<WordRepository.HistoryDay>)

    // Observing today's words keeps the day's set in step with Settings (picked, topped up or trimmed).
    private val perTab = tab.flatMapLatest { (langs, lang) ->
        combine(words.observeGymDue(lang), words.observeHistory(lang), words.observeDailyWords(words.today())) { due, history, _ ->
            PerTab(langs, lang, due, history)
        }
    }

    val state: StateFlow<WordBankUiState> = combine(
        ui, words.observeBank(), perTab, words.observeSavedLines(), combine(settings.settings, words.exhaustedLangs, ::Pair),
    ) { u, bank, t, lines, (s, exhausted) ->
        u.copy(
            loading = false,
            words = bank.filter { t.tab == null || it.lang == t.tab },
            gymDue = t.due,
            lines = lines.map { SavedLineUi(it.id, it.front, it.back, Lang.fromCode(it.lang)) }.filter { t.tab == null || it.lang == t.tab },
            trackChips = io.github.ukpratik.mukhar.data.prefs.GoalTrack.entries.filter { it in s.tracks }.map { it.key },
            dailyCount = s.dailyWordsFor(t.tab ?: t.langs.first()),
            langs = t.langs,
            tab = t.tab,
            history = t.history,
            exhausted = exhausted,
            today = words.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordBankUiState())

    fun onEvent(e: WordBankEvent) {
        when (e) {
            is WordBankEvent.Section -> ui.update { it.copy(section = e.section) }
            is WordBankEvent.Source -> ui.update { it.copy(source = e.filter) }
            is WordBankEvent.ToggleTrack -> ui.update { it.copy(tracks = if (e.track in it.tracks) it.tracks - e.track else it.tracks + e.track) }
            is WordBankEvent.Tab -> viewModelScope.launch { settings.setWordsTab(e.lang) }
        }
    }
}

data class WordBankActions(
    val onEvent: (WordBankEvent) -> Unit = {},
    /** The Word Gym for the tab's language (null = all). */
    val onOpenGym: (Lang?) -> Unit = {},
    val onOpenWord: (String) -> Unit = {},
    val onTalk: () -> Unit = {},
    /** Extra practice from History: words first shown from..to, in the tab's language. */
    val onRevise: (Lang?, LocalDate, LocalDate) -> Unit = { _, _, _ -> },
)

@Composable
fun WordsScreen(
    onOpenGym: (Lang?) -> Unit,
    onOpenWord: (String) -> Unit,
    onTalk: () -> Unit,
    onRevise: (Lang?, LocalDate, LocalDate) -> Unit = { _, _, _ -> },
    vm: WordBankViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    WordBankContent(state, WordBankActions(vm::onEvent, onOpenGym, onOpenWord, onTalk, onRevise))
}

/** S14 states 1 (empty) and 2 (list with filters), plus History and Saved lines, under optional language tabs. Stateless. */
@Composable
fun WordBankContent(state: WordBankUiState, actions: WordBankActions, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = MukharSpace.gutter).padding(top = MukharSpace.m, bottom = MukharSpace.xxl).testTag(TAG_WORD_BANK),
        verticalArrangement = Arrangement.spacedBy(MukharSpace.m),
    ) {
        if (state.loading) return@Column
        Text(
            stringResource(R.string.words_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        val tab = state.tab
        if (tab != null && state.langs.size > 1) LangTabs(state.langs, tab) { actions.onEvent(WordBankEvent.Tab(it)) }
        val sectionLabel = stringResource(R.string.words_section_a11y)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = sectionLabel; isTraversalGroup = true }) {
            WordsSection.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = state.section == s,
                    onClick = { actions.onEvent(WordBankEvent.Section(s)) },
                    shape = SegmentedButtonDefaults.itemShape(i, WordsSection.entries.size),
                    modifier = Modifier.testTag("words_seg_${s.name.lowercase()}"),
                    label = {
                        Text(
                            stringResource(
                                when (s) {
                                    WordsSection.BANK -> R.string.words_seg_bank
                                    WordsSection.HISTORY -> R.string.words_seg_history
                                    WordsSection.LINES -> R.string.words_seg_lines
                                },
                            ),
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    },
                )
            }
        }
        when (state.section) {
            WordsSection.BANK -> Bank(state, actions)
            WordsSection.HISTORY -> History(state, actions)
            WordsSection.LINES -> Lines(state)
        }
    }
}

/** US-075: English · हिंदी. A tab row (selection by underline and weight, not colour alone). */
@Composable
private fun LangTabs(langs: List<Lang>, selected: Lang, onSelect: (Lang) -> Unit) {
    val cd = stringResource(R.string.words_lang_tabs_a11y)
    PrimaryTabRow(
        selectedTabIndex = langs.indexOf(selected).coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().testTag(TAG_WORDS_TABS).semantics { contentDescription = cd },
    ) {
        langs.forEach { l ->
            val name = stringResource(if (l == Lang.HI) R.string.lang_hi_native else R.string.lang_en)
            Tab(
                selected = l == selected,
                onClick = { onSelect(l) },
                modifier = Modifier.heightIn(min = MukharSpace.touch).testTag("words_tab_${l.code}"),
                text = {
                    Text(
                        if (l == Lang.HI) mixedScript(name) else AnnotatedString(name),
                        fontFamily = if (l == Lang.HI) NotoDeva else null,
                        fontWeight = if (l == selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }
    }
}

/** US-077: words by the day they were first shown, with extra practice for a day or the week. */
@Composable
private fun History(state: WordBankUiState, actions: WordBankActions) {
    state.exhaustedHere?.let { ExhaustedNote(it) }
    if (state.history.isEmpty()) {
        Text(
            stringResource(R.string.words_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MukharSpace.s).testTag(TAG_HISTORY_EMPTY),
        )
        return
    }
    val weekStart = WordHistory.weekStart(state.today)
    val weekCount = state.history.filter { !it.day.isBefore(weekStart) }.sumOf { it.words.size }
    if (weekCount > 0) {
        OutlinedButton(
            onClick = { actions.onRevise(state.tab, weekStart, state.today) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MukharSpace.touch).testTag(TAG_REVISE_WEEK),
        ) {
            Icon(Icons.Outlined.FitnessCenter, null)
            Spacer(Modifier.width(8.dp))
            Text(pluralStringResource(R.plurals.words_history_revise_week, weekCount, weekCount))
        }
    }
    state.history.forEach { day ->
        val label = dayLabel(day.day, state.today)
        Row(Modifier.fillMaxWidth().padding(top = MukharSpace.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            val reviseCd = pluralStringResource(R.plurals.words_history_revise_day_a11y, day.words.size, day.words.size, label)
            TextButton(
                onClick = { actions.onRevise(state.tab, day.day, day.day) },
                modifier = Modifier.heightIn(min = MukharSpace.touch).testTag("revise_${day.day}").semantics { contentDescription = reviseCd },
            ) { Text(stringResource(R.string.words_history_revise_day)) }
        }
        MukharCard(Modifier.fillMaxWidth()) {
            day.words.forEachIndexed { i, w ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WordRow(w, showNext = true) { actions.onOpenWord(w.id) }
            }
        }
    }
}

@Composable
private fun ExhaustedNote(lang: Lang) {
    MukharCard(Modifier.fillMaxWidth().testTag(TAG_EXHAUSTED)) {
        Text(
            stringResource(R.string.words_exhausted, stringResource(if (lang == Lang.HI) R.string.lang_hi else R.string.lang_en)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(MukharSpace.l),
        )
    }
}

/** "Today", "Yesterday", else "Thu 5 Nov". */
@Composable
fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.words_history_today)
    today.minusDays(1) -> stringResource(R.string.words_history_yesterday)
    else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))
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
    MukharCard(
        Modifier.fillMaxWidth().testTag(TAG_GYM_ROW).clearAndSetSemantics {
            contentDescription = a11y
            if (due > 0) {
                role = Role.Button
                onClick { onOpen(); true }
            }
        },
        onClick = if (due > 0) onOpen else null,
    ) {
        Row(Modifier.padding(MukharSpace.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp).clip(MaterialTheme.shapes.medium)
                    .background(if (due > 0) MukharTheme.colors.accentTint else MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (due > 0) {
                    Text(due.toString(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = MukharTheme.colors.accentInk)
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
    GymRow(state) { actions.onOpenGym(state.tab) }
    state.exhaustedHere?.let { ExhaustedNote(it) }
    if (state.words.isEmpty()) {
        Empty(state, actions.onTalk)
        return
    }
    val filterLabel = stringResource(R.string.words_filter_a11y)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = filterLabel; isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(MukharSpace.s),
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
    MukharCard(Modifier.fillMaxWidth()) {
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
private fun WordRow(w: WordItem, showNext: Boolean = false, onClick: () -> Unit) {
    val meta = posAndRegister(w)
    val status = statusLabel(w)
    val next = if (showNext) nextReviewLabel(w) else null
    val a11y = stringResource(R.string.word_row_a11y, w.lemma, meta, w.meaning, status) + (next?.let { ". $it" } ?: "")
    Row(
        Modifier.fillMaxWidth().testTag("word_${w.id}").clickable(role = Role.Button, onClick = onClick).clearAndSetSemantics { contentDescription = a11y }
            .padding(horizontal = MukharSpace.l, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // Wraps the meta under the lemma at large text instead of squeezing it.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.Bottom) {
                Text(
                    w.lemma,
                    style = if (w.lang == Lang.HI) MukharTheme.type.hiBodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
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
            if (next != null) {
                Text(next, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            if (w.source == WordSource.TALK) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Mic, null, tint = MukharTheme.colors.accentInk, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.word_src_talk, shortDay(w.addedAt.atZone(ZoneId.systemDefault()).toLocalDate())),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = MukharTheme.colors.accentInk,
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
    Column(Modifier.fillMaxWidth().padding(top = MukharSpace.xl).testTag(TAG_WORDS_EMPTY), horizontalAlignment = Alignment.CenterHorizontally) {
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
        Button(onClick = onTalk, modifier = Modifier.padding(top = 16.dp).heightIn(min = MukharSpace.touch)) {
            Icon(Icons.Rounded.Mic, null, tint = MukharTheme.colors.accent)
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
        Column(Modifier.fillMaxWidth().padding(top = MukharSpace.xl), horizontalAlignment = Alignment.CenterHorizontally) {
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
    MukharCard(Modifier.fillMaxWidth()) {
        state.lines.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = MukharSpace.l, vertical = 14.dp).semantics(mergeDescendants = true) {}) {
                Text(
                    "“${l.better}”",
                    style = if (l.lang == Lang.HI) MukharTheme.type.hiBodyMedium else MukharTheme.type.quote,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Eyebrow(stringResource(R.string.line_better_of, l.original), Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** "Next review in 3 days" / "Review due now" / "Not reviewed yet" (History rows, US-077). */
@Composable
private fun nextReviewLabel(w: WordItem): String {
    val next = w.nextReview ?: return stringResource(R.string.words_next_none)
    val d = java.time.Duration.between(java.time.Instant.now(), next)
    return if (d.isNegative || d.isZero) stringResource(R.string.words_next_due) else stringResource(R.string.words_next_in, intervalLabel(d, spoken = true))
}

const val TAG_WORD_BANK = "word_bank"
const val TAG_WORDS_TABS = "words_tabs"
const val TAG_HISTORY_EMPTY = "words_history_empty"
const val TAG_REVISE_WEEK = "words_revise_week"
const val TAG_EXHAUSTED = "words_exhausted"
const val TAG_GYM_ROW = "word_gym_row"
const val TAG_WORDS_EMPTY = "words_empty"
const val TAG_WORDS_COUNT = "words_count"
