package com.vaak.data.prefs

import com.vaak.domain.prompt.Track
import com.google.common.truth.Truth.assertThat
import com.vaak.data.TestStores
import com.vaak.domain.model.Lang
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {
    private val stores = TestStores()
    private val store = stores.settings

    @After
    fun tearDown() = stores.close()

    @Test
    fun defaultsArePrivacyFirst() = runBlocking<Unit> {
        val s = store.current()
        assertThat(s.analyticsConsent).isFalse() // D-026: off unless opted in
        assertThat(s.keepBaselineAudio).isFalse() // US-003: opt-in
        assertThat(s.disclosureConsentAt).isNull()
        assertThat(s.talksEnabled).isFalse()
        assertThat(s.onboardingDone).isFalse()
        assertThat(s.firstName).isNull()
        assertThat(s.reminder).isNull()
    }

    @Test
    fun blockedAgeGateStoresOnlyTheFlag() = runBlocking<Unit> {
        store.setAgeGate(AgeGate.BLOCKED)
        assertThat(store.current()).isEqualTo(Settings(ageGate = AgeGate.BLOCKED))
    }

    @Test
    fun profileIsTrimmedAndBlankNameIsCleared() = runBlocking<Unit> {
        store.setProfile("  Rohan ", setOf(Lang.EN, Lang.HI), setOf(GoalTrack.INTERVIEWS, GoalTrack.WORD_POWER))
        var s = store.current()
        assertThat(s.firstName).isEqualTo("Rohan")
        assertThat(s.langs).containsExactly(Lang.EN, Lang.HI)
        assertThat(s.tracks).containsExactly(GoalTrack.INTERVIEWS, GoalTrack.WORD_POWER)

        store.setProfile("   ", setOf(Lang.HI), setOf(GoalTrack.EVERYDAY))
        s = store.current()
        assertThat(s.firstName).isNull()
        assertThat(s.langs).containsExactly(Lang.HI)
    }

    @Test(expected = IllegalArgumentException::class)
    fun moreThanThreeGoalsIsRejected() = runBlocking<Unit> {
        store.setProfile(null, setOf(Lang.EN), GoalTrack.entries.take(4).toSet())
    }

    @Test
    fun disclosureConsentIsIndependentOfAnalytics() = runBlocking<Unit> {
        val at = Instant.parse("2026-11-02T03:00:00Z")
        store.recordDisclosure(at, analytics = false)
        assertThat(store.current().disclosureConsentAt).isEqualTo(at)
        assertThat(store.current().analyticsConsent).isFalse()
        assertThat(store.current().talksEnabled).isTrue()

        store.recordDisclosure(null, analytics = true) // "Not now" with the switch on
        assertThat(store.current().talksEnabled).isFalse()
        assertThat(store.current().analyticsConsent).isTrue()
    }

    @Test
    fun reminderRoundTripsAndCapsThePhrase() = runBlocking<Unit> {
        store.setReminder(ReminderSettings(LocalTime.of(18, 45), Routine.CUSTOM, "walking the dog after a long day at work"))
        val r = store.current().reminder!!
        assertThat(r.time).isEqualTo(LocalTime.of(18, 45))
        assertThat(r.routine).isEqualTo(Routine.CUSTOM)
        assertThat(r.customPhrase).hasLength(ReminderSettings.MAX_PHRASE)

        store.setReminder(null)
        assertThat(store.current().reminder).isNull()
    }

    @Test
    fun onboardingStepIsClearedOnCompletion() = runBlocking<Unit> {
        store.setOnboardingStep("REMINDER")
        assertThat(store.current().onboardingStep).isEqualTo("REMINDER")
        store.completeOnboarding(Instant.EPOCH)
        assertThat(store.current().onboardingDone).isTrue()
        assertThat(store.current().onboardingStep).isNull()
        assertThat(store.current().onboardedAt).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun sessionsResetEachDay() = runBlocking<Unit> {
        val mon = LocalDate.parse("2026-11-02")
        store.recordSession(mon, "review")
        store.recordSession(mon, "talk")
        assertThat(store.habitMarks.first().sessionKinds).containsExactly("review", "talk").inOrder()
        store.recordSession(mon.plusDays(1), "talk")
        val marks = store.habitMarks.first()
        assertThat(marks.sessionDay).isEqualTo(mon.plusDays(1))
        assertThat(marks.sessionKinds).containsExactly("talk")
    }

    @Test
    fun talkPrefsComeFromOnboardingAndShareTheSameFile() = runBlocking<Unit> {
        // Before onboarding: both languages and the S08 default tracks.
        assertThat(store.prefs.first()).isEqualTo(TalkPrefs())
        store.setProfile(null, setOf(Lang.HI), setOf(GoalTrack.INTERVIEWS, GoalTrack.WORD_POWER))
        var p = store.currentPrefs()
        assertThat(p.langs).containsExactly(Lang.HI)
        assertThat(p.lastLang).isEqualTo(Lang.HI) // never a language the user didn't pick
        assertThat(p.tracksFor(Lang.HI)).containsExactly(Track.INTERVIEWS) // word power has no talk prompts
        store.setTrack(Lang.HI, Track.INTERVIEWS)
        store.setStutterFriendly(true)
        store.setThinkTime(true)
        p = store.currentPrefs()
        assertThat(p.trackFor(Lang.HI)).isEqualTo(Track.INTERVIEWS)
        assertThat(p.thinkTime).isTrue()
        // One key per setting: S09's stutter switch and Settings' are the same value.
        assertThat(store.current().stutterFriendly).isTrue()
    }

    @Test
    fun onlyNonTalkGoalsFallBackToTheDefaultTracks() = runBlocking<Unit> {
        store.setProfile(null, setOf(Lang.EN), setOf(GoalTrack.CONTENT_CREATION))
        assertThat(store.currentPrefs().tracksFor(Lang.EN)).isEqualTo(TalkPrefs.DEFAULT_TRACKS[Lang.EN])
    }
}
