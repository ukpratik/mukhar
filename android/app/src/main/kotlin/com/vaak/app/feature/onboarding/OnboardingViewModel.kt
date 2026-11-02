package com.vaak.app.feature.onboarding

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaak.app.feature.onboarding.warmup.WarmupAnalyzer
import com.vaak.app.feature.onboarding.warmup.WarmupMeter
import com.vaak.app.feature.onboarding.warmup.WarmupResult
import com.vaak.data.baseline.BaselineRecorder
import com.vaak.data.baseline.BaselineResult
import com.vaak.data.prefs.AgeGate
import com.vaak.data.prefs.GoalTrack
import com.vaak.data.prefs.ReminderSettings
import com.vaak.data.prefs.Routine
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.repo.HabitRepository
import com.vaak.data.work.ReminderScheduler
import com.vaak.domain.Clock
import com.vaak.domain.model.Lang
import com.vaak.domain.streak.Activity
import com.vaak.domain.streak.HabitState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

enum class LangChoice(val langs: Set<Lang>) {
    EN(setOf(Lang.EN)),
    HI(setOf(Lang.HI)),
    BOTH(setOf(Lang.EN, Lang.HI)),
}

sealed interface WarmupUi {
    data object Ready : WarmupUi
    data class Recording(val elapsedMs: Long, val levels: List<Float>) : WarmupUi
    data object TooShort : WarmupUi
    data class Done(val result: WarmupResult, val day: Int) : WarmupUi
    data object MicError : WarmupUi
}

sealed interface BaselineUi {
    data object Offer : BaselineUi
    data class Recording(val elapsedMs: Long) : BaselineUi
    data object TooShort : BaselineUi
    data class Saved(val sessionId: String, val durationMs: Long) : BaselineUi
    data object Later : BaselineUi

    /** After Stop, while the baseline is analysed (S05 → S10 analysing view). */
    data object Analysing : BaselineUi

    /** 409 BASELINE_USED (D-031): this account or phone already has a starting point. */
    data object AlreadyUsed : BaselineUi
}

data class OnboardingUiState(
    val loading: Boolean = true,
    val flow: OnboardingState = OnboardingState(),
    val yearText: String = "",
    val currentYear: Int = 2026,
    val firstName: String = "",
    val langChoice: LangChoice = LangChoice.EN,
    val tracks: Set<GoalTrack> = emptySet(),
    val trackLimitHit: Boolean = false,
    val warmup: WarmupUi = WarmupUi.Ready,
    val readInHindi: Boolean = false,
    val analyticsOptIn: Boolean = false,
    val baseline: BaselineUi = BaselineUi.Offer,
    val keepBaselineAudio: Boolean = false,
    val confirmDiscardBaseline: Boolean = false,
    val routine: Routine = Routine.MORNING_TEA,
    val customPhrase: String = "",
    val time: LocalTime = Routine.MORNING_TEA.suggestedTime,
    val weeklyGoal: Int = HabitState().weeklyGoal,
    val oemGuide: Oem? = null,
    val warmupCredited: Boolean = false,
    /** S04 or S05 opened on its own from Today (not inside onboarding): leaving the step closes the screen. */
    val standalone: Boolean = false,
) {
    val step: OnboardingStep get() = flow.step
    val birthYear: Int? get() = yearText.toIntOrNull()
    val yearValid: Boolean get() = yearText.length == 4 && AgeCheck.isPlausible(birthYear, currentYear)
    val reminder: ReminderSettings
        get() = ReminderSettings(time, routine, customPhrase.trim().takeIf { routine == Routine.CUSTOM })
    val reminderReady: Boolean get() = routine != Routine.CUSTOM || customPhrase.isNotBlank()

    /** The read-aloud language: Hindi only → Hindi; Both → English with a "Read in Hindi" switch. */
    val readAloudLang: Lang
        get() = when {
            langChoice == LangChoice.HI -> Lang.HI
            langChoice == LangChoice.BOTH && readInHindi -> Lang.HI
            else -> Lang.EN
        }
}

sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}

/**
 * S01 to S06 (US-001 to US-004). One ViewModel for the whole flow; transitions come from [OnboardingMachine] and
 * every answer is written to [SettingsStore] as it's given, so the flow resumes where it was left.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val habit: HabitRepository,
    private val scheduler: ReminderScheduler,
    private val baselineRecorder: BaselineRecorder,
    private val meter: WarmupMeter,
    private val clock: Clock,
) : ViewModel() {

    /** Overridable in tests; the OEM battery guide is keyed on it (S06 state 5). */
    internal var manufacturer: String = Build.MANUFACTURER

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    private var warmupJob: Job? = null
    @Volatile private var stopWarmup = false
    private var baselineJob: Job? = null
    private var standalone = false

    init {
        viewModelScope.launch {
            val s = settings.current()
            val year = clock.now().atZone(clock.zone()).year
            val restored = s.onboardingStep?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
            val step = when {
                s.ageGate == AgeGate.BLOCKED -> OnboardingStep.BLOCKED
                restored != null && s.ageGate == AgeGate.PASSED -> restored
                else -> OnboardingStep.WELCOME
            }
            val goal = habit.snapshot().habit.weeklyGoal
            _state.update {
                it.copy(
                    loading = false,
                    currentYear = year,
                    flow = if (standalone) it.flow else OnboardingState(
                        step = step,
                        micSkipped = s.micSkipped,
                        consented = when {
                            s.disclosureConsentAt != null -> true
                            step == OnboardingStep.DECLINED || (step >= OnboardingStep.REMINDER && !s.micSkipped) -> false
                            else -> null
                        },
                    ),
                    firstName = s.firstName.orEmpty(),
                    langChoice = when (s.langs) {
                        LangChoice.HI.langs -> LangChoice.HI
                        LangChoice.BOTH.langs -> LangChoice.BOTH
                        else -> it.langChoice
                    },
                    tracks = s.tracks,
                    analyticsOptIn = s.analyticsConsent,
                    weeklyGoal = goal,
                    warmupCredited = habit.snapshot().days.isNotEmpty(),
                )
            }
        }
    }

    /** Opens one step on its own (Today's "Turn on talks" opens S04, the baseline card opens S05). */
    fun startStandalone(step: OnboardingStep) {
        if (standalone) return
        standalone = true
        _state.update { it.copy(flow = it.flow.copy(step = step), standalone = true) }
    }

    /** Preselect Hindi when the phone language is Hindi (S02). */
    fun preselectLanguage(phoneLanguage: String) {
        if (phoneLanguage == "hi" && _state.value.tracks.isEmpty()) {
            _state.update { if (it.langChoice == LangChoice.EN) it.copy(langChoice = LangChoice.HI) else it }
        }
    }

    // S01

    fun onYearChange(text: String) = _state.update { it.copy(yearText = text.filter(Char::isDigit).take(4)) }

    fun onAgeContinue() {
        val s = _state.value
        val year = s.birthYear ?: return
        if (!s.yearValid) return
        viewModelScope.launch {
            // Only the band is stored, never the year (US-001). Blocked stores nothing else.
            settings.setAgeGate(if (AgeCheck.isAdult(year, s.currentYear)) AgeGate.PASSED else AgeGate.BLOCKED)
            move(OnboardingEvent.AgeEntered(year, s.currentYear))
            _state.update { it.copy(yearText = if (it.step == OnboardingStep.BLOCKED) "" else it.yearText) }
        }
    }

    // S02

    fun onNameChange(name: String) = _state.update { it.copy(firstName = name.take(MAX_NAME)) }

    fun onLangChoice(choice: LangChoice) = _state.update { it.copy(langChoice = choice) }

    /** 1 to 3 goals; a 4th tap is ignored and explained (S02 state 3). */
    fun onToggleTrack(track: GoalTrack) = _state.update {
        when {
            track in it.tracks -> it.copy(tracks = it.tracks - track, trackLimitHit = false)
            it.tracks.size >= GoalTrack.MAX_PICKED -> it.copy(trackLimitHit = true)
            else -> it.copy(tracks = it.tracks + track, trackLimitHit = false)
        }
    }

    fun onProfileContinue() {
        val s = _state.value
        if (s.tracks.isEmpty()) return
        viewModelScope.launch {
            settings.setProfile(s.firstName, s.langChoice.langs, s.tracks)
            move(OnboardingEvent.ProfileDone)
        }
    }

    // S03

    fun onReadInHindi(enabled: Boolean) = _state.update { it.copy(readInHindi = enabled) }

    /** Starts on mic grant. Auto-stops at 0:20; [onWarmupDone] stops early. */
    fun startWarmup(paragraph: String) {
        warmupJob?.cancel()
        stopWarmup = false
        val lang = _state.value.readAloudLang
        val analyzer = WarmupAnalyzer(WarmupAnalyzer.countWords(paragraph), lang)
        _state.update { it.copy(warmup = WarmupUi.Recording(0, emptyList())) }
        warmupJob = viewModelScope.launch {
            var elapsed = 0L
            val levels = ArrayDeque<Float>()
            try {
                meter.frames()
                    .takeWhile { !stopWarmup && elapsed < WarmupAnalyzer.DURATION_MS }
                    .collect { frame ->
                        val level = analyzer.feed(frame)
                        elapsed += frame.size * 1000L / WarmupAnalyzer.SAMPLE_RATE
                        if (elapsed % UI_EVERY_MS < WarmupAnalyzer.FRAME_MS) {
                            levels.addLast(level)
                            while (levels.size > WAVE_BARS) levels.removeFirst()
                            _state.update { it.copy(warmup = WarmupUi.Recording(elapsed, levels.toList())) }
                        }
                    }
                finishWarmup(analyzer.result())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(warmup = WarmupUi.MicError) }
            }
        }
    }

    fun onWarmupDone() {
        stopWarmup = true
    }

    /** Back while recording discards the warm-up (no prompt, S03 notes). */
    fun cancelWarmup() {
        warmupJob?.cancel()
        _state.update { it.copy(warmup = WarmupUi.Ready) }
    }

    private suspend fun finishWarmup(result: WarmupResult) {
        if (!result.enoughSpeech) {
            _state.update { it.copy(warmup = WarmupUi.TooShort) }
            return
        }
        // Finishing the warm-up counts as day 1 (the onboarding exception to US-031).
        val credit = habit.credit(Activity.Onboarding(clock.now(), clock.zone()))
        _state.update { it.copy(warmup = WarmupUi.Done(result, credit.streak.coerceAtLeast(1)), warmupCredited = true) }
    }

    fun onWarmupContinue() = move(OnboardingEvent.WarmupDone)

    fun onMicDenied() {
        cancelWarmup()
        move(OnboardingEvent.MicDenied)
    }

    fun onRetryMic() = move(OnboardingEvent.RetryMic)

    fun onContinueWithoutMic() {
        viewModelScope.launch {
            settings.setMicSkipped(true)
            move(OnboardingEvent.ContinueWithoutMic)
        }
    }

    // S04

    fun onAnalyticsOptIn(enabled: Boolean) = _state.update { it.copy(analyticsOptIn = enabled) }

    /** Agree records consent only; the analytics switch is saved as set, independent of Agree (D-026, D-028). */
    fun onAgree() {
        viewModelScope.launch {
            settings.recordDisclosure(clock.now(), _state.value.analyticsOptIn)
            move(OnboardingEvent.Agree)
        }
    }

    fun onNotNow() {
        viewModelScope.launch {
            settings.recordDisclosure(null, _state.value.analyticsOptIn)
            move(OnboardingEvent.NotNow)
        }
    }

    fun onDeclinedContinue() = move(OnboardingEvent.DeclinedContinue)

    // S05

    fun onRecordBaseline() {
        val s = _state.value
        baselineJob?.cancel()
        baselineJob = viewModelScope.launch {
            val lang = if (s.langChoice == LangChoice.HI) Lang.HI else Lang.EN
            if (!baselineRecorder.isAvailable || !baselineRecorder.start(lang, BASELINE_PROMPT_ID)) {
                onBaselineLater()
                return@launch
            }
            baselineRecorder.progress.collect { p ->
                when {
                    p != null -> _state.update { it.copy(baseline = BaselineUi.Recording(p.elapsedMs)) }
                    // The recorder stopped on its own (1:00 cap or a mic failure): save it as if Stop was tapped.
                    _state.value.baseline is BaselineUi.Recording -> onStopBaseline()
                }
            }
        }
    }

    fun onStopBaseline() {
        if (_state.value.baseline !is BaselineUi.Recording) return
        _state.update { it.copy(baseline = BaselineUi.Analysing) }
        viewModelScope.launch {
            val result = baselineRecorder.stop()
            baselineJob?.cancel()
            // Another baseline already exists for this account or phone: nothing to wait for on Today.
            if (result == BaselineResult.AlreadyUsed) settings.setBaseline(recorded = true, keepAudio = false)
            _state.update {
                it.copy(
                    baseline = when (result) {
                        is BaselineResult.Saved -> BaselineUi.Saved(result.sessionId, result.durationMs)
                        BaselineResult.TooShort -> BaselineUi.TooShort
                        BaselineResult.Failed -> BaselineUi.Offer
                        BaselineResult.AlreadyUsed -> BaselineUi.AlreadyUsed
                    },
                )
            }
        }
    }

    fun onBaselineBack(): Boolean {
        if (_state.value.baseline !is BaselineUi.Recording) return false
        _state.update { it.copy(confirmDiscardBaseline = true) }
        return true
    }

    fun onDiscardBaseline(discard: Boolean) {
        _state.update { it.copy(confirmDiscardBaseline = false) }
        if (!discard) return
        viewModelScope.launch {
            baselineRecorder.discard()
            baselineJob?.cancel()
            _state.update { it.copy(baseline = BaselineUi.Offer) }
        }
    }

    fun onKeepBaselineAudio(keep: Boolean) = _state.update { it.copy(keepBaselineAudio = keep) }

    /**
     * A baseline recovered after the app closed mid-recording (Today → S09 state 8 → here): analyse it as the
     * baseline, then ask "Keep this recording?" as after a normal stop (US-003).
     */
    fun resumeRecoveredBaseline(sessionId: String) {
        if (_state.value.baseline !is BaselineUi.Offer) return
        _state.update { it.copy(baseline = BaselineUi.Analysing) }
        viewModelScope.launch {
            val result = baselineRecorder.resume(sessionId)
            if (result == BaselineResult.AlreadyUsed) settings.setBaseline(recorded = true, keepAudio = false)
            _state.update {
                it.copy(
                    baseline = when (result) {
                        is BaselineResult.Saved -> BaselineUi.Saved(result.sessionId, result.durationMs)
                        BaselineResult.AlreadyUsed -> BaselineUi.AlreadyUsed
                        BaselineResult.TooShort, BaselineResult.Failed -> BaselineUi.Offer
                    },
                )
            }
        }
    }

    /**
     * Leaving S05 before answering "Keep this recording?" (back, closing the screen) applies the default: off, so
     * the audio is deleted (US-003, D-010). Process death is covered at the next start by
     * `TalkRepository.releaseUnansweredBaselineAudio`.
     */
    override fun onCleared() {
        val saved = _state.value.baseline as? BaselineUi.Saved ?: return
        if (!baselineAnswered) baselineRecorder.abandon(saved.sessionId)
    }

    private var baselineAnswered = false

    /** Keep-audio is asked once, off by default (US-003); the numbers are kept either way. */
    fun onBaselineSavedContinue() {
        val saved = _state.value.baseline as? BaselineUi.Saved ?: return
        val keep = _state.value.keepBaselineAudio
        baselineAnswered = true
        viewModelScope.launch {
            baselineRecorder.keepAudio(saved.sessionId, keep)
            settings.setBaseline(recorded = true, keepAudio = keep)
            move(OnboardingEvent.BaselineDone)
        }
    }

    /** "No rush. Saved for later." for 1.5 s, then on to the reminder. Today keeps the baseline card. */
    fun onBaselineLater() {
        _state.update { it.copy(baseline = BaselineUi.Later) }
        viewModelScope.launch {
            delay(LATER_TOAST_MS)
            if (_state.value.baseline == BaselineUi.Later && _state.value.step == OnboardingStep.BASELINE) onBaselineNext()
        }
    }

    fun onBaselineNext() = move(OnboardingEvent.BaselineDone)

    // S06

    fun onRoutine(routine: Routine) = _state.update { it.copy(routine = routine, time = routine.suggestedTime) }

    fun onCustomPhrase(text: String) = _state.update { it.copy(customPhrase = text.take(ReminderSettings.MAX_PHRASE)) }

    fun onTime(time: LocalTime) = _state.update { it.copy(time = time) }

    fun onWeeklyGoal(goal: Int) = _state.update { it.copy(weeklyGoal = goal) }

    /** After "Remind me" and (on Android 13+) the permission answer. */
    fun onReminderConfirmed(notificationsAllowed: Boolean) {
        val s = _state.value
        if (!s.reminderReady) return
        viewModelScope.launch {
            settings.setReminder(s.reminder)
            habit.setWeeklyGoal(s.weeklyGoal)
            val oem = Oem.fromManufacturer(manufacturer)
            if (notificationsAllowed && oem != null && !settings.current().oemGuideShown) {
                settings.markOemGuideShown()
                _state.update { it.copy(oemGuide = oem) }
            } else {
                move(OnboardingEvent.ReminderSet(notificationsAllowed))
            }
        }
    }

    fun onNoReminder() {
        viewModelScope.launch {
            settings.setReminder(null)
            habit.setWeeklyGoal(_state.value.weeklyGoal)
            move(OnboardingEvent.NoReminder)
        }
    }

    /** Closing the battery guide (either button) goes to Today. */
    fun onOemGuideClosed() {
        _state.update { it.copy(oemGuide = null) }
        move(OnboardingEvent.Finish)
    }

    fun onFinish() = move(OnboardingEvent.Finish)

    /** Returns false when Back should leave the app (first step or a final stop). */
    fun onBack(): Boolean {
        val s = _state.value
        if (s.warmup is WarmupUi.Recording) {
            cancelWarmup()
            return true
        }
        if (s.step == OnboardingStep.BASELINE && onBaselineBack()) return true
        if (standalone || !s.step.canGoBack) return false
        move(OnboardingEvent.Back)
        return true
    }

    private fun move(event: OnboardingEvent) {
        if (standalone) {
            if (OnboardingMachine.reduce(_state.value.flow, event).step != _state.value.step) {
                viewModelScope.launch { _effects.send(OnboardingEffect.Finished) }
            }
            return
        }
        val before = _state.value.step
        _state.update { it.copy(flow = OnboardingMachine.reduce(it.flow, event)) }
        val after = _state.value.step
        if (after == before) return
        viewModelScope.launch {
            when (after) {
                OnboardingStep.DONE -> {
                    settings.completeOnboarding(clock.now())
                    scheduler.reschedule()
                    _effects.send(OnboardingEffect.Finished)
                }
                OnboardingStep.WELCOME, OnboardingStep.BLOCKED -> settings.setOnboardingStep(null)
                else -> settings.setOnboardingStep(after.name)
            }
        }
    }

    private companion object {
        const val MAX_NAME = 40
        const val UI_EVERY_MS = 100L
        const val WAVE_BARS = 24
        const val LATER_TOAST_MS = 1_500L
        const val BASELINE_PROMPT_ID = "baseline_intro"
    }
}
