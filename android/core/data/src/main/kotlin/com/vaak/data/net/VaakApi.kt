package com.vaak.data.net

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Worker API v1 (LLD §3), client side. Base URL from `BuildConfig.API_BASE_URL`; auth headers are added by
 * [HeadersInterceptor]. Untested against a real server: the Worker isn't built yet (build-plan P1/P2).
 */
interface VaakApi {
    /** Multipart: `meta` JSON + `audio` (`audio/aac`, ADTS). */
    @Multipart
    @POST("transcribe")
    suspend fun transcribe(@Part("meta") meta: TranscribeMetaDto, @Part audio: MultipartBody.Part): TranscribeResponseDto

    @POST("feedback")
    suspend fun feedback(@Body body: FeedbackRequestDto): FeedbackResponseDto

    @POST("report")
    suspend fun report(@Body body: ReportRequestDto): ReportResponseDto
}
