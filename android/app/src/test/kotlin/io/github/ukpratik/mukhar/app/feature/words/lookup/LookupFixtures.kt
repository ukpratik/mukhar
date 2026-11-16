package io.github.ukpratik.mukhar.app.feature.words.lookup

import io.github.ukpratik.mukhar.data.byok.ExplainExample
import io.github.ukpratik.mukhar.data.byok.WordExplanation
import io.github.ukpratik.mukhar.data.content.CuratedWord
import io.github.ukpratik.mukhar.data.dictionary.LookupEntry
import io.github.ukpratik.mukhar.data.dictionary.LookupHit
import io.github.ukpratik.mukhar.domain.dictionary.DictEntry
import io.github.ukpratik.mukhar.domain.dictionary.DictSense
import io.github.ukpratik.mukhar.domain.feedback.Alternative
import io.github.ukpratik.mukhar.domain.feedback.Register
import io.github.ukpratik.mukhar.domain.model.Lang

/** D-056 look-up fixtures: an English dictionary word, a Mukhar word, a Hindi word, and an AI answer. */
object LookupFixtures {
    val happyHit = LookupHit("en:happy", Lang.EN, "happy", null, "adjective", "enjoying or showing or marked by joy or pleasure", "ख़ुश, आनंदित, सुखी", mukhar = false)
    val articulateHit = LookupHit("en:articulate", Lang.EN, "articulate", null, "verb", "To say an idea clearly so others understand it.", "स्पष्ट रूप से कहना", mukhar = true)
    val khushHit = LookupHit("hi:खुश", Lang.HI, "ख़ुश", "xuś", "adjective", "happy, pleased", null, mukhar = false)

    val happyDict = DictEntry(
        Lang.EN, "happy", null, "ˈhæpi", 296,
        listOf(
            DictSense("adjective", "enjoying or showing or marked by joy or pleasure", "a happy smile"),
            DictSense("adjective", "marked by good fortune", "a felicitous life", synonyms = listOf("felicitous")),
            DictSense("adjective", "eagerly disposed to act or to be of service", "glad to help", synonyms = listOf("glad")),
        ),
        hindi = listOf("ख़ुश", "आनंदित", "सुखी", "प्रसन्न"),
        forms = listOf("happier", "happiest"),
    )

    val happy = LookupEntry(
        "en:happy", Lang.EN, "happy", null, "ˈhæpi", curated = null, dict = happyDict,
        better = listOf(
            Alternative("delighted", "very pleased", Register.CASUAL, "I'm delighted with the result."),
            Alternative("content", "quietly satisfied", Register.FORMAL, "She seems content in her new role."),
        ),
    )

    val articulateCurated = CuratedWord(
        "en:articulate", Lang.EN, "articulate", null, "verb", "To say an idea clearly so others understand it.", "स्पष्ट रूप से कहना",
        "She articulated the plan in two minutes.", null, listOf("professional"), "formal", "medium",
    )

    val articulate = LookupEntry("en:articulate", Lang.EN, "articulate", null, "ˈɑːtɪkjʊleɪt", articulateCurated, dict = null, better = emptyList())

    val khush = LookupEntry(
        "hi:खुश", Lang.HI, "ख़ुश", "xuś", "xʊʃ", curated = null,
        dict = DictEntry(Lang.HI, "ख़ुश", "xuś", "xʊʃ", 120, listOf(DictSense("adjective", "happy, pleased", "मैं बहुत ख़ुश हूँ।", "I am very happy."))),
        better = emptyList(),
    )

    val lucidAi = WordExplanation(
        isWord = true, didYouMean = "", pos = "adjective", meaningEn = "Clear and easy to understand.", meaningHi = "साफ़ और समझने में आसान",
        pronunciation = "LOO-sid",
        examples = listOf(ExplainExample("Her answer was lucid.", "उसका जवाब साफ़ था।"), ExplainExample("Give me a lucid summary.", "मुझे एक साफ़ सारांश दो।")),
        usageNote = "Slightly formal; good for writing and meetings.", synonyms = listOf("clear", "plain", "coherent"),
    )
}
