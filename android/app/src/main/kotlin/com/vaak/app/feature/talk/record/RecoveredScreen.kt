package com.vaak.app.feature.talk.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vaak.app.R
import com.vaak.app.feature.talk.common.CenterState
import com.vaak.app.feature.talk.common.CreditPill
import com.vaak.app.feature.talk.common.clock
import com.vaak.app.feature.talk.report.CreditUi
import com.vaak.app.nav.RecoveredRoute
import com.vaak.data.content.PromptRepository
import com.vaak.data.db.TalkSessionEntity
import com.vaak.data.repo.HabitRepository
import com.vaak.data.repo.TalkKind
import com.vaak.data.repo.TalkRepository
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class RecoveredUiState(val session: TalkSessionEntity? = null, val promptText: String = "", val credit: CreditUi? = null)

/** S09 state 8: the app closed mid-talk; the saved part is offered before anything else (US-018). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecoveredViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val talks: TalkRepository,
    private val prompts: PromptRepository,
    private val habits: HabitRepository,
) : ViewModel() {
    val id = savedState.toRoute<RecoveredRoute>().sessionId

    val state: StateFlow<RecoveredUiState> = flow { emit(talks.session(id)) }.flatMapLatest { s ->
        if (s == null) {
            flow { emit(RecoveredUiState()) }
        } else {
            val text = prompts.byId(s.promptId)?.text.orEmpty()
            habits.observeCredit(Instant.ofEpochMilli(s.createdAt + s.durationMs), ZoneId.of(s.zoneId)).map { c ->
                RecoveredUiState(s, text, c?.let { CreditUi(it.streak, it.weeklyGoalMet) })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveredUiState())

    fun getFeedback(done: () -> Unit) = viewModelScope.launch {
        talks.analyzeRecovered(id)
        done()
    }

    fun discard(done: () -> Unit) = viewModelScope.launch {
        talks.discard(id)
        done()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveredScreen(
    onClose: () -> Unit,
    onReport: (String) -> Unit,
    onRecordAgain: (TalkSessionEntity) -> Unit,
    onResumeBaseline: (String) -> Unit = {},
    vm: RecoveredViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = state.session
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).padding(bottom = 28.dp),
    ) {
        Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.rec_close_cd)) }
        }
        if (s != null) {
            val duration = clock(s.durationMs.toLong())
            // A baseline is shown as the starting point it is, and resumes the baseline flow (US-003).
            val baseline = s.kind == TalkKind.BASELINE.key
            CenterState(
                Icons.Rounded.Restore,
                stringResource(if (baseline) R.string.rec_baseline_title else R.string.rec_title),
                stringResource(if (baseline) R.string.rec_baseline_body else R.string.rec_body, duration),
                artSize = 88.dp,
            ) {
                val time = Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.of(s.zoneId))
                    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).lowercase(Locale.US)
                VaakCard(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.rec_card, duration, time), style = MaterialTheme.typography.labelLarge)
                            Text("“${state.promptText.ifEmpty { if (baseline) TalkRepository.BASELINE_PROMPT else "" }}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
                state.credit?.let { CreditPill(it, Modifier.padding(top = 4.dp)) }
            }
            Spacer(Modifier.heightIn(min = 32.dp))
            if (baseline) {
                Button(onClick = { onResumeBaseline(s.id) }, modifier = Modifier.fillMaxWidth().height(54.dp).testTag(TAG_RESUME_BASELINE)) {
                    Text(stringResource(R.string.rec_baseline_get))
                }
            } else {
                Button(onClick = { vm.getFeedback { onReport(vm.id) } }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.rec_get)) }
                TextButton(onClick = { onRecordAgain(s) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rec_again)) }
            }
            TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.discard)) }
        }
    }
    if (confirm) {
        ModalBottomSheet(onDismissRequest = { confirm = false }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
                Text(stringResource(R.string.discard_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Button(onClick = { confirm = false }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(54.dp)) { Text(stringResource(R.string.keep_it)) }
                TextButton(onClick = { confirm = false; scope.launch { vm.discard(onClose) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.discard)) }
            }
        }
    }
}

const val TAG_RESUME_BASELINE = "resume_baseline"
