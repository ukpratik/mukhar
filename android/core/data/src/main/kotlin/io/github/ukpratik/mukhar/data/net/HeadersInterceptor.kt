package io.github.ukpratik.mukhar.data.net

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/** Supplies the auth headers (LLD §3). Firebase anonymous auth and the session token (US-064) are not built yet. */
interface CredentialsProvider {
    /** Firebase ID token, or null when signed out. */
    suspend fun idToken(): String?

    /** The daily session token from `POST /session`, or null. */
    suspend fun sessionToken(): String?
}

/** Placeholder until Firebase + Play Integrity land with the Worker: no credentials. */
object NoCredentials : CredentialsProvider {
    override suspend fun idToken(): String? = null
    override suspend fun sessionToken(): String? = null
}

/** The app's version code for `X-App-Version` (the data module has no app BuildConfig). */
fun interface AppVersion {
    fun code(): Int
}

/** Adds `Authorization`, `X-Mukhar-Session`, `X-App-Version` and `X-Request-Id` to every call. */
class HeadersInterceptor(
    private val version: AppVersion,
    private val tokens: () -> Pair<String?, String?>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val (id, session) = tokens()
        val request = chain.request().newBuilder()
            .header("X-App-Version", version.code().toString())
            .header("X-Request-Id", UUID.randomUUID().toString())
            .apply {
                id?.let { header("Authorization", "Bearer $it") }
                session?.let { header("X-Mukhar-Session", it) }
            }
            .build()
        return chain.proceed(request)
    }
}
