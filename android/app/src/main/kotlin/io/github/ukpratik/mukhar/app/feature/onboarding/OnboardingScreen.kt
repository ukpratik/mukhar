package io.github.ukpratik.mukhar.app.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.settings.Links
import io.github.ukpratik.mukhar.app.feature.talk.common.reducedMotion
import io.github.ukpratik.mukhar.ui.theme.MukharSpace

@Composable
fun OnboardingRoute(onFinished: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    OnboardingScreen(hiltViewModel(), onFinished = onFinished, onExit = { activity?.finish() })
}

/** S04 or S05 on its own, opened from Today. Leaving the step (either answer, or Back) closes it. */
@Composable
fun StandaloneStepRoute(step: OnboardingStep, onClose: () -> Unit, resumeSessionId: String? = null) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    LaunchedEffect(step) {
        viewModel.startStandalone(step)
        resumeSessionId?.let(viewModel::resumeRecoveredBaseline)
    }
    OnboardingScreen(viewModel, onFinished = onClose, onExit = onClose)
}

/** Hosts S01 to S06 with the shared chrome (back, 6-step progress, sticky CTA bar). */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val paragraph = readAloudText(state.readAloudLang)
    val phoneLanguage = currentLocale().language
    val uriHandler = LocalUriHandler.current
    val reduced = reducedMotion()

    LaunchedEffect(viewModel) {
        viewModel.preselectLanguage(phoneLanguage)
        viewModel.effects.collect { if (it == OnboardingEffect.Finished) onFinished() }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startWarmup(paragraph)
        } else {
            val activity = context.findActivity()
            micPermanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            viewModel.onMicDenied()
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onReminderConfirmed(granted)
    }

    fun requestMic() {
        if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            viewModel.startWarmup(paragraph)
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun remindMe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onReminderConfirmed(NotificationManagerCompat.from(context).areNotificationsEnabled())
        }
    }

    // Coming back from the system settings with the mic now allowed resumes the warm-up.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.step == OnboardingStep.MIC_DENIED && context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            micPermanentlyDenied = false
            viewModel.onRetryMic()
        }
    }

    BackHandler {
        if (!viewModel.onBack()) onExit()
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .consumeWindowInsets(WindowInsets.systemBars).imePadding(),
    ) {
        val step = state.step
        val showProgress = !state.standalone && step != OnboardingStep.BLOCKED && step != OnboardingStep.DONE
        val back: (() -> Unit)? = if (step.canGoBack || state.standalone) ({ if (!viewModel.onBack()) onExit() }) else null
        ObTopBar(
            step = if (showProgress) step.progress else null,
            onBack = back,
            backLabel = when {
                state.warmup is WarmupUi.Recording && step == OnboardingStep.WARMUP -> stringResource(R.string.warmup_back_stop)
                step == OnboardingStep.DECLINED -> stringResource(R.string.declined_back)
                step == OnboardingStep.BASELINE && state.baseline is BaselineUi.Recording -> stringResource(R.string.baseline_back_discard)
                else -> stringResource(R.string.ob_back)
            },
        )
        AnimatedContent(
            targetState = step,
            // Reduced motion (design §8): the step swaps with no fade.
            transitionSpec = {
                if (reduced) EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(220)) togetherWith fadeOut(tween(150))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding-step",
        ) { s ->
            // The pane title is announced when the step changes, so TalkBack users hear where they landed.
            val pane = stepTitle(s, state)
            Column(
                Modifier.fillMaxSize().semantics { paneTitle = pane }.verticalScroll(rememberScrollState())
                    .padding(horizontal = MukharSpace.gutter).padding(bottom = MukharSpace.l),
            ) {
                when (s) {
                    OnboardingStep.WELCOME -> WelcomeBody(state.yearText, state.yearValid, viewModel::onYearChange, viewModel::onAgeContinue)
                    OnboardingStep.BLOCKED -> BlockedBody()
                    OnboardingStep.LANGUAGES -> LanguagesBody(state, viewModel::onNameChange, viewModel::onLangChoice, viewModel::onToggleTrack)
                    OnboardingStep.WARMUP -> WarmupBody(state, viewModel::onReadInHindi)
                    OnboardingStep.MIC_DENIED -> MicDeniedBody()
                    OnboardingStep.DISCLOSURE -> DisclosureBody(state.analyticsOptIn, viewModel::onAnalyticsOptIn) {
                        runCatching { uriHandler.openUri(Links.PRIVACY_POLICY) }
                    }
                    OnboardingStep.DECLINED -> DeclinedBody()
                    OnboardingStep.BASELINE -> BaselineBody(state)
                    OnboardingStep.REMINDER -> ReminderBody(state, viewModel::onRoutine, viewModel::onCustomPhrase, viewModel::onTime, viewModel::onWeeklyGoal)
                    OnboardingStep.REMINDER_DENIED -> ReminderDeniedBody(state)
                    OnboardingStep.DONE -> Unit
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            when (step) {
                OnboardingStep.WELCOME -> WelcomeCta(state.yearValid, viewModel::onAgeContinue)
                OnboardingStep.BLOCKED -> BlockedCta(onExit)
                OnboardingStep.LANGUAGES -> LanguagesCta(state.tracks.isNotEmpty(), viewModel::onProfileContinue)
                OnboardingStep.WARMUP -> WarmupCta(state, ::requestMic, viewModel::onMicDenied, viewModel::onWarmupDone, viewModel::onWarmupContinue)
                OnboardingStep.MIC_DENIED -> MicDeniedCta(
                    permanentlyDenied = micPermanentlyDenied,
                    onTryAgain = {
                        viewModel.onRetryMic()
                        requestMic()
                    },
                    onOpenSettings = { context.openAppDetails() },
                    onContinueWithout = viewModel::onContinueWithoutMic,
                )
                OnboardingStep.DISCLOSURE -> DisclosureCta(viewModel::onAgree, viewModel::onNotNow)
                OnboardingStep.DECLINED -> DeclinedCta(viewModel::onDeclinedContinue, viewModel::onAgree)
                OnboardingStep.BASELINE -> BaselineCta(
                    state,
                    onRecord = viewModel::onRecordBaseline,
                    onLater = viewModel::onBaselineLater,
                    onNext = viewModel::onBaselineNext,
                    onStop = viewModel::onStopBaseline,
                    onKeep = viewModel::onKeepBaselineAudio,
                    onSavedContinue = viewModel::onBaselineSavedContinue,
                    onDiscard = viewModel::onDiscardBaseline,
                )
                OnboardingStep.REMINDER -> ReminderCta(state.reminderReady, ::remindMe, viewModel::onNoReminder)
                OnboardingStep.REMINDER_DENIED -> ReminderDeniedCta(viewModel::onFinish) { context.openNotificationSettings() }
                OnboardingStep.DONE -> Unit
            }
        }
    }

    state.oemGuide?.let { oem ->
        OemGuideSheet(
            oem = oem,
            onOpen = {
                oem.openSettings(context)
                viewModel.onOemGuideClosed()
            },
            onDismiss = viewModel::onOemGuideClosed,
        )
    }
}

@Composable
private fun stepTitle(step: OnboardingStep, state: OnboardingUiState): String = when (step) {
    OnboardingStep.WELCOME -> stringResource(R.string.welcome_title)
    OnboardingStep.BLOCKED -> stringResource(R.string.blocked_title)
    OnboardingStep.LANGUAGES -> stringResource(R.string.profile_title)
    OnboardingStep.WARMUP -> stringResource(R.string.warmup_title)
    OnboardingStep.MIC_DENIED -> stringResource(R.string.mic_denied_title)
    OnboardingStep.DISCLOSURE -> stringResource(R.string.disclosure_title)
    OnboardingStep.DECLINED -> stringResource(R.string.declined_title)
    OnboardingStep.BASELINE -> stringResource(R.string.baseline_title)
    OnboardingStep.REMINDER -> stringResource(R.string.reminder_title)
    OnboardingStep.REMINDER_DENIED, OnboardingStep.DONE -> state.firstName.trim().let {
        if (it.isEmpty()) stringResource(R.string.reminder_all_set) else stringResource(R.string.reminder_all_set_name, it)
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.openAppDetails() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
