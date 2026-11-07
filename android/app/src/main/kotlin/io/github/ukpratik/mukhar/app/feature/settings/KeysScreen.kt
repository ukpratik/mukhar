package io.github.ukpratik.mukhar.app.feature.settings

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.findActivity
import io.github.ukpratik.mukhar.app.feature.talk.common.MukharAppBar
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.KeyCheck
import io.github.ukpratik.mukhar.data.byok.KeyInfo
import io.github.ukpratik.mukhar.data.byok.KeyKind
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class KeysActions(
    val onBack: () -> Unit = {},
    val onSave: (ByokProvider, String) -> Unit = { _, _ -> },
    val onTest: (ByokProvider) -> Unit = {},
    val onRemove: (ByokProvider) -> Unit = {},
)

@Composable
fun KeysScreen(onBack: () -> Unit, viewModel: KeysViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // FLAG_SECURE: no screenshots, screen recording or recents thumbnail while keys are on screen.
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    KeysContent(state, KeysActions(onBack, viewModel::onSave, viewModel::onTest, viewModel::onRemove))
}

/** Stateless "Your keys" (ADR-008 copy). Only the last 4 characters of a saved key are ever shown. */
@Composable
fun KeysContent(state: KeysUiState, actions: KeysActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        MukharAppBar(stringResource(R.string.keys_title), stringResource(R.string.plan_back_a11y), actions.onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.xxl)) {
            Text(stringResource(R.string.keys_lead), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(KeyKind.STT to R.string.keys_stt_heading, KeyKind.LLM to R.string.keys_llm_heading).forEach { (kind, heading) ->
                Text(
                    stringResource(heading).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = MukharSpace.l, bottom = 6.dp).semantics { heading() },
                )
                ByokProvider.entries.filter { it.kind == kind }.forEach { p ->
                    KeyCard(p, state.keys[p], state.status[p], actions, Modifier.padding(bottom = 10.dp))
                }
            }
            Text(
                stringResource(R.string.key_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MukharSpace.m).testTag(TAG_KEY_FOOTNOTE),
            )
        }
    }
}

@Composable
private fun KeyCard(p: ByokProvider, info: KeyInfo?, status: KeyStatus?, actions: KeysActions, modifier: Modifier) {
    val name = stringResource(providerName(p))
    // Never rememberSaveable: a key must not end up in the saved-instance-state bundle.
    var editing by remember(info == null) { mutableStateOf(info == null) }
    var draft by remember { mutableStateOf("") }
    MukharCard(modifier.fillMaxWidth().testTag(TAG_KEY_CARD + p.id)) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (info != null) Text(stringResource(R.string.key_masked, info.last4), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            val checking = status == KeyStatus.Checking
            val result = (status as? KeyStatus.Done)?.result ?: info?.check
            val line = when {
                checking -> stringResource(R.string.key_checking)
                result != null -> stringResource(checkText(result))
                info != null -> stringResource(R.string.key_not_checked)
                else -> null
            }
            if (line != null) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result?.usable == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag(TAG_KEY_STATUS + p.id),
                )
            }
            val checkedAt = info?.checkedAtMs
            if (status !is KeyStatus.Done && checkedAt != null) {
                Text(stringResource(R.string.key_checked_on, checkedDate(checkedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.trim() },
                    singleLine = true,
                    label = { Text(stringResource(R.string.key_field_label, name)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(TAG_KEY_FIELD + p.id),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            actions.onSave(p, draft)
                            draft = ""
                            editing = false
                        },
                        enabled = draft.isNotBlank() && !checking,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(TAG_KEY_SAVE + p.id),
                    ) { Text(stringResource(R.string.key_save_test)) }
                    if (info != null) TextButton(onClick = { draft = ""; editing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.key_cancel)) }
                }
            } else {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.onTest(p) }, enabled = !checking, modifier = Modifier.heightIn(min = 48.dp).testTag(TAG_KEY_TEST + p.id)) {
                        Text(stringResource(R.string.key_test))
                    }
                    TextButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.key_replace)) }
                    TextButton(onClick = { actions.onRemove(p) }, modifier = Modifier.heightIn(min = 48.dp).testTag(TAG_KEY_REMOVE + p.id)) {
                        Text(stringResource(R.string.key_remove))
                    }
                }
            }
        }
    }
}

fun checkText(r: KeyCheck): Int = when (r) {
    KeyCheck.OK -> R.string.key_works
    KeyCheck.LIMITED -> R.string.key_works_limited
    KeyCheck.INVALID -> R.string.key_invalid
    KeyCheck.MODEL_UNAVAILABLE -> R.string.key_model
    KeyCheck.OFFLINE -> R.string.key_offline
    KeyCheck.ERROR -> R.string.key_error
}

private fun checkedDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", Locale.US))

const val TAG_KEY_CARD = "key_card_"
const val TAG_KEY_STATUS = "key_status_"
const val TAG_KEY_FIELD = "key_field_"
const val TAG_KEY_SAVE = "key_save_"
const val TAG_KEY_TEST = "key_test_"
const val TAG_KEY_REMOVE = "key_remove_"
const val TAG_KEY_FOOTNOTE = "key_footnote"
