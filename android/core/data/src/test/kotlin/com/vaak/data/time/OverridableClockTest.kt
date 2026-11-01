package com.vaak.data.time

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The debug clock override (build plan "Debug hooks"): shiftable in debug builds, inert in release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverridableClockTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var real = Instant.parse("2026-11-05T04:30:00Z")
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun debugBuildsCanShiftTimeAndZone() {
        val clock = OverridableClock(prefs("debug_a"), overridesAllowed = true) { real }
        assertThat(clock.isOverridden).isFalse()
        clock.setNow(Instant.parse("2026-11-09T21:29:00Z"))
        assertThat(clock.now()).isEqualTo(Instant.parse("2026-11-09T21:29:00Z"))
        real = real.plusSeconds(60) // time keeps running
        assertThat(clock.now()).isEqualTo(Instant.parse("2026-11-09T21:30:00Z"))
        clock.shiftBy(Duration.ofDays(1))
        assertThat(clock.now()).isEqualTo(Instant.parse("2026-11-10T21:30:00Z"))
        clock.setZone(ZoneId.of("America/New_York"))
        assertThat(clock.zone()).isEqualTo(ZoneId.of("America/New_York"))
        assertThat(clock.isOverridden).isTrue()
        clock.reset()
        assertThat(clock.now()).isEqualTo(real)
        assertThat(clock.zone()).isEqualTo(ZoneId.systemDefault())
    }

    @Test
    fun releaseBuildsIgnoreAStoredOverride() {
        val p = prefs("debug_b")
        p.edit().putLong("offset_ms", 86_400_000L).putString("zone", "America/New_York").commit()
        val clock = OverridableClock(p, overridesAllowed = false) { real }
        assertThat(clock.now()).isEqualTo(real)
        assertThat(clock.zone()).isEqualTo(ZoneId.systemDefault())
        assertThat(clock.isOverridden).isFalse()
    }

    @Test(expected = IllegalStateException::class)
    fun releaseBuildsCantWriteAnOverride() {
        OverridableClock(prefs("debug_c"), overridesAllowed = false) { real }.shiftBy(Duration.ofHours(1))
    }
}
