package com.vaak.data.net

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Maps HTTP status + the error envelope (LLD §3) and transport failures to [AppError] (LLD §6). */
class ErrorMapper(private val json: Json, private val nowMs: () -> Long = System::currentTimeMillis) {

    fun map(t: Throwable, stage: Stage): AppError = when (t) {
        is HttpException -> fromHttp(t.code(), t.response()?.errorBody()?.string(), stage)
        is SerializationException -> AppError.Unknown("bad_response")
        is IOException -> AppError.Offline
        else -> AppError.Unknown(t::class.simpleName ?: "error")
    }

    fun fromHttp(status: Int, body: String?, stage: Stage): AppError {
        val err = body?.let { runCatching { json.decodeFromString<ErrorEnvelopeDto>(it).error }.getOrNull() }
        val code = err?.code
        return when (status) {
            401 -> AppError.Auth
            403 -> AppError.Integrity
            404 -> AppError.Upstream(Stage.SERVER, code ?: "SESSION_STATE")
            // D-031: a second baseline is refused; terminal, the app explains it instead of retrying.
            409 -> if (code == "BASELINE_USED") AppError.BaselineUsed else AppError.Upstream(Stage.SERVER, code ?: "SESSION_STATE")
            413 -> AppError.AudioTooLong
            422 -> AppError.TooLittleSpeech
            426 -> AppError.UpdateRequired
            429 -> if (code == "RATE_LIMITED") {
                AppError.Busy(err.retryAfterS)
            } else {
                AppError.Quota(err?.retryAfterS?.let { nowMs() + it * 1000 })
            }
            502 -> when (code) {
                "LLM_FAILED" -> AppError.Upstream(Stage.LLM, code)
                "STT_FAILED" -> AppError.Upstream(Stage.STT, code)
                else -> AppError.Upstream(stage, code)
            }
            503 -> AppError.Busy(err?.retryAfterS)
            504 -> AppError.Upstream(stage, code ?: "TIMEOUT")
            in 500..599 -> AppError.Offline // Worker or D1 outage: same as a network failure (HLD §8.1)
            else -> AppError.Unknown("http_$status")
        }
    }
}
