package io.github.ukpratik.mukhar.domain

import java.time.Instant
import java.time.ZoneId

/** Injected everywhere time matters, so streak, quota and reminder logic is deterministic in tests (LLD §5). */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
}

/** The device clock and the device's current time zone. */
object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** A settable clock for tests and previews. `zone` can change mid-test to simulate travel. */
class FixedClock(var instant: Instant, var zoneId: ZoneId = ZoneId.of("Asia/Kolkata")) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun advanceBy(duration: java.time.Duration) {
        instant = instant.plus(duration)
    }
}
