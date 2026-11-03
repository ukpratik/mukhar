package io.github.ukpratik.mukhar.app.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.feature.habit.StreakSheet
import io.github.ukpratik.mukhar.app.feature.onboarding.openNotificationSettings
import io.github.ukpratik.mukhar.app.feature.plan.openPlaySubscriptions

/** Today with its ViewModel and the streak sheet (S07 + S20). Navigation targets come from the nav graph. */
@Composable
fun TodayDestination(
    onStartTalk: () -> Unit,
    onOpenRevise: () -> Unit,
    onOpenWords: () -> Unit,
    onOpenMe: () -> Unit,
    onTurnOnTalks: () -> Unit,
    onRecordBaseline: () -> Unit,
    onOpenRecovered: (String) -> Unit,
    onOpenWord: (String) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words by viewModel.wordsLearned.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshNotificationPermission() }
    LaunchedEffect(state.autoOpenSheet) { if (state.autoOpenSheet) sheetOpen = true }
    LaunchedEffect(sheetOpen) { if (sheetOpen) viewModel.onSheetOpened() }

    TodayContent(
        state = state,
        actions = TodayActions(
            onStartTalk = onStartTalk,
            onShuffle = viewModel::onShuffle,
            onOpenStreak = { sheetOpen = true },
            onOpenRevise = onOpenRevise,
            onOpenWords = onOpenWords,
            onOpenMe = onOpenMe,
            onTurnOnTalks = onTurnOnTalks,
            onRecordBaseline = onRecordBaseline,
            onOpenWaiting = onStartTalk,
            onOpenRecovered = onOpenRecovered,
            onTipTap = { context.openNotificationSettings() },
            onDismissTip = viewModel::onDismissTip,
            onOpenWord = onOpenWord,
            onSpeak = viewModel::onSpeak,
            onKeepTrial = viewModel::onKeepTrial,
            onManageTrial = { context.openPlaySubscriptions() },
        ),
    )

    val habit = state.habit
    val card = state.streak
    if (sheetOpen && habit != null && card != null) {
        val close = {
            sheetOpen = false
            viewModel.onSheetDismissed()
        }
        StreakSheet(
            habit = habit,
            card = card,
            talksEnabled = state.talksEnabled,
            wordsLearned = words,
            onGoal = viewModel::onWeeklyGoal,
            onTalk = {
                close()
                onStartTalk()
            },
            onRevise = {
                close()
                onOpenRevise()
            },
            onDismiss = close,
        )
    }
}
