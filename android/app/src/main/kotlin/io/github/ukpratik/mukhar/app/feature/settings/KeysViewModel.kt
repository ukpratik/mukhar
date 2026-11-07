package io.github.ukpratik.mukhar.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ukpratik.mukhar.data.byok.ByokKeyRepository
import io.github.ukpratik.mukhar.data.byok.ByokProvider
import io.github.ukpratik.mukhar.data.byok.KeyCheck
import io.github.ukpratik.mukhar.data.byok.KeyInfo
import io.github.ukpratik.mukhar.data.byok.KeyKind
import io.github.ukpratik.mukhar.data.repo.TalkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A key check in progress, or its answer. */
sealed interface KeyStatus {
    data object Checking : KeyStatus
    data class Done(val result: KeyCheck) : KeyStatus
}

data class KeysUiState(
    val keys: Map<ByokProvider, KeyInfo> = emptyMap(),
    /** The latest check per provider in this visit (the stored answer shows otherwise). */
    val status: Map<ByokProvider, KeyStatus> = emptyMap(),
)

/** "Your keys" (ADR-008): save + test, test, replace, remove. Keys go straight to the encrypted vault. */
@HiltViewModel
class KeysViewModel @Inject constructor(
    private val repo: ByokKeyRepository,
    private val talks: TalkRepository,
) : ViewModel() {
    private val status = MutableStateFlow<Map<ByokProvider, KeyStatus>>(emptyMap())

    val state: StateFlow<KeysUiState> = combine(repo.keys, status) { k, s -> KeysUiState(k, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeysUiState())

    fun onSave(provider: ByokProvider, key: String) {
        if (key.isBlank()) return
        run(provider) { repo.saveAndCheck(provider, key) }
    }

    fun onTest(provider: ByokProvider) = run(provider) { repo.check(provider) }

    fun onRemove(provider: ByokProvider) {
        viewModelScope.launch {
            repo.remove(provider)
            status.update { it - provider }
        }
    }

    private fun run(provider: ByokProvider, block: suspend () -> KeyCheck?) {
        status.update { it + (provider to KeyStatus.Checking) }
        viewModelScope.launch {
            val r = block()
            status.update { if (r == null) it - provider else it + (provider to KeyStatus.Done(r)) }
            // A working speech-to-text key can analyse talks that were waiting for an engine.
            if (r?.usable == true && provider.kind == KeyKind.STT) talks.retryWaitingForEngine()
        }
    }
}
