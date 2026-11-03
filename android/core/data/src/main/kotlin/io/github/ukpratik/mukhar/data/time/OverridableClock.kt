package io.github.ukpratik.mukhar.data.time

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import io.github.ukpratik.mukhar.domain.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's injected [Clock] (LLD §5). In debuggable builds QA can shift it (build plan "Debug hooks": clock
 * override) from the debug screen or by intent; the shift is an offset, so time keeps running. Release builds
 * ignore any stored override, and nothing in a release build writes one. The prefs file is not in Auto Backup.
 */
@Singleton
class OverridableClock internal constructor(
    prefsProvider: () -> android.content.SharedPreferences,
    private val overridesAllowed: Boolean,
    private val system: () -> Instant,
) : Clock {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        prefsProvider = { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) },
        overridesAllowed = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        system = Instant::now,
    )

    // NFR-002 (D-036): opened on first use, and never in a release build (overrides are off there), so cold start
    // does no prefs disk read on the main thread.
    private val prefs by lazy(prefsProvider)

    val isOverridden: Boolean get() = overridesAllowed && (offsetMs() != 0L || prefs.contains(KEY_ZONE))

    override fun now(): Instant = system().plusMillis(offsetMs())

    override fun zone(): ZoneId =
        (if (overridesAllowed) prefs.getString(KEY_ZONE, null) else null)?.let(ZoneId::of) ?: ZoneId.systemDefault()

    /** Makes [now] return [at] from this moment on (and keep running). Debug builds only. */
    fun setNow(at: Instant) = edit { putLong(KEY_OFFSET, Duration.between(system(), at).toMillis()) }

    fun shiftBy(duration: Duration) = edit { putLong(KEY_OFFSET, offsetMs() + duration.toMillis()) }

    fun setZone(zone: ZoneId?) = edit { if (zone == null) remove(KEY_ZONE) else putString(KEY_ZONE, zone.id) }

    fun reset() = edit { clear() }

    private fun offsetMs(): Long = if (overridesAllowed) prefs.getLong(KEY_OFFSET, 0L) else 0L

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        check(overridesAllowed) { "Clock overrides are for debug builds only" }
        prefs.edit(commit = true, action = block)
    }

    private companion object {
        const val PREFS = "debug_clock"
        const val KEY_OFFSET = "offset_ms"
        const val KEY_ZONE = "zone"
    }
}
