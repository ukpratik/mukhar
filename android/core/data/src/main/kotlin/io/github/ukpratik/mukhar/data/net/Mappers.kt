package io.github.ukpratik.mukhar.data.net

import io.github.ukpratik.mukhar.domain.feedback.BetterVersion
import io.github.ukpratik.mukhar.domain.feedback.Feedback
import io.github.ukpratik.mukhar.domain.feedback.Glow
import io.github.ukpratik.mukhar.domain.feedback.Grow
import io.github.ukpratik.mukhar.domain.feedback.GrowIssue
import io.github.ukpratik.mukhar.domain.feedback.Mistake
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.feedback.Safety
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.model.Word

// Wire ⇄ domain mapping. Domain stays free of serialization (LLD §1).

fun WordDto.toDomain(): Word = Word(w = w, startMs = s, endMs = maxOf(s, e), conf = c.coerceIn(0.0, 1.0))

fun Word.toDto(): WordDto = WordDto(w = w, s = startMs, e = endMs, c = conf)

fun FeedbackDto.toDomain(): Feedback = Feedback(
    summary = summary,
    glow = glow?.let { Glow(it.start, it.end, it.quote, it.note) },
    grow = grow?.let { Grow(it.start, it.end, it.quote, GrowIssue.fromKey(it.issue), it.why, it.how) },
    betterVersions = betterVersions.map { bv ->
        BetterVersion(
            bv.start, bv.end, bv.quote, bv.rewrite,
            bv.upgradedWords.map { UpgradedWord(it.word, it.meaning, Register.fromKey(it.register)) },
        )
    },
    mistakes = mistakes.map { Mistake(it.start, it.end, it.quote, it.fix) },
    safety = Safety.fromKey(safety),
)

fun Feedback.toDto(): FeedbackDto = FeedbackDto(
    summary = summary,
    glow = glow?.let { GlowDto(it.start, it.end, it.quote, it.note) },
    grow = grow?.let { GrowDto(it.start, it.end, it.quote, it.issue.key, it.why, it.how) },
    betterVersions = betterVersions.map { bv ->
        BetterVersionDto(
            bv.start, bv.end, bv.quote, bv.rewrite,
            bv.upgradedWords.map { UpgradedWordDto(it.word, it.meaning, it.register.key) },
        )
    },
    mistakes = mistakes.map { MistakeDto(it.start, it.end, it.quote, it.fix) },
    safety = safety.key,
)
