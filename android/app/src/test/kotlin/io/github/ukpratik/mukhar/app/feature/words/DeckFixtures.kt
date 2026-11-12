package io.github.ukpratik.mukhar.app.feature.words

import io.github.ukpratik.mukhar.data.repo.DeckCard
import io.github.ukpratik.mukhar.data.repo.DeckState
import io.github.ukpratik.mukhar.domain.model.Lang

/** US-081 deck cards and states for UI tests. */
object DeckFixtures {
    val lucid = DeckCard(
        id = "en:lucid", lang = Lang.EN, lemma = "lucid", translit = null, pos = "adjective", register = "formal",
        meaning = "Clear and easy to understand.", gloss = "स्पष्ट", example = "She gave a lucid explanation of the plan.", exampleGloss = null,
    )
    val nuance = DeckCard(
        id = "en:nuance", lang = Lang.EN, lemma = "nuance", translit = null, pos = "noun", register = "power",
        meaning = "A small difference in meaning.", gloss = "बारीकी", example = "He missed the nuance in her answer.", exampleGloss = null,
    )
    val sateek = DeckCard(
        id = "hi:सटीक", lang = Lang.HI, lemma = "सटीक", translit = "sateek", pos = "adjective", register = "formal",
        meaning = "बिल्कुल सही", gloss = "Precise, exact.", example = "उसका जवाब सटीक था।", exampleGloss = "Her answer was precise.",
    )

    fun state(cards: List<DeckCard> = listOf(lucid, nuance), decided: Int = 2, goal: Int = 5, keepGoing: Boolean = false, remaining: Int = cards.size) =
        DeckState(cards = cards, remaining = remaining, decidedToday = decided, goal = goal, keepGoing = keepGoing)
}
