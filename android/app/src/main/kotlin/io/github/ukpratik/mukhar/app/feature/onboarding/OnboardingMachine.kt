package io.github.ukpratik.mukhar.app.feature.onboarding

/**
 * Onboarding steps (design S01 to S06). [progress] is the segment shown in the 6-step bar: About you → Languages
 * and goals → Warm-up → Privacy → Starting point → Reminder.
 */
enum class OnboardingStep(val progress: Int, val canGoBack: Boolean) {
    WELCOME(1, canGoBack = false),
    BLOCKED(1, canGoBack = false),
    LANGUAGES(2, canGoBack = true),
    WARMUP(3, canGoBack = true),
    MIC_DENIED(3, canGoBack = true),
    DISCLOSURE(4, canGoBack = true),
    DECLINED(4, canGoBack = true),

    /** 2A (ADR-008): "How should Mukhar listen?" right after agreeing to S04 (same progress segment as Privacy). */
    LISTENING(4, canGoBack = true),
    BASELINE(5, canGoBack = true),
    REMINDER(6, canGoBack = true),
    REMINDER_DENIED(6, canGoBack = false),
    DONE(6, canGoBack = false);

    companion object {
        const val TOTAL = 6
    }
}

sealed interface OnboardingEvent {
    data class AgeEntered(val birthYear: Int, val currentYear: Int) : OnboardingEvent
    data object ProfileDone : OnboardingEvent
    data object WarmupDone : OnboardingEvent
    data object MicDenied : OnboardingEvent
    /** "Not now" on the warm-up with the mic already allowed: skip only the warm-up (I-001). */
    data object SkipWarmup : OnboardingEvent
    data object RetryMic : OnboardingEvent
    data object ContinueWithoutMic : OnboardingEvent
    data object Agree : OnboardingEvent
    data object NotNow : OnboardingEvent
    data object DeclinedContinue : OnboardingEvent
    data object ListeningChosen : OnboardingEvent
    data object BaselineDone : OnboardingEvent
    data class ReminderSet(val notificationsAllowed: Boolean) : OnboardingEvent
    data object NoReminder : OnboardingEvent
    data object Finish : OnboardingEvent
    data object Back : OnboardingEvent
}

/** Flow state. [micSkipped] and [consented] decide where Back from the reminder goes. */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val micSkipped: Boolean = false,
    val consented: Boolean? = null,
)

/** US-001: a plausible year (1900 to this year); age is counted from 1 January (a self-declared check). */
object AgeCheck {
    const val ADULT = 18
    const val EARLIEST_YEAR = 1900

    fun isPlausible(year: Int?, currentYear: Int): Boolean = year != null && year in EARLIEST_YEAR..currentYear

    fun isAdult(year: Int, currentYear: Int): Boolean = currentYear - year >= ADULT
}

/**
 * Pure transitions (unit-tested). Rules from the design notes: under 18 is a final stop; no mic skips S04 and S05
 * (no audio will leave the phone); "Not now" on S04 skips S05 (the baseline needs analysis). Agreeing on S04 leads to
 * the listening choice (ADR-008), then S05.
 */
object OnboardingMachine {
    fun reduce(state: OnboardingState, event: OnboardingEvent): OnboardingState = when (event) {
        is OnboardingEvent.AgeEntered -> when {
            state.step != OnboardingStep.WELCOME -> state
            !AgeCheck.isPlausible(event.birthYear, event.currentYear) -> state
            AgeCheck.isAdult(event.birthYear, event.currentYear) -> state.copy(step = OnboardingStep.LANGUAGES)
            else -> state.copy(step = OnboardingStep.BLOCKED)
        }
        OnboardingEvent.ProfileDone -> state.onlyFrom(OnboardingStep.LANGUAGES) { copy(step = OnboardingStep.WARMUP) }
        OnboardingEvent.WarmupDone -> state.onlyFrom(OnboardingStep.WARMUP) {
            copy(step = OnboardingStep.DISCLOSURE, micSkipped = false)
        }
        OnboardingEvent.MicDenied -> state.onlyFrom(OnboardingStep.WARMUP) { copy(step = OnboardingStep.MIC_DENIED) }
        OnboardingEvent.SkipWarmup -> state.onlyFrom(OnboardingStep.WARMUP) { copy(step = OnboardingStep.DISCLOSURE, micSkipped = false) }
        OnboardingEvent.RetryMic -> state.onlyFrom(OnboardingStep.MIC_DENIED) { copy(step = OnboardingStep.WARMUP) }
        OnboardingEvent.ContinueWithoutMic -> state.onlyFrom(OnboardingStep.MIC_DENIED) {
            copy(step = OnboardingStep.REMINDER, micSkipped = true)
        }
        OnboardingEvent.Agree -> if (state.step == OnboardingStep.DISCLOSURE || state.step == OnboardingStep.DECLINED) {
            state.copy(step = OnboardingStep.LISTENING, consented = true)
        } else {
            state
        }
        OnboardingEvent.NotNow -> state.onlyFrom(OnboardingStep.DISCLOSURE) {
            copy(step = OnboardingStep.DECLINED, consented = false)
        }
        OnboardingEvent.DeclinedContinue -> state.onlyFrom(OnboardingStep.DECLINED) { copy(step = OnboardingStep.REMINDER) }
        OnboardingEvent.ListeningChosen -> state.onlyFrom(OnboardingStep.LISTENING) { copy(step = OnboardingStep.BASELINE) }
        OnboardingEvent.BaselineDone -> state.onlyFrom(OnboardingStep.BASELINE) { copy(step = OnboardingStep.REMINDER) }
        is OnboardingEvent.ReminderSet -> state.onlyFrom(OnboardingStep.REMINDER) {
            copy(step = if (event.notificationsAllowed) OnboardingStep.DONE else OnboardingStep.REMINDER_DENIED)
        }
        OnboardingEvent.NoReminder -> state.onlyFrom(OnboardingStep.REMINDER) { copy(step = OnboardingStep.DONE) }
        OnboardingEvent.Finish -> state.copy(step = OnboardingStep.DONE)
        OnboardingEvent.Back -> state.copy(step = backFrom(state))
    }

    private fun backFrom(state: OnboardingState): OnboardingStep = when (state.step) {
        OnboardingStep.LANGUAGES -> OnboardingStep.WELCOME
        OnboardingStep.WARMUP, OnboardingStep.MIC_DENIED -> OnboardingStep.LANGUAGES
        OnboardingStep.DISCLOSURE -> OnboardingStep.WARMUP
        OnboardingStep.DECLINED -> OnboardingStep.DISCLOSURE
        OnboardingStep.LISTENING -> OnboardingStep.DISCLOSURE
        OnboardingStep.BASELINE -> OnboardingStep.LISTENING
        OnboardingStep.REMINDER -> when {
            state.micSkipped -> OnboardingStep.MIC_DENIED
            state.consented == false -> OnboardingStep.DECLINED
            else -> OnboardingStep.BASELINE
        }
        else -> state.step
    }

    private inline fun OnboardingState.onlyFrom(step: OnboardingStep, next: OnboardingState.() -> OnboardingState) =
        if (this.step == step) next() else this
}
