package com.vaak.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Worker API v1 wire format (LLD §3). JSON is snake_case; every DTO mirrors the contract table exactly.

@Serializable
data class TranscribeMetaDto(
    @SerialName("client_session_id") val clientSessionId: String,
    val kind: String,
    @SerialName("parent_session_id") val parentSessionId: String?,
    val lang: String,
    @SerialName("duration_ms") val durationMs: Long,
    val queued: Boolean,
    val keyterms: List<String> = emptyList(),
)

/** One STT word: text, start and end in ms, confidence 0–1. Also the `transcript.tokens_json` format (LLD §2.1). */
@Serializable
data class WordDto(val w: String, val s: Long, val e: Long, val c: Double)

@Serializable
data class QuotaDto(
    val day: String? = null,
    @SerialName("talks_used") val talksUsed: Int? = null,
    @SerialName("talks_limit") val talksLimit: Int? = null,
    @SerialName("checks_used") val checksUsed: Int? = null,
    @SerialName("checks_limit") val checksLimit: Int? = null,
    @SerialName("resets_at") val resetsAt: String? = null,
)

@Serializable
data class TranscribeResponseDto(
    @SerialName("session_id") val sessionId: String,
    val provider: String,
    val model: String,
    @SerialName("duration_ms") val durationMs: Long,
    val words: List<WordDto>,
    val quota: QuotaDto? = null,
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class FeedbackTokenDto(val i: Int, val w: String, val lc: Boolean)

@Serializable
data class FeedbackMetricsDto(
    val wpm: Double,
    @SerialName("wpm_band") val wpmBand: String,
    @SerialName("fillers_per_min") val fillersPerMin: Double,
    @SerialName("filler_count") val fillerCount: Int,
    @SerialName("long_pauses_per_min") val longPausesPerMin: Double?,
    val repetitions: Int?,
    val mattr: Double?,
    @SerialName("speech_s") val speechS: Long,
)

@Serializable
data class FeedbackRequestDto(
    @SerialName("session_id") val sessionId: String,
    val lang: String,
    val track: String,
    val prompt: String,
    @SerialName("stutter_friendly") val stutterFriendly: Boolean,
    val tokens: List<FeedbackTokenDto>,
    val metrics: FeedbackMetricsDto,
)

@Serializable
data class GlowDto(val start: Int, val end: Int, val quote: String, val note: String)

@Serializable
data class GrowDto(val start: Int, val end: Int, val quote: String, val issue: String, val why: String, val how: String)

@Serializable
data class UpgradedWordDto(val word: String, val meaning: String, val register: String)

@Serializable
data class BetterVersionDto(
    val start: Int,
    val end: Int,
    val quote: String,
    val rewrite: String,
    @SerialName("upgraded_words") val upgradedWords: List<UpgradedWordDto>,
)

@Serializable
data class MistakeDto(val start: Int, val end: Int, val quote: String, val fix: String)

/** The validated feedback payload (LLD §4.3 schema). `glow`/`grow` are null when the server used a fallback. */
@Serializable
data class FeedbackDto(
    val summary: String,
    val glow: GlowDto? = null,
    val grow: GrowDto? = null,
    @SerialName("better_versions") val betterVersions: List<BetterVersionDto> = emptyList(),
    val mistakes: List<MistakeDto> = emptyList(),
    val safety: String = "none",
)

@Serializable
data class FallbackFlagsDto(val glow: Boolean = false, val grow: Boolean = false)

@Serializable
data class FeedbackResponseDto(
    @SerialName("prompt_version") val promptVersion: String,
    val model: String,
    val feedback: FeedbackDto,
    val dropped: Int = 0,
    val fallback: FallbackFlagsDto = FallbackFlagsDto(),
    val safety: String = "none",
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class ReportPayloadDto(
    val tokens: List<FeedbackTokenDto>,
    val feedback: FeedbackDto?,
    val note: String? = null,
)

@Serializable
data class ReportRequestDto(
    @SerialName("session_id") val sessionId: String,
    val reason: String,
    val consent: Boolean,
    val lang: String,
    @SerialName("prompt_version") val promptVersion: String?,
    /** Only with the user's explicit consent (US-016); otherwise null. */
    val payload: ReportPayloadDto?,
)

@Serializable
data class ReportResponseDto(@SerialName("report_id") val reportId: String)

@Serializable
data class ErrorBodyDto(val code: String, val message: String? = null, @SerialName("retry_after_s") val retryAfterS: Long? = null)

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto, @SerialName("request_id") val requestId: String? = null)
