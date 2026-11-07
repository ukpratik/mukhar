package io.github.ukpratik.mukhar.app.feature.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoChip
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharAppBar
import io.github.ukpratik.mukhar.app.feature.talk.common.clock
import io.github.ukpratik.mukhar.app.feature.talk.common.langLabel
import io.github.ukpratik.mukhar.domain.talk.RecordingRetention
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}, viewModel: RecordingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val res = LocalResources.current
    LaunchedEffect(viewModel) { viewModel.message.collect { snackbar.showSnackbar(res.getString(it)) } }
    RecordingsContent(
        state,
        RecordingsActions(onBack = onBack, onOpen = { viewModel.stop(); onOpen(it) }, onCap = viewModel::onCap, onPlay = viewModel::onPlay, onDelete = viewModel::onDelete, onDeleteAll = viewModel::onDeleteAll),
        snackbar,
    )
}

data class RecordingsActions(
    val onBack: () -> Unit = {},
    /** Opens the talk's report (a voice-only one can get its transcript there). */
    val onOpen: (String) -> Unit = {},
    val onCap: (Int) -> Unit = {},
    val onPlay: (RecordingRowUi) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onDeleteAll: () -> Unit = {},
)

/** US-080: usage against the limit, the limit picker, every recording (play, delete) and "Delete all". */
@Composable
fun RecordingsContent(state: RecordingsUiState, actions: RecordingsActions, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = actions.onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        MukharAppBar(stringResource(R.string.recordings_title), stringResource(R.string.plan_back_a11y), actions.onBack)
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = MukharSpace.gutter).testTag(TAG_RECORDINGS_LIST),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { UsageCard(state, actions.onCap) }
            if (!state.loading && state.rows.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.LibraryMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.recordings_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            stringResource(R.string.recordings_empty_body), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            items(state.rows, key = { it.item.sessionId }) { row ->
                RecordingRow(
                    row, playing = state.playingId == row.item.sessionId, onPlay = { actions.onPlay(row) },
                    onOpen = { actions.onOpen(row.item.sessionId) }, onDelete = { confirmDelete = row.item.sessionId },
                )
            }
            if (state.rows.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { confirmAll = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp).heightIn(min = 52.dp).testTag(TAG_RECORDINGS_DELETE_ALL),
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.recordings_delete_all))
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }
    confirmDelete?.let { id ->
        val pending = state.rows.firstOrNull { it.item.sessionId == id }?.item?.pending == true
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.recordings_delete_title)) },
            text = { Text(stringResource(if (pending) R.string.recordings_delete_pending_body else R.string.recordings_delete_body)) },
            confirmButton = { TextButton(onClick = { actions.onDelete(id); confirmDelete = null }) { Text(stringResource(R.string.recordings_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.recordings_cancel)) } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.recordings_delete_all_title)) },
            text = { Text(stringResource(R.string.recordings_delete_all_body, sizeLabel(state.totalBytes))) },
            confirmButton = { TextButton(onClick = { actions.onDeleteAll(); confirmAll = false }, modifier = Modifier.testTag(TAG_RECORDINGS_CONFIRM_ALL)) { Text(stringResource(R.string.recordings_delete)) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.recordings_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageCard(state: RecordingsUiState, onCap: (Int) -> Unit) {
    val used = sizeLabel(state.totalBytes)
    val cap = capLabel(state.capMb)
    val usageCd = stringResource(R.string.recordings_usage_a11y, used, cap)
    MukharCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.recordings_usage, used, cap), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = usageCd; heading() },
            )
            LinearProgressIndicator(
                progress = { (state.totalBytes.toFloat() / RecordingRetention.capBytes(state.capMb)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Text(
                stringResource(R.string.recordings_explain), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
            )
            Text(stringResource(R.string.recordings_limit), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (mb in RecordingRetention.CAP_OPTIONS_MB) {
                    val selected = mb == state.capMb
                    FilterChip(
                        selected = selected, onClick = { onCap(mb) }, label = { Text(capLabel(mb), maxLines = 1) },
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { onCap(mb) }).testTag("$TAG_RECORDINGS_CAP$mb"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(row: RecordingRowUi, playing: Boolean, onPlay: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit) {
    val item = row.item
    val zone = runCatching { ZoneId.of(item.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val whenText = Instant.ofEpochMilli(item.createdAt).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US))
    MukharCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    if (playing) stringResource(R.string.recordings_stop_cd) else stringResource(R.string.recordings_play_cd, whenText),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // Baselines have no report screen of their own; talks open theirs.
            val openable = item.kind != "baseline" && !item.pending
            val openLabel = stringResource(R.string.recordings_open)
            Column(
                Modifier.weight(1f).then(if (openable) Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen) else Modifier)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    listOf(whenText, stringResource(langLabel(item.lang)), clock(item.durationMs), sizeLabel(item.bytes)).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val title = when {
                    item.kind == "baseline" -> stringResource(R.string.recordings_starting_point)
                    else -> row.promptText
                }
                if (title.isNotEmpty()) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                when {
                    item.pending -> InfoChip(
                        stringResource(R.string.recordings_pending), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    item.awaitingTranscript -> InfoChip(
                        stringResource(R.string.recordings_awaiting_transcript), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp).testTag("$TAG_RECORDINGS_DELETE${item.sessionId}")) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.recordings_delete_cd, whenText), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

const val TAG_RECORDINGS_LIST = "recordings_list"
const val TAG_RECORDINGS_DELETE_ALL = "recordings_delete_all"
const val TAG_RECORDINGS_CONFIRM_ALL = "recordings_confirm_all"
const val TAG_RECORDINGS_CAP = "recordings_cap_"
const val TAG_RECORDINGS_DELETE = "recordings_delete_"
