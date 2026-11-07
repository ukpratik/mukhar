package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.util.IdGenerator
import io.github.ukpratik.mukhar.data.work.AnalysisPipeline
import io.github.ukpratik.mukhar.data.work.SentenceCheckRequest
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.quota.Decision
import io.github.ukpratik.mukhar.domain.quota.QuotaKind
import io.github.ukpratik.mukhar.domain.quota.QuotaMirror
import io.github.ukpratik.mukhar.domain.quota.QuotaRequest
import io.github.ukpratik.mukhar.domain.quota.Tier
import io.github.ukpratik.mukhar.domain.quota.Usage
import io.github.ukpratik.mukhar.domain.words.SentenceCheckRules
import io.github.ukpratik.mukhar.domain.words.SentenceVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.File

/** Checks used today against the plan's limit (free 10). A display mirror only: the Worker is the authority. */
data class CheckQuota(val used: Int, val limit: Int, val unlimited: Boolean = false) {
    val exhausted: Boolean get() = !unlimited && used >= limit

    companion object {
        /** On the phone and on the user's own keys there's no daily check limit (D-039). */
        val UNLIMITED = CheckQuota(0, Int.MAX_VALUE, unlimited = true)
    }
}

data class SentenceCheck(
    val verdict: SentenceVerdict,
    val transcript: String,
    val corrected: String,
    val example: String,
    val note: String,
    val quota: CheckQuota,
)

/**
 * US-023 "Say it in a sentence": a ≤ 15 s clip → `/check-sentence` through the [AnalysisPipeline] (fake in debug).
 * The daily count is mirrored on the device with [QuotaMirror] (10 free checks; resets 03:00 IST) so the counter
 * and the "Checks are done for today" state work without a round trip; a 429 from the server wins. The clip is
 * deleted after the call, whatever the answer.
 */
class SentenceChecker(
    private val pipeline: AnalysisPipeline,
    private val settings: SettingsStore,
    private val entitlements: Entitlements,
    private val clock: Clock,
    private val ids: IdGenerator,
    /** Whether the server quota applies (only Mukhar Cloud, off in R1). Tests pass `{ true }` to cover the cloud rules. */
    private val serverQuota: (AnalysisMode) -> Boolean = { it.usesServerQuota },
) {
    val quota: Flow<CheckQuota> = combine(settings.checksUsed, entitlements.isPremium, settings.analysisMode) { (day, used), premium, mode ->
        if (serverQuota(mode)) status(day, used, premium) else CheckQuota.UNLIMITED
    }

    suspend fun check(lang: Lang, target: String, meaning: String, audio: File, durationMs: Long): Outcome<SentenceCheck> {
        try {
            if (!serverQuota(settings.analysisMode.first())) return checkWithoutQuota(lang, target, meaning, audio, durationMs)
            val premium = entitlements.isPremium.first()
            val (day, used) = settings.checksUsed.first()
            val today = QuotaMirror.quotaDay(clock.now())
            val usedToday = if (day == today) used else 0
            val decision = QuotaMirror.decide(
                QuotaRequest("me", if (premium) Tier.PREMIUM else Tier.FREE, clock.now(), QuotaKind.CHECK, durationMs),
                usage = { d -> if (d == today) Usage(uidChecks = usedToday) else Usage() },
            )
            if (decision is Decision.Deny) {
                return Outcome.Err(
                    if (decision.retryAfterS != null) AppError.Quota(QuotaMirror.resetsAt(today).toEpochMilli()) else AppError.AudioTooLong,
                )
            }
            val request = SentenceCheckRequest(ids.newId(), lang, target, meaning, durationMs)
            return when (val r = pipeline.checkSentence(request, audio)) {
                is Outcome.Err -> {
                    if (r.error is AppError.Quota) settings.setChecksUsed(today, status(today, 0, premium).limit)
                    r
                }
                is Outcome.Ok -> {
                    settings.setChecksUsed(today, usedToday + 1)
                    val a = r.value
                    Outcome.Ok(
                        SentenceCheck(
                            verdict = SentenceCheckRules.verdict(a.result, a.wordFound, target, a.transcript),
                            transcript = a.transcript, corrected = a.corrected, example = a.example, note = a.note,
                            quota = status(today, usedToday + 1, premium),
                        ),
                    )
                }
            }
        } finally {
            audio.delete()
        }
    }

    /** Local and own-key modes: no QuotaMirror, no counter (D-039). The ≤ 15 s clip rule still applies. */
    private suspend fun checkWithoutQuota(lang: Lang, target: String, meaning: String, audio: File, durationMs: Long): Outcome<SentenceCheck> {
        if (durationMs > SentenceCheckRules.MAX_CLIP_MS + CLIP_GRACE_MS) return Outcome.Err(AppError.AudioTooLong)
        val request = SentenceCheckRequest(ids.newId(), lang, target, meaning, durationMs)
        return when (val r = pipeline.checkSentence(request, audio)) {
            is Outcome.Err -> r
            is Outcome.Ok -> {
                val a = r.value
                Outcome.Ok(
                    SentenceCheck(
                        verdict = SentenceCheckRules.verdict(a.result, a.wordFound, target, a.transcript),
                        transcript = a.transcript, corrected = a.corrected, example = a.example, note = a.note,
                        quota = CheckQuota.UNLIMITED,
                    ),
                )
            }
        }
    }

    private fun status(day: java.time.LocalDate?, used: Int, premium: Boolean): CheckQuota {
        val today = QuotaMirror.quotaDay(clock.now())
        val s = QuotaMirror.status(if (premium) Tier.PREMIUM else Tier.FREE, clock.now(), Usage(uidChecks = if (day == today) used else 0))
        return CheckQuota(s.checksUsed, s.checksLimit)
    }

    private companion object {
        /** The recorder stops at 15 s; allow encoder rounding. */
        const val CLIP_GRACE_MS = 1_000L
    }
}
