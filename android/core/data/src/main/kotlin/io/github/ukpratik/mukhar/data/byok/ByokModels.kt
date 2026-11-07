package io.github.ukpratik.mukhar.data.byok

import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Stage

/** What a key is for: speech-to-text or the AI coach (ADR-008 "Your own keys"). STT and LLM are independent. */
enum class KeyKind { STT, LLM }

/** The vendors a user can bring a key for. [id] is the associated data that binds a ciphertext to its provider. */
enum class ByokProvider(val id: String, val kind: KeyKind) {
    DEEPGRAM("deepgram", KeyKind.STT),
    SARVAM("sarvam", KeyKind.STT),
    ANTHROPIC("anthropic", KeyKind.LLM);

    companion object {
        fun fromId(id: String): ByokProvider? = entries.firstOrNull { it.id == id }
    }
}

/** The answer of a key check (S19 "Your keys" states). */
enum class KeyCheck(val key: String) {
    /** "Key works". */
    OK("ok"),

    /** 403 from Deepgram's project list: the key works for transcription but can't list projects. Treated as valid. */
    LIMITED("limited"),

    /** "Key not accepted (401). Check you copied all of it." */
    INVALID("invalid"),

    /** "This key can't use claude-haiku-5-5" (404 on the model). */
    MODEL_UNAVAILABLE("model_unavailable"),

    /** "No internet. We'll check next time." */
    OFFLINE("offline"),

    /** Anything else (5xx, a surprising answer): try again later. */
    ERROR("error");

    val usable: Boolean get() = this == OK || this == LIMITED

    companion object {
        fun fromKey(key: String?): KeyCheck? = entries.firstOrNull { it.key == key }
    }
}

/** Why a direct vendor call failed. Never carries the key, the audio or the transcript. */
sealed interface ByokError {
    data object Offline : ByokError
    data object Unauthorized : ByokError
    data object Forbidden : ByokError
    data class RateLimited(val retryAfterS: Long?) : ByokError
    data object Refused : ByokError
    data object Truncated : ByokError
    data object ModelUnavailable : ByokError
    data class BadResponse(val reason: String) : ByokError
    data class Server(val status: Int) : ByokError

    val keyRejected: Boolean get() = this == Unauthorized || this == Forbidden
}

sealed interface ByokResult<out T> {
    data class Ok<T>(val value: T) : ByokResult<T>
    data class Fail(val error: ByokError) : ByokResult<Nothing>
}

/** How a failed vendor call is explained when there's no offline fallback (the talk stays saved). */
fun ByokError.toAppError(stage: Stage): AppError = when (this) {
    ByokError.Offline -> AppError.Offline
    is ByokError.RateLimited -> AppError.Busy(retryAfterS)
    is ByokError.Server -> AppError.Busy(null)
    // A refused key can't be retried on a timer: the talk waits until a key or an offline model is added.
    ByokError.Unauthorized, ByokError.Forbidden, ByokError.ModelUnavailable -> AppError.EngineUnavailable
    ByokError.Refused, ByokError.Truncated, is ByokError.BadResponse -> AppError.Upstream(stage, "BYOK_FAILED")
}
