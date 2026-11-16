package io.github.ukpratik.mukhar.app.feature.words.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.Eyebrow
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.words.posRes
import io.github.ukpratik.mukhar.data.dictionary.DictionaryRepository
import io.github.ukpratik.mukhar.data.dictionary.LookupHit
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.domain.dictionary.LookupKey
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Results for [searched] (the query they were found for, so a slow search never shows under a newer query). */
data class LookupUiState(
    val query: String = "",
    val searched: String = "",
    val results: List<LookupHit> = emptyList(),
    val recent: List<LookupHit> = emptyList(),
    /** The dictionary is still being imported (first use only). */
    val preparing: Boolean = true,
    val hasKey: Boolean = false,
) {
    val showRecent: Boolean get() = query.isBlank()
    val noMatch: Boolean get() = !preparing && query.isNotBlank() && searched == query.trim() && results.isEmpty()
}

/** D-056: the search screen. Typing searches as you go (debounced); results come from [DictionaryRepository]. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LookupViewModel @Inject constructor(
    private val dictionary: DictionaryRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val preparing = MutableStateFlow(true)
    private val hasKey = MutableStateFlow(false)

    private val found = query.mapLatest { q ->
        delay(DEBOUNCE_MS)
        val trimmed = q.trim()
        trimmed to if (trimmed.isEmpty()) emptyList() else dictionary.search(trimmed)
    }

    private val recent = settings.recentLookups.mapLatest { ids -> ids.mapNotNull { dictionary.hitFor(it) } }

    val state: StateFlow<LookupUiState> = combine(query, found, recent, preparing, hasKey) { q, (searched, results), r, p, k ->
        LookupUiState(q, searched, results, r, p, k)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LookupUiState())

    init {
        viewModelScope.launch {
            dictionary.warmUp()
            preparing.value = false
            // Re-run the query typed while the dictionary was importing.
            query.value = query.value
        }
        viewModelScope.launch { hasKey.value = dictionary.hasKey() }
    }

    fun onQuery(q: String) {
        query.value = q
    }

    fun opened(id: String) {
        viewModelScope.launch { settings.addRecentLookup(id) }
    }

    fun clearRecent() {
        viewModelScope.launch { settings.clearRecentLookups() }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
    }
}

data class LookupActions(
    val onBack: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onOpen: (LookupHit) -> Unit = {},
    /** "Explain with AI" for a word the dictionary doesn't have: (lang, word). */
    val onExplainUnknown: (Lang, String) -> Unit = { _, _ -> },
    val onClearRecent: () -> Unit = {},
)

@Composable
fun LookupScreen(onBack: () -> Unit, onOpenEntry: (String) -> Unit, onExplainUnknown: (Lang, String) -> Unit, vm: LookupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LookupContent(
        state,
        LookupActions(
            onBack = onBack,
            onQuery = vm::onQuery,
            onOpen = { hit ->
                vm.opened(hit.id)
                onOpenEntry(hit.id)
            },
            onExplainUnknown = onExplainUnknown,
            onClearRecent = vm::clearRecent,
        ),
    )
}

/** Stateless search screen: the field (focused on open), then recent look-ups, results, or "no match". */
@Composable
fun LookupContent(state: LookupUiState, actions: LookupActions, modifier: Modifier = Modifier, autoFocus: Boolean = true) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().testTag(TAG_LOOKUP)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = MukharSpace.gutter, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.lookup_back_cd)) }
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.lookup_field_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQuery("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.lookup_clear_cd)) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f).focusRequester(focus).testTag(TAG_LOOKUP_FIELD),
            )
        }
        if (state.preparing && state.query.isNotBlank()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = MukharSpace.gutter))
            Text(
                stringResource(R.string.lookup_preparing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MukharSpace.gutter, vertical = 8.dp),
            )
        }
        when {
            state.showRecent -> Recent(state, actions)
            state.noMatch -> NoMatch(state, actions)
            else -> Results(state.results, actions.onOpen)
        }
    }
}

@Composable
private fun Recent(state: LookupUiState, actions: LookupActions) {
    if (state.recent.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(MukharSpace.gutter).testTag(TAG_LOOKUP_EMPTY), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.lookup_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.lookup_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(start = MukharSpace.gutter, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(stringResource(R.string.lookup_recent), Modifier.weight(1f))
        TextButton(onClick = actions.onClearRecent) { Text(stringResource(R.string.lookup_recent_clear)) }
    }
    Results(state.recent, actions.onOpen, icon = true)
}

@Composable
private fun NoMatch(state: LookupUiState, actions: LookupActions) {
    val q = state.query.trim()
    Column(Modifier.fillMaxWidth().padding(MukharSpace.gutter).testTag(TAG_LOOKUP_NO_MATCH), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lookup_no_match, q), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lookup_no_match_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // With a key, the AI can still explain it (a newer word, a phrase, a spelling the dictionary lacks).
        if (state.hasKey) {
            OutlinedButton(
                onClick = { actions.onExplainUnknown(if (LookupKey.isDevanagari(q)) Lang.HI else Lang.EN, q) },
                modifier = Modifier.padding(top = 4.dp).heightIn(min = MukharSpace.touch).testTag(TAG_LOOKUP_EXPLAIN),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lookup_explain_unknown, q))
            }
        }
    }
}

@Composable
private fun Results(hits: List<LookupHit>, onOpen: (LookupHit) -> Unit, icon: Boolean = false) {
    LazyColumn(Modifier.fillMaxSize().testTag(TAG_LOOKUP_RESULTS)) {
        items(hits, key = { it.id }) { hit ->
            HitRow(hit, icon) { onOpen(hit) }
            HorizontalDivider(Modifier.padding(horizontal = MukharSpace.gutter), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HitRow(hit: LookupHit, icon: Boolean, onClick: () -> Unit) {
    val hi = hit.lang == Lang.HI
    val pos = posRes(hit.pos)?.let { stringResource(it) }
    val mukhar = stringResource(R.string.lookup_mukhar_badge)
    val a11y = stringResource(R.string.lookup_hit_a11y, hit.word, listOfNotNull(hit.translit, pos).joinToString(", "), listOfNotNull(hit.meaning, hit.other).joinToString(". ")) +
        if (hit.mukhar) ". " + stringResource(R.string.lookup_hit_mukhar_a11y) else ""
    Row(
        Modifier.fillMaxWidth().testTag("lookup_hit_${hit.id}").clickable(role = Role.Button, onClick = onClick).clearAndSetSemantics { contentDescription = a11y }
            .padding(horizontal = MukharSpace.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    hit.word,
                    style = if (hi) MukharTheme.type.hiBodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val sub = listOfNotNull(hit.translit, pos).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hit.mukhar) InfoChip(mukhar, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
            }
            if (hit.meaning.isNotBlank()) {
                Text(
                    hit.meaning,
                    style = if (hi && hit.mukhar) MukharTheme.type.hiBodyMedium else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            hit.other?.let {
                Text(
                    it,
                    style = if (hi) MaterialTheme.typography.bodySmall else MukharTheme.type.hiBodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

const val TAG_LOOKUP = "lookup"
const val TAG_LOOKUP_FIELD = "lookup_field"
const val TAG_LOOKUP_RESULTS = "lookup_results"
const val TAG_LOOKUP_EMPTY = "lookup_empty"
const val TAG_LOOKUP_NO_MATCH = "lookup_no_match"
const val TAG_LOOKUP_EXPLAIN = "lookup_explain"
