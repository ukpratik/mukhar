package io.github.ukpratik.mukhar.domain.metrics

/** Moving-average type-token ratio (LLD §4.2): window 50, shown only from 100 tokens ("Speak longer to unlock"). */
object Mattr {
    const val WINDOW = 50
    const val MIN_TOKENS = 100

    /** O(n) sliding window over already-normalised tokens; `null` below [MIN_TOKENS]. */
    fun compute(tokens: List<String>): Double? {
        val n = tokens.size
        if (n < MIN_TOKENS) return null
        val counts = HashMap<String, Int>()
        for (k in 0 until WINDOW) counts.merge(tokens[k], 1, Int::plus)
        var sum = counts.size.toDouble() / WINDOW
        for (k in 1..n - WINDOW) {
            val out = tokens[k - 1]
            val left = counts.getValue(out) - 1
            if (left == 0) counts.remove(out) else counts[out] = left
            counts.merge(tokens[k + WINDOW - 1], 1, Int::plus)
            sum += counts.size.toDouble() / WINDOW
        }
        return sum / (n - WINDOW + 1)
    }
}
