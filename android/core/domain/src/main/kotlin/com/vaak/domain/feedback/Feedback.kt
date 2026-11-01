package com.vaak.domain.feedback

/** The coach's notes for one talk (LLD §4.3 output schema). Token indices refer to the indexed transcript. */
data class Feedback(
    val summary: String,
    val glow: Glow?,
    val grow: Grow?,
    val betterVersions: List<BetterVersion>,
    val mistakes: List<Mistake>,
    val safety: Safety,
)

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
