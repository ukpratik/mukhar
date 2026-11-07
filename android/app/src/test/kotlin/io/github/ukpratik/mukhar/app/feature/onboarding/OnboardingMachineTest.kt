package io.github.ukpratik.mukhar.app.feature.onboarding

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.app.feature.onboarding.OnboardingEvent as E
import io.github.ukpratik.mukhar.app.feature.onboarding.OnboardingStep as S
import org.junit.Test

/** The pure onboarding state machine (S01 to S06 flow lines). */
class OnboardingMachineTest {
    private fun run(vararg events: E, from: OnboardingState = OnboardingState()): OnboardingState =
        events.fold(from) { s, e -> OnboardingMachine.reduce(s, e) }

    @Test
    fun ageGate() {
        assertThat(run(E.AgeEntered(1998, 2026)).step).isEqualTo(S.LANGUAGES)
        assertThat(run(E.AgeEntered(2008, 2026)).step).isEqualTo(S.LANGUAGES) // 18 on 1 January
        assertThat(run(E.AgeEntered(2009, 2026)).step).isEqualTo(S.BLOCKED)
        assertThat(run(E.AgeEntered(1899, 2026)).step).isEqualTo(S.WELCOME) // implausible: ignored
        assertThat(run(E.AgeEntered(2027, 2026)).step).isEqualTo(S.WELCOME)
    }

    @Test
    fun blockedIsFinal() {
        val blocked = run(E.AgeEntered(2012, 2026))
        assertThat(run(E.Back, from = blocked).step).isEqualTo(S.BLOCKED)
        assertThat(run(E.AgeEntered(1990, 2026), from = blocked).step).isEqualTo(S.BLOCKED)
        assertThat(S.BLOCKED.canGoBack).isFalse()
    }

    @Test
    fun happyPathAgreeRecordsBaselineThenReminder() {
        val s = run(E.AgeEntered(1998, 2026), E.ProfileDone, E.WarmupDone, E.Agree, E.ListeningChosen, E.BaselineDone, E.ReminderSet(true))
        assertThat(s.step).isEqualTo(S.DONE)
        assertThat(s.consented).isTrue()
    }

    @Test
    fun notNowSkipsTheBaseline() {
        val declined = run(E.AgeEntered(1998, 2026), E.ProfileDone, E.WarmupDone, E.NotNow)
        assertThat(declined.step).isEqualTo(S.DECLINED)
        assertThat(run(E.DeclinedContinue, from = declined).step).isEqualTo(S.REMINDER)
        // "I've changed my mind, agree" goes to the listening choice (ADR-008), then the baseline.
        assertThat(run(E.Agree, from = declined).step).isEqualTo(S.LISTENING)
        assertThat(run(E.Agree, E.ListeningChosen, from = declined).step).isEqualTo(S.BASELINE)
    }

    @Test
    fun skippingOnlyTheWarmupGoesToPrivacy() {
        val s = run(E.AgeEntered(1998, 2026), E.ProfileDone, E.SkipWarmup)
        assertThat(s.step).isEqualTo(S.DISCLOSURE)
        assertThat(s.micSkipped).isFalse()
        assertThat(run(E.Agree, from = s).step).isEqualTo(S.LISTENING)
        assertThat(run(E.Back, from = s).step).isEqualTo(S.WARMUP)
    }

    @Test
    fun noMicSkipsPrivacyAndBaseline() {
        val denied = run(E.AgeEntered(1998, 2026), E.ProfileDone, E.MicDenied)
        assertThat(denied.step).isEqualTo(S.MIC_DENIED)
        assertThat(run(E.RetryMic, from = denied).step).isEqualTo(S.WARMUP)
        val skipped = run(E.ContinueWithoutMic, from = denied)
        assertThat(skipped.step).isEqualTo(S.REMINDER)
        assertThat(skipped.micSkipped).isTrue()
        assertThat(run(E.Back, from = skipped).step).isEqualTo(S.MIC_DENIED)
    }

    @Test
    fun notificationsDeniedShowsTheDeniedStateThenFinishes() {
        val atReminder = OnboardingState(step = S.REMINDER, consented = true)
        val denied = run(E.ReminderSet(false), from = atReminder)
        assertThat(denied.step).isEqualTo(S.REMINDER_DENIED)
        assertThat(run(E.Finish, from = denied).step).isEqualTo(S.DONE)
        assertThat(run(E.NoReminder, from = atReminder).step).isEqualTo(S.DONE)
    }

    @Test
    fun backFollowsTheFlow() {
        assertThat(run(E.Back, from = OnboardingState(S.LANGUAGES)).step).isEqualTo(S.WELCOME)
        assertThat(run(E.Back, from = OnboardingState(S.DISCLOSURE)).step).isEqualTo(S.WARMUP)
        assertThat(run(E.Back, from = OnboardingState(S.DECLINED)).step).isEqualTo(S.DISCLOSURE)
        assertThat(run(E.Back, from = OnboardingState(S.REMINDER, consented = true)).step).isEqualTo(S.BASELINE)
        assertThat(run(E.Back, from = OnboardingState(S.REMINDER, consented = false)).step).isEqualTo(S.DECLINED)
    }

    @Test
    fun eventsFromTheWrongStepAreIgnored() {
        assertThat(run(E.WarmupDone).step).isEqualTo(S.WELCOME)
        assertThat(run(E.BaselineDone, from = OnboardingState(S.LANGUAGES)).step).isEqualTo(S.LANGUAGES)
    }

    @Test
    fun progressHasSixSteps() {
        assertThat(S.entries.map { it.progress }.distinct().sorted()).containsExactly(1, 2, 3, 4, 5, 6).inOrder()
    }

    @Test
    fun theListeningChoiceSitsBetweenPrivacyAndTheBaseline() {
        val agreed = run(E.AgeEntered(1998, 2026), E.ProfileDone, E.WarmupDone, E.Agree)
        assertThat(agreed.step).isEqualTo(S.LISTENING)
        assertThat(agreed.step.progress).isEqualTo(S.DISCLOSURE.progress)
        assertThat(run(E.Back, from = agreed).step).isEqualTo(S.DISCLOSURE)
        assertThat(run(E.Back, from = OnboardingState(S.BASELINE, consented = true)).step).isEqualTo(S.LISTENING)
        // Only the choice moves it on.
        assertThat(run(E.BaselineDone, from = agreed).step).isEqualTo(S.LISTENING)
    }
}
