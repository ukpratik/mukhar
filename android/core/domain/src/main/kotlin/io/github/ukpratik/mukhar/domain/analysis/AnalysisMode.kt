package io.github.ukpratik.mukhar.domain.analysis

/**
 * How a talk is analysed (ADR-008, D-037). [ON_DEVICE] is the free default; [OWN_KEYS] calls the user's own
 * Deepgram/Sarvam and Anthropic keys straight from the phone; [CLOUD] is the Mukhar Worker, kept in the code but
 * switched off for R1 ([CLOUD_ENABLED]), so it's shown as "coming soon" and treated as [ON_DEVICE] if ever stored.
 */
enum class AnalysisMode(val key: String) {
    ON_DEVICE("on_device"),
    OWN_KEYS("own_keys"),
    CLOUD("cloud");

    /** The mode that actually runs: [CLOUD] falls back to [ON_DEVICE] while the Worker is off. */
    val effective: AnalysisMode get() = if (this == CLOUD && !CLOUD_ENABLED) ON_DEVICE else this

    /** Server quota (TalkQuota, QuotaMirror) applies only to the Worker; local and own-key modes have no limits (D-039). */
    val usesServerQuota: Boolean get() = effective == CLOUD

    companion object {
        /** R1: no server, no API spend (D-037). Flip to re-enable Mukhar Cloud and its quota. */
        const val CLOUD_ENABLED = false

        val DEFAULT = ON_DEVICE

        fun fromKey(key: String?): AnalysisMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Notes the results screen shows about how a talk was analysed (stored with the coach's notes, as keys). */
enum class AnalysisNote(val key: String) {
    /** The speech-to-text key was refused (401/403); the talk used the offline transcript instead. */
    STT_KEY_REJECTED("stt_key_rejected"),

    /** The speech-to-text call failed (network, 429, server); the offline transcript was used. */
    STT_FALLBACK("stt_fallback"),

    /** The AI coaching key was refused; the offline coach wrote the notes. */
    LLM_KEY_REJECTED("llm_key_rejected"),

    /** The AI coaching call failed (network, 429, refusal, bad output); the offline coach wrote the notes. */
    LLM_FALLBACK("llm_fallback"),

    /** The AI notes didn't match the transcript (grounding), so the offline coach wrote them. */
    LLM_UNGROUNDED("llm_ungrounded"),

    /** The AI notes are based on an offline (less accurate) transcript. */
    OFFLINE_TRANSCRIPT("offline_transcript"),

    /** The soft monthly spending cap was reached, so this talk was analysed on the phone. */
    SOFT_CAP_REACHED("soft_cap_reached"),

    /** No transcript could be written, so pace (estimated), pauses and the notes come from the voice alone (D-048). */
    AUDIO_ONLY("audio_only");

    companion object {
        fun fromKey(key: String): AnalysisNote? = entries.firstOrNull { it.key == key }
    }
}
