package io.github.ukpratik.mukhar.data.net

/** Data-layer result (LLD §6): repositories and the pipeline never throw across layers. */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(f: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(f(value))
    is Outcome.Err -> this
}

/** Where a server-side failure happened (S10 states 4 and 5). */
enum class Stage { STT, LLM, SERVER }

/** Every failure the UI knows how to explain (LLD §6). The UI maps these to the design's state copy. */
sealed interface AppError {
    /** No network, a timeout, or a Worker/D1 outage (HLD §8.1: treated like offline, the talk is queued). */
    data object Offline : AppError
    data object Auth : AppError
    data object Integrity : AppError

    /** Daily quota used; [resetsAtMs] is when the next quota day starts, if the server said. */
    data class Quota(val resetsAtMs: Long?) : AppError

    /** Spend cap, kill switch or rate limit: stays queued and retries later. */
    data class Busy(val retryAfterS: Long?) : AppError
    data object TooLittleSpeech : AppError
    data object AudioTooLong : AppError
    data class Upstream(val stage: Stage, val code: String?) : AppError

    /** 409 BASELINE_USED: this account or device already had its free baseline analysed (D-031). */
    data object BaselineUsed : AppError
    data object UpdateRequired : AppError
    data object Storage : AppError
    data class Unknown(val cause: String) : AppError
}

/** Terminal errors are never retried automatically (LLD §6). */
val AppError.isTerminal: Boolean
    get() = this is AppError.TooLittleSpeech || this is AppError.AudioTooLong || this is AppError.UpdateRequired ||
        this is AppError.Storage || this is AppError.BaselineUsed

/** A short, content-free code stored in `talk_session.error_code` and safe to log. */
val AppError.errorCode: String
    get() = when (this) {
        AppError.Offline -> "OFFLINE"
        AppError.Auth -> "AUTH"
        AppError.Integrity -> "INTEGRITY"
        is AppError.Quota -> "QUOTA_EXHAUSTED"
        is AppError.Busy -> "BUSY"
        AppError.TooLittleSpeech -> "TOO_LITTLE_SPEECH"
        AppError.AudioTooLong -> "AUDIO_TOO_LONG"
        is AppError.Upstream -> when (stage) {
            Stage.STT -> "STT_FAILED"
            Stage.LLM -> "LLM_FAILED"
            Stage.SERVER -> code ?: "SERVER"
        }
        AppError.UpdateRequired -> "UPDATE_REQUIRED"
        AppError.Storage -> "STORAGE"
        AppError.BaselineUsed -> "BASELINE_USED"
        is AppError.Unknown -> "UNKNOWN"
    }
