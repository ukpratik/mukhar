package io.github.ukpratik.mukhar.data.prefs

import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.words.DailyWordPicker
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
    /** New words a day per practice language, 5 to 50 (US-076, D-045). Missing languages use the default. */
    val dailyNewWords: Map<Lang, Int> = Lang.entries.associateWith { DailyWordPicker.DEFAULT_COUNT },
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
    /** ADR-008: how talks are analysed. Cloud is "coming soon" in R1 and runs as on-device if ever stored. */
    val analysisMode: AnalysisMode = AnalysisMode.DEFAULT,
    /** "Ask before going over ₹[softCapInr]" a month on the user's own keys (off by default). */
    val softCapOn: Boolean = false,
    val softCapInr: Int = DEFAULT_SOFT_CAP_INR,
) {
    /** Talks need the S04 consent; without it only offline features run (S07 state 5). */
    val talksEnabled: Boolean get() = disclosureConsentAt != null

    fun dailyWordsFor(lang: Lang): Int = dailyNewWords[lang] ?: DailyWordPicker.DEFAULT_COUNT

    /** The practice languages in display order (English first), English when none is set yet. */
    val practiceLangs: List<Lang> get() = Lang.entries.filter { it in langs }.ifEmpty { listOf(Lang.EN) }
}

const val DEFAULT_SOFT_CAP_INR = 100

/**
 * This month's use of the user's own keys (ADR-008 "Cost transparency"): local estimates only, never sent anywhere.
 * [month] is `yyyy-MM`; a new month starts from zero.
 */
data class ByokUsage(
    val month: String = "",
    val talks: Int = 0,
    val deepgramMs: Long = 0,
    val sarvamMs: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
)

/** Small habit bookkeeping that has no Room column (sessions today for repair, one-time notices). */
data class HabitMarks(
    val sessionDay: LocalDate? = null,
    val sessionKinds: List<String> = emptyList(),
    val freezeNoticeDay: LocalDate? = null,
    val freshStartAckDay: LocalDate? = null,
)

/** The server's count of talks on a quota day (03:00 IST), mirrored for S17. */
data class ServerTalkQuota(val day: LocalDate, val used: Int, val limit: Int)
