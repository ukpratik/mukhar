package io.github.ukpratik.mukhar.data.byok

import io.github.ukpratik.mukhar.data.prefs.ByokUsage
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import io.github.ukpratik.mukhar.domain.Clock
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * Vendor prices for the "This month on your keys" estimate (ADR-008 "Cost transparency"). Estimates only: the
 * vendor's dashboard is the bill. Update [AS_OF] with the numbers.
 */
object ByokPrices {
    const val AS_OF = "2026-11-01"

    /** Deepgram Nova-3 pre-recorded, pay as you go (upper end of $0.0043–0.0052 a minute). */
    const val DEEPGRAM_USD_PER_MIN = 0.0052

    /** Sarvam speech-to-text (Saaras), ₹ per minute. */
    const val SARVAM_INR_PER_MIN = 0.49

    /** Claude Haiku 5.5, prompts up to 100K tokens: $0.10 in, $0.50 out per million tokens. */
    const val HAIKU_USD_PER_MTOK_IN = 0.10
    const val HAIKU_USD_PER_MTOK_OUT = 0.50

    /** Rough exchange rate for the estimate. */
    const val INR_PER_USD = 88.0

    fun estimateInr(u: ByokUsage): Double {
        val deepgram = u.deepgramMs / 60_000.0 * DEEPGRAM_USD_PER_MIN * INR_PER_USD
        val sarvam = u.sarvamMs / 60_000.0 * SARVAM_INR_PER_MIN
        val llm = (u.inputTokens * HAIKU_USD_PER_MTOK_IN + u.outputTokens * HAIKU_USD_PER_MTOK_OUT) / 1_000_000.0 * INR_PER_USD
        return deepgram + sarvam + llm
    }

    /** "≈ ₹22": whole rupees, at least ₹1 once anything was used. */
    fun roundedInr(u: ByokUsage): Int = estimateInr(u).let { if (it > 0 && it < 1) 1 else it.roundToInt() }
}

/** What the own-key pipeline needs from the usage log (a seam for tests). */
interface UsageMeter {
    suspend fun overSoftCap(): Boolean
    suspend fun record(talks: Int = 0, deepgramMs: Long = 0, sarvamMs: Long = 0, inputTokens: Long = 0, outputTokens: Long = 0)
}

/** Records this month's own-key use and answers the soft-cap question. */
class ByokUsageLog(private val settings: SettingsStore, private val clock: Clock) : UsageMeter {
    fun month(): String = YearMonth.from(clock.now().atZone(clock.zone())).toString()

    suspend fun thisMonth(): ByokUsage = settings.byokUsage.first().takeIf { it.month == month() } ?: ByokUsage(month())

    override suspend fun record(talks: Int, deepgramMs: Long, sarvamMs: Long, inputTokens: Long, outputTokens: Long) =
        settings.addByokUsage(month(), talks, deepgramMs, sarvamMs, inputTokens, outputTokens)

    /** The optional "Ask me before going over ₹N" cap: when it's reached, talks are analysed on the phone instead. */
    override suspend fun overSoftCap(): Boolean {
        val s = settings.current()
        return s.softCapOn && ByokPrices.estimateInr(thisMonth()) >= s.softCapInr
    }
}
