package com.vaak.data.net

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/** HTTP codes and the error envelope (LLD §3) → AppError (LLD §6). */
class ErrorMapperTest {
    private val mapper = ErrorMapper(Json { ignoreUnknownKeys = true }) { 1_000_000L }
    private fun env(code: String, retry: Long? = null) =
        """{"error":{"code":"$code","message":"m"${retry?.let { ",\"retry_after_s\":$it" } ?: ""}},"request_id":"r1"}"""

    @Test
    fun mapsEveryContractError() {
        assertThat(mapper.fromHttp(401, env("SESSION_EXPIRED"), Stage.STT)).isEqualTo(AppError.Auth)
        assertThat(mapper.fromHttp(403, env("INTEGRITY_FAILED"), Stage.STT)).isEqualTo(AppError.Integrity)
        assertThat(mapper.fromHttp(413, env("AUDIO_TOO_LONG"), Stage.STT)).isEqualTo(AppError.AudioTooLong)
        assertThat(mapper.fromHttp(422, env("TOO_LITTLE_SPEECH"), Stage.STT)).isEqualTo(AppError.TooLittleSpeech)
        assertThat(mapper.fromHttp(426, null, Stage.STT)).isEqualTo(AppError.UpdateRequired)
        assertThat(mapper.fromHttp(429, env("QUOTA_EXHAUSTED", 31_500), Stage.STT)).isEqualTo(AppError.Quota(1_000_000L + 31_500_000L))
        assertThat(mapper.fromHttp(429, env("RATE_LIMITED", 60), Stage.STT)).isEqualTo(AppError.Busy(60))
        assertThat(mapper.fromHttp(502, env("STT_FAILED"), Stage.STT)).isEqualTo(AppError.Upstream(Stage.STT, "STT_FAILED"))
        assertThat(mapper.fromHttp(502, env("LLM_FAILED"), Stage.LLM)).isEqualTo(AppError.Upstream(Stage.LLM, "LLM_FAILED"))
        assertThat(mapper.fromHttp(503, env("BUSY"), Stage.LLM)).isEqualTo(AppError.Busy(null))
        assertThat(mapper.fromHttp(504, null, Stage.STT)).isEqualTo(AppError.Upstream(Stage.STT, "TIMEOUT"))
        assertThat(mapper.fromHttp(409, env("SESSION_STATE"), Stage.LLM)).isEqualTo(AppError.Upstream(Stage.SERVER, "SESSION_STATE"))
        assertThat(mapper.fromHttp(500, "not json", Stage.STT)).isEqualTo(AppError.Offline)
    }

    @Test
    fun transportFailuresAreOffline() {
        assertThat(mapper.map(IOException("reset"), Stage.STT)).isEqualTo(AppError.Offline)
        assertThat(mapper.map(SocketTimeoutException(), Stage.LLM)).isEqualTo(AppError.Offline)
    }

    @Test
    fun terminalErrorsAreNotRetried() {
        assertThat(AppError.TooLittleSpeech.isTerminal).isTrue()
        assertThat(AppError.Offline.isTerminal).isFalse()
        assertThat(AppError.Upstream(Stage.LLM, null).errorCode).isEqualTo("LLM_FAILED")
    }
}
