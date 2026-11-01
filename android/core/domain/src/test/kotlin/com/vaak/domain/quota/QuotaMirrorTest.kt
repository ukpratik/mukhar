package com.vaak.domain.quota

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.vaak.domain.TestVectors
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.LocalDate

@RunWith(Parameterized::class)
class QuotaDecideVectorTest(private val name: String, private val case: JsonObject) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = TestVectors.load("quota.json").getValue("decide").jsonArray.map {
            arrayOf(it.jsonObject.getValue("name").jsonPrimitive.content, it.jsonObject)
        }
    }

    @Test
    fun decidesAsSpecified() {
        val parent = case["parent"]?.jsonObject?.let {
            ParentSession(it.getValue("uid").jsonPrimitive.content, it.getValue("kind").jsonPrimitive.content, it.getValue("retake_used").jsonPrimitive.boolean)
        }
        val request = QuotaRequest(
            uid = case.getValue("uid").jsonPrimitive.content,
            tier = Tier.valueOf(case.getValue("tier").jsonPrimitive.content.uppercase()),
            now = Instant.parse(case.getValue("now").jsonPrimitive.content),
            kind = QuotaKind.valueOf(case.getValue("kind").jsonPrimitive.content.uppercase()),
            durationMs = case.getValue("duration_ms").jsonPrimitive.long,
            queued = case.getValue("queued").jsonPrimitive.boolean,
            parent = parent,
        )
        val usage = case.getValue("usage").jsonObject
        val decision = QuotaMirror.decide(request, { day ->
            val u = usage[day.toString()]?.jsonObject ?: return@decide Usage()
            fun f(k: String) = u[k]?.jsonPrimitive?.int ?: 0
            Usage(f("uid_talks"), f("device_talks"), f("uid_checks"), f("device_checks"))
        })
        val expected = case.getValue("expected").jsonObject
        if (expected.getValue("allow").jsonPrimitive.boolean) {
            val debit = expected.getValue("debit_day").let { if (it is JsonNull) null else LocalDate.parse(it.jsonPrimitive.content) }
            assertWithMessage(name).that(decision).isEqualTo(Decision.Allow(debit))
        } else {
            val retry = expected["retry_after_s"]?.jsonPrimitive?.long
            assertWithMessage(name).that(decision)
                .isEqualTo(Decision.Deny(DenyCode.valueOf(expected.getValue("code").jsonPrimitive.content), retry))
        }
    }
}

@RunWith(Parameterized::class)
class QuotaBaselineVectorTest(private val name: String, private val case: JsonObject) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = TestVectors.load("quota.json").getValue("baseline").jsonArray.map {
            arrayOf(it.jsonObject.getValue("name").jsonPrimitive.content, it.jsonObject)
        }
    }

    @Test
    fun decidesAsSpecified() {
        val used = case.getValue("baseline_used").jsonObject
        val request = QuotaRequest(
            uid = "u1",
            tier = Tier.valueOf(case.getValue("tier").jsonPrimitive.content.uppercase()),
            now = Instant.parse(case.getValue("now").jsonPrimitive.content),
            kind = QuotaKind.BASELINE,
            durationMs = case.getValue("duration_ms").jsonPrimitive.long,
            baselineUsed = BaselineUsed(used.getValue("uid").jsonPrimitive.boolean, used.getValue("device").jsonPrimitive.boolean),
        )
        val usage = case.getValue("usage").jsonObject
        val decision = QuotaMirror.decide(request, { day ->
            val u = usage[day.toString()]?.jsonObject ?: return@decide Usage()
            fun f(k: String) = u[k]?.jsonPrimitive?.int ?: 0
            Usage(f("uid_talks"), f("device_talks"), f("uid_checks"), f("device_checks"))
        })
        val expected = case.getValue("expected").jsonObject
        if (expected.getValue("allow").jsonPrimitive.boolean) {
            assertWithMessage(name).that(decision).isEqualTo(Decision.Allow(null))
        } else {
            assertWithMessage(name).that(decision)
                .isEqualTo(Decision.Deny(DenyCode.valueOf(expected.getValue("code").jsonPrimitive.content)))
        }
    }
}

class QuotaMirrorTest {
    private val json = TestVectors.load("quota.json")

    @Test
    fun quotaDayAndResetMatchVectors() {
        for (c in json.getValue("quota_day").jsonArray.map { it.jsonObject }) {
            val now = Instant.parse(c.getValue("now").jsonPrimitive.content)
            val day = QuotaMirror.quotaDay(now)
            assertWithMessage("$now").that(day.toString()).isEqualTo(c.getValue("day").jsonPrimitive.content)
            assertWithMessage("$now").that(QuotaMirror.resetsAt(day).toString()).isEqualTo(c.getValue("resets_at").jsonPrimitive.content)
        }
    }

    @Test
    fun limitsMatchVectors() {
        val l = json.getValue("limits").jsonObject
        fun i(k: String) = l.getValue(k).jsonPrimitive.int
        assertThat(Limits()).isEqualTo(
            Limits(i("free_talks_per_day"), i("free_talk_max_s"), i("premium_talks_per_day"), i("premium_talk_max_s"),
                i("free_checks_per_day"), i("premium_checks_per_day"), i("check_max_s")),
        )
    }

    @Test
    fun quotaDayIgnoresTheDeviceZone() {
        // 02:59 IST on Nov 2 is still quota day Nov 1, whatever zone the phone is in.
        assertThat(QuotaMirror.quotaDay(Instant.parse("2026-11-01T21:29:00Z"))).isEqualTo(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun statusShowsLimitsPerTier() {
        val now = Instant.parse("2026-11-02T05:00:00Z")
        val free = QuotaMirror.status(Tier.FREE, now, Usage(uidTalks = 1, deviceChecks = 3))
        assertThat(free).isEqualTo(QuotaStatus(LocalDate.of(2026, 11, 2), 1, 1, 3, 10, Instant.parse("2026-11-02T21:30:00Z")))
        val premium = QuotaMirror.status(Tier.PREMIUM, now, Usage())
        assertThat(premium.talksLimit).isEqualTo(20)
        assertThat(premium.checksLimit).isEqualTo(200)
    }
}
