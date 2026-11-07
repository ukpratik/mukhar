package io.github.ukpratik.mukhar.data.byok

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Direct-from-phone HTTP for the user's own keys (ADR-008). Keys are held in memory only for the call and never
 * logged: [SafeLoggingInterceptor] logs the method, host, path, status and time, and any header it prints goes
 * through [Redaction] first. There is no OkHttp body/header logger anywhere in the app.
 */
object ByokHttp {
    fun client(logger: (String) -> Unit, logHeaders: Boolean = false): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // a 5-minute talk can take a while on a slow uplink
        .callTimeout(150, TimeUnit.SECONDS)
        .addInterceptor(SafeLoggingInterceptor(logger, logHeaders))
        .build()
}

/** Header names whose values are secrets. Matched case-insensitively. */
object Redaction {
    val SENSITIVE: Set<String> = setOf("authorization", "x-api-key", "api-subscription-key", "proxy-authorization", "cookie", "set-cookie")
    const val MASK = "██"

    fun headers(headers: Headers): String = headers.joinToString(", ") { (name, value) ->
        "$name: ${if (name.lowercase() in SENSITIVE) MASK else value}"
    }
}

/** Logs codes and timings only; see [ByokHttp]. */
class SafeLoggingInterceptor(private val log: (String) -> Unit, private val logHeaders: Boolean = false) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        val target = "${request.method} ${request.url.host}${request.url.encodedPath}"
        if (logHeaders) log("byok: → $target [${Redaction.headers(request.headers)}]")
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            log("byok: $target failed (${e::class.simpleName})")
            throw e
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        log("byok: $target → ${response.code} (${ms} ms)")
        if (logHeaders) log("byok: ← [${Redaction.headers(response.headers)}]")
        return response
    }
}

/** Suspends on an OkHttp call; cancelling the coroutine cancels the request. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        },
    )
}

/** Common HTTP status → [ByokError] mapping for the vendors' error answers. */
internal fun httpError(code: Int, retryAfter: String?): ByokError = when (code) {
    401 -> ByokError.Unauthorized
    402, 403 -> ByokError.Forbidden
    404 -> ByokError.ModelUnavailable
    408 -> ByokError.Offline
    429 -> ByokError.RateLimited(retryAfter?.toLongOrNull())
    in 500..599 -> ByokError.Server(code)
    else -> ByokError.BadResponse("http_$code")
}

/** Qualifies the OkHttp client used for direct calls with the user's own keys. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ByokHttpClient
