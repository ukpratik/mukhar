package com.vaak.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vaak.domain.model.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `settings` DataStore (LLD §2.1), file `datastore/settings.preferences_pb` (included in Auto Backup).
 * Nothing here is sent to a server; the first name is used only for the Today greeting (D-021).
 */
@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    val habitMarks: Flow<HabitMarks> = store.data.map { it.toMarks() }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun setAgeGate(gate: AgeGate) = update { it[K.ageGate] = gate.key }

    /** Name is optional; blank clears it. Trimmed and capped so the greeting stays on two lines. */
    suspend fun setProfile(firstName: String?, langs: Set<Lang>, tracks: Set<GoalTrack>) {
        require(langs.isNotEmpty()) { "Pick at least one language" }
        require(tracks.size in 1..GoalTrack.MAX_PICKED) { "Pick 1 to 3 goals" }
        update {
            it.putOrRemove(K.firstName, firstName?.trim()?.take(MAX_NAME)?.takeIf(String::isNotEmpty))
            it[K.langs] = langs.map(Lang::code).toSet()
            it[K.tracks] = tracks.map(GoalTrack::key).toSet()
        }
    }

    suspend fun setMicSkipped(skipped: Boolean) = update { it[K.micSkipped] = skipped }

    /** S04: consent is an explicit Agree. [analytics] is independent of it and off unless switched on (D-026, D-028). */
    suspend fun recordDisclosure(consentAt: Instant?, analytics: Boolean) = update {
        it.putOrRemove(K.disclosureConsentAt, consentAt?.toEpochMilli())
        it[K.analyticsConsent] = analytics
    }

    suspend fun setBaseline(recorded: Boolean, keepAudio: Boolean) = update {
        it[K.baselineRecorded] = recorded
        it[K.keepBaselineAudio] = keepAudio
    }

    suspend fun setReminder(reminder: ReminderSettings?) = update {
        if (reminder == null) {
            it.remove(K.reminderTime)
            it.remove(K.reminderRoutine)
            it.remove(K.reminderPhrase)
        } else {
            it[K.reminderTime] = reminder.time.toSecondOfDay() / 60
            it[K.reminderRoutine] = reminder.routine.key
            it.putOrRemove(
                K.reminderPhrase,
                reminder.customPhrase?.trim()?.take(ReminderSettings.MAX_PHRASE)?.takeIf(String::isNotEmpty),
            )
        }
    }

    suspend fun setReminderBackoffLevel(level: Int) = update { it[K.backoff] = level }

    suspend fun setOnboardingStep(step: String?) = update { it.putOrRemove(K.onboardingStep, step) }

    suspend fun completeOnboarding(at: Instant) = update {
        it[K.onboardingDone] = true
        it[K.onboardedAt] = at.toEpochMilli()
        it.remove(K.onboardingStep)
    }

    suspend fun markOemGuideShown() = update { it[K.oemGuideShown] = true }

    suspend fun dismissNotificationTip() = update { it[K.notifTipDismissed] = true }

    // Habit marks (HabitRepository only).

    suspend fun recordSession(day: LocalDate, kind: String) = update {
        val sameDay = it[K.sessionDay] == day.toString()
        val kinds = if (sameDay) it[K.sessionKinds].orEmpty() else ""
        it[K.sessionDay] = day.toString()
        it[K.sessionKinds] = if (kinds.isEmpty()) kind else "$kinds,$kind"
    }

    suspend fun setFreezeNotice(day: LocalDate?) = update { it.putOrRemove(K.freezeNotice, day?.toString()) }

    suspend fun setFreshStartAck(day: LocalDate) = update { it[K.freshStartAck] = day.toString() }

    private fun Preferences.toSettings(): Settings {
        val minutes = this[K.reminderTime]
        val routine = Routine.fromKey(this[K.reminderRoutine])
        return Settings(
            firstName = this[K.firstName],
            langs = this[K.langs].orEmpty().mapNotNull { code -> Lang.entries.firstOrNull { it.code == code } }.toSet(),
            tracks = this[K.tracks].orEmpty().mapNotNull(GoalTrack::fromKey).toSet(),
            dailyNewWords = this[K.dailyNewWords] ?: 3,
            reminder = if (minutes != null && routine != null) {
                ReminderSettings(LocalTime.ofSecondOfDay(minutes * 60L), routine, this[K.reminderPhrase])
            } else {
                null
            },
            stutterFriendly = this[K.stutterFriendly] ?: false,
            keepBaselineAudio = this[K.keepBaselineAudio] ?: false,
            transliteration = this[K.transliteration] ?: false,
            ageGate = AgeGate.fromKey(this[K.ageGate]),
            disclosureConsentAt = this[K.disclosureConsentAt]?.let(Instant::ofEpochMilli),
            analyticsConsent = this[K.analyticsConsent] ?: false,
            onboardingDone = this[K.onboardingDone] ?: false,
            onboardingStep = this[K.onboardingStep],
            onboardedAt = this[K.onboardedAt]?.let(Instant::ofEpochMilli),
            micSkipped = this[K.micSkipped] ?: false,
            baselineRecorded = this[K.baselineRecorded] ?: false,
            reminderBackoffLevel = this[K.backoff] ?: 0,
            oemGuideShown = this[K.oemGuideShown] ?: false,
            notificationTipDismissed = this[K.notifTipDismissed] ?: false,
        )
    }

    private fun Preferences.toMarks() = HabitMarks(
        sessionDay = this[K.sessionDay]?.let(LocalDate::parse),
        sessionKinds = this[K.sessionKinds]?.split(',')?.filter(String::isNotEmpty).orEmpty(),
        freezeNoticeDay = this[K.freezeNotice]?.let(LocalDate::parse),
        freshStartAckDay = this[K.freshStartAck]?.let(LocalDate::parse),
    )

    /** Edits without leaking DataStore types to callers (only :core:data depends on DataStore). */
    private suspend fun update(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    /** Key names follow LLD §2.1. */
    private object K {
        val firstName = stringPreferencesKey("first_name")
        val langs = stringSetPreferencesKey("langs")
        val tracks = stringSetPreferencesKey("tracks")
        val dailyNewWords = intPreferencesKey("daily_new_words")
        val reminderTime = intPreferencesKey("reminder_time_min")
        val reminderRoutine = stringPreferencesKey("reminder_routine")
        val reminderPhrase = stringPreferencesKey("reminder_phrase")
        val stutterFriendly = booleanPreferencesKey("stutter_friendly")
        val keepBaselineAudio = booleanPreferencesKey("keep_baseline_audio")
        val transliteration = booleanPreferencesKey("transliteration")
        val ageGate = stringPreferencesKey("age_gate")
        val disclosureConsentAt = longPreferencesKey("disclosure_consent_at")
        val analyticsConsent = booleanPreferencesKey("analytics_consent")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val onboardingStep = stringPreferencesKey("onboarding_step")
        val onboardedAt = longPreferencesKey("onboarded_at")
        val micSkipped = booleanPreferencesKey("mic_skipped")
        val baselineRecorded = booleanPreferencesKey("baseline_recorded")
        val backoff = intPreferencesKey("reminder_backoff_level")
        val oemGuideShown = booleanPreferencesKey("oem_guide_shown")
        val notifTipDismissed = booleanPreferencesKey("notification_tip_dismissed")
        val sessionDay = stringPreferencesKey("habit_session_day")
        val sessionKinds = stringPreferencesKey("habit_session_kinds")
        val freezeNotice = stringPreferencesKey("habit_freeze_notice_day")
        val freshStartAck = stringPreferencesKey("habit_fresh_start_ack_day")
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
