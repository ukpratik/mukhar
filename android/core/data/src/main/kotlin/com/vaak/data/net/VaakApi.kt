package com.vaak.data.net

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
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

    /** Multipart: `meta` JSON + `audio` (≤ 15 s, `audio/aac`). */
    @Multipart
    @POST("check-sentence")
    suspend fun checkSentence(@Part("meta") meta: CheckSentenceMetaDto, @Part audio: MultipartBody.Part): CheckSentenceResponseDto

    @POST("report")
    suspend fun report(@Body body: ReportRequestDto): ReportResponseDto

    /** US-061: verify a Play purchase token (subscriptionsv2) and bind it to this user (restore rebinds). */
    @POST("entitlement")
    suspend fun entitlement(@Body body: EntitlementRequestDto): EntitlementResponseDto

    /** The server's view of the plan and today's quota (the client mirror defers to it). */
    @GET("me")
    suspend fun me(): MeResponseDto

    /** US-063: idempotent; deletes quota, entitlement cache and reports for this uid. */
    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteMe(@Body body: DeleteMeRequestDto): DeleteMeResponseDto
}
