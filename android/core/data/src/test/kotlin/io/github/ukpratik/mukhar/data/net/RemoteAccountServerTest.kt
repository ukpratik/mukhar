package io.github.ukpratik.mukhar.data.net

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.billing.RemoteAccountServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

/** The client side of `/entitlement`, `GET /me` and `DELETE /me`, field for field with worker/src/routes. */
class RemoteAccountServerTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
    private lateinit var account: RemoteAccountServer

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/v1/")).client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(MukharApi::class.java)
        account = RemoteAccountServer(api, ErrorMapper(json))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun entitlementPostsTokenAndProductAndParsesTheView() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"premium":true,"expiry_time":1794182400000,"is_trial":true,"auto_renewing":true,"request_id":"r"}"""))
        val r = account.verify("tok-1", "premium") as Outcome.Ok
        assertThat(r.value.premium).isTrue()
        assertThat(r.value.expiryMs).isEqualTo(1794182400000L)
        assertThat(r.value.isTrial).isTrue()
        assertThat(r.value.autoRenewing).isTrue()
        val req = server.takeRequest()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.url.encodedPath).isEqualTo("/v1/entitlement")
        val body = json.parseToJsonElement(req.body!!.utf8()).jsonObject
        assertThat(body.keys).containsExactly("purchase_token", "product_id")
        assertThat(body["product_id"]!!.jsonPrimitive.content).isEqualTo("premium")
    }

    @Test
    fun purchaseInvalidAndPlayUnavailableMapToErrors() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":{"code":"PURCHASE_INVALID","message":"x"},"request_id":"r"}"""))
        val bad = account.verify("t", "premium") as Outcome.Err
        assertThat(bad.error).isEqualTo(AppError.Upstream(Stage.SERVER, "PURCHASE_INVALID"))
        server.enqueue(MockResponse(code = 502, body = """{"error":{"code":"PLAY_UNAVAILABLE","message":"x"},"request_id":"r"}"""))
        val down = account.verify("t", "premium") as Outcome.Err
        assertThat(down.error).isEqualTo(AppError.Upstream(Stage.SERVER, "PLAY_UNAVAILABLE"))
    }

    @Test
    fun meParsesEntitlementAndQuota() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"premium":false,"expiry_time":null,"is_trial":false,"auto_renewing":false,""" +
                    """"quota":{"day":"2026-11-02","talks_used":1,"talks_limit":1,"checks_used":3,"checks_limit":10,"resets_at":"2026-11-02T21:30:00Z"},"request_id":"r"}""",
            ),
        )
        val r = account.status() as Outcome.Ok
        assertThat(r.value.entitlement.premium).isFalse()
        assertThat(r.value.entitlement.expiryMs).isNull()
        assertThat(r.value.quota!!.day).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(r.value.quota!!.used).isEqualTo(1)
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1/me")
    }

    @Test
    fun deleteMeSendsADeleteWithAJsonBody() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"deleted":true,"request_id":"r"}"""))
        assertThat(account.deleteMe()).isEqualTo(Outcome.Ok(Unit))
        val req = server.takeRequest()
        assertThat(req.method).isEqualTo("DELETE")
        assertThat(req.url.encodedPath).isEqualTo("/v1/me")
        assertThat(json.parseToJsonElement(req.body!!.utf8()).jsonObject.keys).containsExactly("device_hash", "app_instance_id")
    }
}
