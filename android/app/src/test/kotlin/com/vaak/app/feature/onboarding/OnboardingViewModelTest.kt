package com.vaak.app.feature.onboarding

import android.app.AlarmManager
import com.google.common.truth.Truth.assertThat
import com.vaak.app.testing.OnboardingHarness
import com.vaak.app.testing.waitFor
import com.vaak.data.baseline.UnavailableBaselineRecorder
import com.vaak.data.prefs.AgeGate
import com.vaak.data.prefs.GoalTrack
import com.vaak.data.prefs.Routine
import com.vaak.data.prefs.Settings
import com.vaak.domain.model.Lang
import com.vaak.domain.streak.Source
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest {
    private var h = OnboardingHarness()

    @After
    fun tearDown() = h.close()

    private val state get() = h.vm.state.value
    private fun settings(): Settings = runBlocking { h.settings.current() }
    private fun await(step: OnboardingStep) = waitFor { state.step == step }

    @Test
    fun underEighteenStoresNothingButTheBlockedFlag() {
        waitFor { !state.loading }
        h.vm.onYearChange("2012")
        assertThat(state.yearValid).isTrue()
        h.vm.onAgeContinue()
        await(OnboardingStep.BLOCKED)
        waitFor { settings().ageGate == AgeGate.BLOCKED }
        assertThat(settings()).isEqualTo(Settings(ageGate = AgeGate.BLOCKED))
        assertThat(state.yearText).isEmpty()
        assertThat(h.vm.onBack()).isFalse() // final stop: Back leaves the app
    }

    @Test
    fun blockedStaysBlockedOnTheNextLaunch() {
        runBlocking { h.settings.setAgeGate(AgeGate.BLOCKED) }
        waitFor { !state.loading }
        assertThat(state.step).isEqualTo(OnboardingStep.BLOCKED)
    }

    @Test
    fun theYearIsNeverStored() {
        waitFor { !state.loading }
        h.vm.onYearChange("19a98x")
        assertThat(state.yearText).isEqualTo("1998")
        h.vm.onAgeContinue()
        await(OnboardingStep.LANGUAGES)
        waitFor { settings().ageGate == AgeGate.PASSED }
        assertThat(settings().toString()).doesNotContain("1998")
    }

    @Test
    fun fullFlowWithBaselineAndReminder() {
        waitFor { !state.loading }
        h.vm.onYearChange("1998")
        h.vm.onAgeContinue()
        await(OnboardingStep.LANGUAGES)

        h.vm.onNameChange("Rohan")
        h.vm.onLangChoice(LangChoice.BOTH)
        h.vm.onToggleTrack(GoalTrack.INTERVIEWS)
        h.vm.onProfileContinue()
        await(OnboardingStep.WARMUP)
        assertThat(settings().firstName).isEqualTo("Rohan")
        assertThat(settings().langs).containsExactly(Lang.EN, Lang.HI)

        h.vm.startWarmup(h.context.getString(com.vaak.app.R.string.warmup_text_en))
        waitFor { state.warmup is WarmupUi.Done }
        assertThat((state.warmup as WarmupUi.Done).day).isEqualTo(1)
        // Finishing the warm-up counts as day 1 (onboarding exception).
        assertThat(runBlocking { h.habit.snapshot() }.todaySource).isEqualTo(Source.ONBOARDING)

        h.vm.onWarmupContinue()
        await(OnboardingStep.DISCLOSURE)
        assertThat(state.analyticsOptIn).isFalse()
        h.vm.onAgree()
        await(OnboardingStep.BASELINE)
        assertThat(settings().disclosureConsentAt).isNotNull()
        assertThat(settings().analyticsConsent).isFalse() // Agree doesn't enable analytics (D-026)

        h.vm.onRecordBaseline()
        waitFor { state.baseline is BaselineUi.Recording }
        h.vm.onStopBaseline()
        waitFor { state.baseline is BaselineUi.Saved }
        assertThat(state.keepBaselineAudio).isFalse() // keep-audio defaults to off
        h.vm.onBaselineSavedContinue()
        await(OnboardingStep.REMINDER)
        assertThat(settings().baselineRecorded).isTrue()
        assertThat(settings().keepBaselineAudio).isFalse()

        h.vm.onRoutine(Routine.LUNCH)
        assertThat(state.time).isEqualTo(LocalTime.of(13, 30))
        h.vm.onWeeklyGoal(3)
        h.vm.onReminderConfirmed(notificationsAllowed = true)
        await(OnboardingStep.DONE)
        waitFor { settings().onboardingDone }
        assertThat(settings().reminder!!.routine).isEqualTo(Routine.LUNCH)
        assertThat(runBlocking { h.habit.snapshot() }.habit.weeklyGoal).isEqualTo(3)
        waitFor { shadowOf(h.context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isNotEmpty() }
    }

    @Test
    fun noMicSkipsPrivacyAndBaselineAndGivesNoCredit() {
        h.resumeAt(OnboardingStep.WARMUP)
        waitFor { !state.loading }
        h.vm.onMicDenied()
        await(OnboardingStep.MIC_DENIED)
        h.vm.onContinueWithoutMic()
        await(OnboardingStep.REMINDER)
        assertThat(settings().micSkipped).isTrue()
        assertThat(settings().talksEnabled).isFalse()
        assertThat(runBlocking { h.habit.snapshot() }.days).isEmpty()
    }

    @Test
    fun tooLittleSpeechAsksForAnotherGo() {
        h.close()
        h = OnboardingHarness(meter = com.vaak.app.testing.FakeWarmupMeter(speech = false))
        h.resumeAt(OnboardingStep.WARMUP)
        waitFor { !state.loading }
        h.vm.startWarmup("Every morning I make a cup of tea.")
        waitFor { state.warmup == WarmupUi.TooShort }
        assertThat(runBlocking { h.habit.snapshot() }.days).isEmpty()
    }

    @Test
    fun withoutARecorderTheBaselineTakesTheLaterPath() {
        h.close()
        h = OnboardingHarness(recorder = UnavailableBaselineRecorder())
        h.resumeAt(OnboardingStep.BASELINE)
        waitFor { !state.loading && state.step == OnboardingStep.BASELINE }
        h.vm.onRecordBaseline()
        waitFor { state.baseline == BaselineUi.Later }
        h.vm.onBaselineNext()
        await(OnboardingStep.REMINDER)
        assertThat(settings().baselineRecorded).isFalse() // the Today card stays
    }

    @Test
    fun notificationsDeniedKeepsThePlan() {
        h.resumeAt(OnboardingStep.REMINDER)
        waitFor { !state.loading && state.step == OnboardingStep.REMINDER }
        h.vm.onReminderConfirmed(notificationsAllowed = false)
        await(OnboardingStep.REMINDER_DENIED)
        assertThat(settings().reminder).isNotNull()
        h.vm.onFinish()
        waitFor { settings().onboardingDone }
    }

    @Test
    fun theBatteryGuideShowsOnceOnXiaomi() {
        h.resumeAt(OnboardingStep.REMINDER)
        waitFor { !state.loading && state.step == OnboardingStep.REMINDER }
        h.vm.manufacturer = "Xiaomi"
        h.vm.onReminderConfirmed(notificationsAllowed = true)
        waitFor { state.oemGuide == Oem.XIAOMI }
        assertThat(settings().oemGuideShown).isTrue()
        h.vm.onOemGuideClosed()
        await(OnboardingStep.DONE)
        assertThat(Oem.fromManufacturer("Google")).isNull()
        assertThat(Oem.fromManufacturer("realme")).isEqualTo(Oem.REALME)
        assertThat(Oem.fromManufacturer("vivo")).isEqualTo(Oem.VIVO)
        assertThat(Oem.fromManufacturer("OPPO")).isEqualTo(Oem.OPPO)
    }

    @Test
    fun aFourthGoalIsIgnoredAndExplained() {
        h.resumeAt(OnboardingStep.LANGUAGES)
        waitFor { !state.loading }
        listOf(GoalTrack.PROFESSIONAL, GoalTrack.INTERVIEWS, GoalTrack.WORD_POWER, GoalTrack.EVERYDAY).forEach(h.vm::onToggleTrack)
        assertThat(state.tracks).hasSize(3)
        assertThat(state.trackLimitHit).isTrue()
        h.vm.onToggleTrack(GoalTrack.WORD_POWER)
        assertThat(state.tracks).hasSize(2)
        assertThat(state.trackLimitHit).isFalse()
    }

    @Test
    fun turnOnTalksFromTodayClosesAfterAgree() {
        waitFor { !state.loading }
        h.vm.startStandalone(OnboardingStep.DISCLOSURE)
        assertThat(state.step).isEqualTo(OnboardingStep.DISCLOSURE)
        var finished = false
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val job = scope.launch { h.vm.effects.collect { if (it == OnboardingEffect.Finished) finished = true } }
        h.vm.onAgree()
        waitFor { finished }
        job.cancel()
        assertThat(settings().talksEnabled).isTrue()
        assertThat(settings().onboardingDone).isFalse() // standalone doesn't touch onboarding
    }
}

