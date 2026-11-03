package io.github.ukpratik.mukhar.domain.model

/** One STT word with timestamps (ms from the start of the recording) and confidence 0..1 (LLD §2.1 `tokens_json`). */
data class Word(val w: String, val startMs: Long, val endMs: Long, val conf: Double) {
    init {
        require(endMs >= startMs) { "endMs must be ≥ startMs" }
        require(conf in 0.0..1.0) { "conf must be in 0..1" }
    }
}
