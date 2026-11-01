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
import com.vaak.domain.prompt.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Talk-related user settings read by the talk flow (S08, S09, analysis). Languages and tracks come from onboarding. */
data class TalkPrefs(
    val langs: List<Lang> = listOf(Lang.EN, Lang.HI),
    val tracks: Map<Lang, List<Track>> = DEFAULT_TRACKS,
    val lastLang: Lang = Lang.EN,
    val lastTrack: Map<Lang, Track> = emptyMap(),
    val stutterFriendly: Boolean = false,
    val transliteration: Boolean = false,
    val structureHint: Boolean = true,
    val thinkTime: Boolean = false,
) {
    fun tracksFor(lang: Lang): List<Track> = tracks[lang].orEmpty().ifEmpty { listOf(Track.EVERYDAY) }
    fun trackFor(lang: Lang): Track = lastTrack[lang]?.takeIf { it in tracksFor(lang) } ?: tracksFor(lang).first()

    companion object {
        /** The tracks drawn in S08 when onboarding picked no talk track (or before it ran). */
        val DEFAULT_TRACKS = mapOf(
            Lang.EN to listOf(Track.PROFESSIONAL, Track.INTERVIEWS, Track.EVERYDAY),
            Lang.HI to listOf(Track.PROFESSIONAL, Track.EVERYDAY, Track.PUBLIC_SPEAKING),
        )
    }
}

/** The talk flow's view of [SettingsStore] (a seam so talk tests can use an in-memory fake). */
interface UserSettings {
    val prefs: Flow<TalkPrefs>
    suspend fun currentPrefs(): TalkPrefs = prefs.first()
    suspend fun setLang(lang: Lang)
    suspend fun setTrack(lang: Lang, track: Track)
    suspend fun setStutterFriendly(on: Boolean)
    suspend fun setTransliteration(on: Boolean)
    suspend fun setStructureHint(on: Boolean)
    suspend fun setThinkTime(on: Boolean)
}

/**
 * The one `settings` DataStore (LLD §2.1), file `datastore/settings.preferences_pb` (included in Auto Backup).
 * Onboarding, Today, reminders and habit marks use [settings]; the talk flow uses [prefs] ([UserSettings]).
 * Nothing here is sent to a server; the first name is used only for the Today greeting (D-021).
 */
@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) : UserSettings {

    val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    val habitMarks: Flow<HabitMarks> = store.data.map { it.toMarks() }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    /** Onboarding's languages and goals mapped to the talk flow, plus the talk-only switches (S08/S09). */
    override val prefs: Flow<TalkPrefs> = store.data.map { it.toTalkPrefs() }.distinctUntilChanged()

    override suspend fun setLang(lang: Lang) = update { it[K.talkLang] = lang.code }
    override suspend fun setTrack(lang: Lang, track: Track) = update { it[K.talkTrack(lang)] = track.key }
    override suspend fun setStutterFriendly(on: Boolean) = update { it[K.stutterFriendly] = on }
    override suspend fun setTransliteration(on: Boolean) = update { it[K.transliteration] = on }
    override suspend fun setStructureHint(on: Boolean) = update { it[K.structureHint] = on }
    override suspend fun setThinkTime(on: Boolean) = update { it[K.thinkTime] = on }

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

    private fun Preferences.toTalkPrefs(): TalkPrefs {
        val picked = this[K.langs].orEmpty()
        val langs = Lang.entries.filter { it.code in picked }.ifEmpty { TalkPrefs().langs }
        // Goal tracks without talk prompts (content creation, word power) don't add a talk track.
        val talkTracks = GoalTrack.entries.filter { it.key in this[K.tracks].orEmpty() }
            .mapNotNull { g -> Track.entries.firstOrNull { it.key == g.key } }
        val lastLang = this[K.talkLang]?.let { code -> langs.firstOrNull { it.code == code } } ?: langs.first()
        return TalkPrefs(
            langs = langs,
            tracks = langs.associateWith { talkTracks.ifEmpty { TalkPrefs.DEFAULT_TRACKS[it].orEmpty() } },
            lastLang = lastLang,
            lastTrack = Lang.entries.mapNotNull { l -> this[K.talkTrack(l)]?.let { l to Track.fromKey(it) } }.toMap(),
            stutterFriendly = this[K.stutterFriendly] ?: false,
            transliteration = this[K.transliteration] ?: false,
            structureHint = this[K.structureHint] ?: true,
            thinkTime = this[K.thinkTime] ?: false,
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

    /** Key names follow LLD §2.1; the talk-only keys (last language/track, S09 switches) were added in M2. */
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
        val talkLang = stringPreferencesKey("talk_lang")
        val structureHint = booleanPreferencesKey("structure_hint")
        val thinkTime = booleanPreferencesKey("think_time")
        fun talkTrack(lang: Lang) = stringPreferencesKey("talk_track_${lang.code}")
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
