package io.github.ukpratik.mukhar.domain.feedback

import io.github.ukpratik.mukhar.domain.analysis.AnalysisNote

/** The coach's notes for one talk (LLD §4.3 output schema). Token indices refer to the indexed transcript. */
data class Feedback(
    val summary: String,
    val glow: Glow?,
    val grow: Grow?,
    val betterVersions: List<BetterVersion>,
    val mistakes: List<Mistake>,
    val safety: Safety,
    /** Offline coach (RuleCoach): overused words with curated "Try instead" alternatives. Empty for AI notes. */
    val wordUpgrades: List<WordUpgrade> = emptyList(),
    /** How this talk was analysed, when it's worth saying (fallbacks, offline transcript). */
    val notes: List<AnalysisNote> = emptyList(),
) {
    companion object {
        /** A glow or grow that isn't tied to a quote (metrics-based, from the offline coach or a fallback). */
        const val NO_SPAN = -1
    }
}

/** True when the item quotes a token range of the transcript. */
val Glow.hasSpan: Boolean get() = start >= 0 && quote.isNotBlank()
val Grow.hasSpan: Boolean get() = start >= 0 && quote.isNotBlank()

/** An overused word ([count] times, first at token [firstIndex]) and up to 3 curated alternatives. */
data class WordUpgrade(val word: String, val count: Int, val firstIndex: Int, val alternatives: List<Alternative>)

/** One curated alternative from `synonyms_{en,hi}.json`: register and an example sentence. */
data class Alternative(val word: String, val meaning: String, val register: Register, val example: String)

data class Glow(val start: Int, val end: Int, val quote: String, val note: String)

data class Grow(val start: Int, val end: Int, val quote: String, val issue: GrowIssue, val why: String, val how: String)

data class BetterVersion(
    val start: Int,
    val end: Int,
    val quote: String,
    val rewrite: String,
    val upgradedWords: List<UpgradedWord>,
)

data class UpgradedWord(val word: String, val meaning: String, val register: Register)

data class Mistake(val start: Int, val end: Int, val quote: String, val fix: String)

enum class GrowIssue(val key: String) {
    FILLER("filler"), PACE("pace"), CLARITY("clarity"), WEAK_WORD("weak_word"), REPETITION("repetition"),
    STRUCTURE("structure"), GRAMMAR("grammar");

    companion object {
        fun fromKey(key: String): GrowIssue = entries.firstOrNull { it.key == key } ?: CLARITY
    }
}

enum class Register(val key: String) {
    FORMAL("formal"), CASUAL("casual"), POWER("power");

    companion object {
        fun fromKey(key: String): Register = entries.firstOrNull { it.key == key } ?: CASUAL
    }
}

enum class Safety(val key: String) {
    NONE("none"), UNSAFE("unsafe"), SELF_HARM("self_harm");

    companion object {
        fun fromKey(key: String): Safety = entries.firstOrNull { it.key == key } ?: NONE
    }
}
