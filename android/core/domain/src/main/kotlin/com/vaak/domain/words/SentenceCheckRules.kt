package com.vaak.domain.words

import com.vaak.domain.fsrs.Rating

/** S13 states 3 to 5: ✅ "Nicely used", "Almost there", ❌ "Not quite yet" (neutral, never red). */
enum class SentenceVerdict(val suggested: Rating) {
    NICELY_USED(Rating.GOOD),
    ALMOST(Rating.HARD),
    NOT_YET(Rating.AGAIN),
}

/**
 * Maps the Worker's `/check-sentence` answer (`result: ok|almost|no`, `word_found`) to the verdict shown in S13
 * (US-023). The client re-runs [TargetWordMatcher] on the transcript too: a sentence the STT heard without the
 * target word can't be "Nicely used". The suggested grade is outlined, never auto-picked (S13 notes).
 */
object SentenceCheckRules {
    const val MAX_CLIP_MS = 15_000L

    fun verdict(result: String, serverWordFound: Boolean, target: String, transcript: String): SentenceVerdict {
        val found = serverWordFound || TargetWordMatcher.found(target, transcript.split(' '))
        return when (result.lowercase()) {
            "ok" -> if (found) SentenceVerdict.NICELY_USED else SentenceVerdict.ALMOST
            "almost" -> SentenceVerdict.ALMOST
            else -> SentenceVerdict.NOT_YET
        }
    }
}
