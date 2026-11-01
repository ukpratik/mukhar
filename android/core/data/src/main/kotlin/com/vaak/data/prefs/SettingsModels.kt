package com.vaak.data.prefs

import com.vaak.domain.model.Lang
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** US-001: only the band is kept, never the year (S01). */
enum class AgeGate(val key: String) {
    PASSED("passed"),
    BLOCKED("blocked");

    companion object {
        fun fromKey(key: String?): AgeGate? = entries.firstOrNull { it.key == key }
    }
}

/** The 6 goal tracks (US-002). Keys match `talk_session.track`. */
enum class GoalTrack(val key: String) {
    PROFESSIONAL("professional"),
    INTERVIEWS("interviews"),
    PUBLIC_SPEAKING("public_speaking"),
    CONTENT_CREATION("content_creation"),
    WORD_POWER("word_power"),
    EVERYDAY("everyday");

    companion object {
        const val MAX_PICKED = 3
        fun fromKey(key: String): GoalTrack? = entries.firstOrNull { it.key == key }
    }
}

/** The routine a reminder is tied to (US-004, S06). Each suggests a time the user can change. */
enum class Routine(val key: String, val suggestedTime: LocalTime) {
    MORNING_TEA("morning_tea", LocalTime.of(8, 15)),
    COMMUTE("commute", LocalTime.of(9, 0)),
    LUNCH("lunch", LocalTime.of(13, 30)),
    WORKOUT("workout", LocalTime.of(7, 0)),
    DINNER("dinner", LocalTime.of(21, 0)),
    CUSTOM("custom", LocalTime.of(18, 45));

    companion object {
        fun fromKey(key: String?): Routine? = entries.firstOrNull { it.key == key }
    }
}

/** "After [routine] at [time]". [customPhrase] is used as-is for [Routine.CUSTOM] (≤ 30 characters). */
data class ReminderSettings(val time: LocalTime, val routine: Routine, val customPhrase: String? = null) {
    companion object {
        const val MAX_PHRASE = 30
    }
}

/** Everything in the `settings` DataStore (LLD §2.1). On the device only; backed up with Auto Backup (NFR-014). */
data class Settings(
    val firstName: String? = null,
    val langs: Set<Lang> = emptySet(),
    val tracks: Set<GoalTrack> = emptySet(),
    val dailyNewWords: Int = 3,
    val reminder: ReminderSettings? = null,
    val stutterFriendly: Boolean = false,
    val keepBaselineAudio: Boolean = false,
    val transliteration: Boolean = false,
    val ageGate: AgeGate? = null,
    val disclosureConsentAt: Instant? = null,
    val analyticsConsent: Boolean = false,
    val onboardingDone: Boolean = false,
    val onboardingStep: String? = null,
    val onboardedAt: Instant? = null,
    val micSkipped: Boolean = false,
    val baselineRecorded: Boolean = false,
    val reminderBackoffLevel: Int = 0,
    val oemGuideShown: Boolean = false,
    val notificationTipDismissed: Boolean = false,
) {
    /** Talks need the S04 consent; without it only offline features run (S07 state 5). */
    val talksEnabled: Boolean get() = disclosureConsentAt != null
}

/** Small habit bookkeeping that has no Room column (sessions today for repair, one-time notices). */
data class HabitMarks(
    val sessionDay: LocalDate? = null,
    val sessionKinds: List<String> = emptyList(),
    val freezeNoticeDay: LocalDate? = null,
    val freshStartAckDay: LocalDate? = null,
)
