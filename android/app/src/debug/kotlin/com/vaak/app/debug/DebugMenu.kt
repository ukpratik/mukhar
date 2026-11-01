package com.vaak.app.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.data.debug.DebugControls
import com.vaak.data.debug.FakeMode
import com.vaak.data.debug.LowStorage
import com.vaak.data.prefs.TalkPrefs
import com.vaak.data.prefs.UserSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugEntryPoint {
    fun controls(): DebugControls
    fun settings(): UserSettings
}

/**
 * Debug builds only (build-plan "Debug hooks"): fake pipeline failure modes, delay, low storage, plan, stutter mode,
 * and the clock override screen (also its own launcher entry, "Vaak debug clock").
 */
@Composable
fun DebugMenuEntry() {
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.debug_menu)) }
        OutlinedButton(onClick = { context.startActivity(android.content.Intent(context, DebugClockActivity::class.java)) }) {
            Text(stringResource(R.string.debug_open_clock))
        }
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) { DebugMenu(onClose = { open = false }) }
        }
    }
}

@Composable
private fun DebugMenu(onClose: () -> Unit) {
    val context = LocalContext.current
    val entry = remember { EntryPointAccessors.fromApplication(context.applicationContext, DebugEntryPoint::class.java) }
    val controls = entry.controls()
    val state by controls.state.collectAsStateWithLifecycle()
    val prefs by entry.settings().prefs.collectAsStateWithLifecycle(TalkPrefs())
    val scope = rememberCoroutineScope()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(stringResource(R.string.debug_menu), style = MaterialTheme.typography.headlineMedium)
        Header(R.string.debug_pipeline)
        Toggle(stringResource(R.string.debug_use_fake), state.useFake) { on -> controls.update { it.copy(useFake = on) } }
        Header(R.string.debug_failure)
        Column(Modifier.selectableGroup()) {
            listOf(
                FakeMode.NONE to R.string.debug_mode_none, FakeMode.STT_FAIL to R.string.debug_mode_stt,
                FakeMode.LLM_FAIL to R.string.debug_mode_llm, FakeMode.BUSY to R.string.debug_mode_busy,
                FakeMode.OFFLINE to R.string.debug_mode_offline, FakeMode.SELF_HARM to R.string.debug_mode_self_harm,
            ).forEach { (mode, label) -> Radio(stringResource(label), state.mode == mode) { controls.update { it.copy(mode = mode) } } }
        }
        Text(stringResource(R.string.debug_delay, (state.delayMs / 1000).toInt()), modifier = Modifier.padding(top = 12.dp))
        Slider(
            value = state.delayMs / 1000f,
            onValueChange = { v -> controls.update { it.copy(delayMs = (v.toInt() * 1000L)) } },
            valueRange = 0f..15f,
            steps = 14,
        )
        Header(R.string.debug_storage)
        Column(Modifier.selectableGroup()) {
            listOf(LowStorage.OFF to R.string.debug_storage_off, LowStorage.WARN to R.string.debug_storage_warn, LowStorage.BLOCK to R.string.debug_storage_block)
                .forEach { (level, label) -> Radio(stringResource(label), state.lowStorage == level) { controls.update { it.copy(lowStorage = level) } } }
        }
        Toggle(stringResource(R.string.debug_premium), state.premium) { on -> controls.update { it.copy(premium = on) } }
        Toggle(stringResource(R.string.debug_stutter), prefs.stutterFriendly) { on -> scope.launch { entry.settings().setStutterFriendly(on) } }
        TextButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun Header(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
