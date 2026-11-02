package com.vaak.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vaak.app.nav.OnboardingRoute
import com.vaak.app.nav.TodayRoute
import com.vaak.app.nav.VaakNavHost
import com.vaak.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** M3: picks the start destination from DataStore (onboarding gate, LLD §5). */
@HiltViewModel
class RootViewModel @Inject constructor(settings: SettingsStore) : ViewModel() {
    private val _start = MutableStateFlow<Any?>(null)
    val start: StateFlow<Any?> = _start.asStateFlow()

    init {
        viewModelScope.launch {
            _start.value = if (settings.current().onboardingDone) TodayRoute else OnboardingRoute
        }
    }

    fun onOnboardingFinished() {
        _start.value = TodayRoute
    }

    /** After Delete all my data: a fresh graph at onboarding, as on a new install (US-063). */
    fun onDataDeleted() {
        _start.value = OnboardingRoute
    }
}

@Composable
fun VaakRoot(viewModel: RootViewModel = hiltViewModel()) {
    val start by viewModel.start.collectAsStateWithLifecycle()
    when (val s = start) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        // A new graph (and nav controller) when onboarding ends, so Today becomes the start destination.
        else -> key(s) {
            VaakNavHost(startDestination = s, onOnboardingFinished = viewModel::onOnboardingFinished, onDataDeleted = viewModel::onDataDeleted)
        }
    }
}
