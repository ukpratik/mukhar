package io.github.ukpratik.mukhar.app.feature.soundlab

import java.io.File

object SoundLabFixtures {
    val vw = MinimalPair(
        id = "en-v-w", langCode = "en", label = "v / w", hint = "Common for Hindi speakers",
        listenHint = "Watch for teeth on the lip.", revealHint = "Listen for the rounded lips.",
        selfCheck = "Did your lips round?", say = listOf("en_wine", "en_vine"),
        a = PairSide("v", "top teeth on your lower lip.", listOf(PairWord("en_vest", "vest"), PairWord("en_vine", "vine"))),
        b = PairSide("w", "round your lips.", listOf(PairWord("en_west", "west"), PairWord("en_wine", "wine"))),
    )

    val rr = MinimalPair(
        id = "hi-rra-ra", langCode = "hi", label = "ड़ / र", hint = "Flap vs. tap",
        listenHint = "ड़ flicks; र taps.", revealHint = "In ड़ the tongue flicks forward.",
        selfCheck = "Did मोड़ flick?", say = listOf("hi_mod", "hi_mor"),
        a = PairSide("ड़", "जीभ ऊपर मोड़कर आगे झटकें।", listOf(PairWord("hi_padi", "पड़ी", "paṛī", "lay down"), PairWord("hi_mod", "मोड़", "moṛ", "turn"))),
        b = PairSide("र", "जीभ दाँतों के पीछे हल्के से छुए।", listOf(PairWord("hi_pari", "परी", "parī", "fairy"), PairWord("hi_mor", "मोर", "mor", "peacock"))),
    )

    /** The shipped pack, read from the module's assets (unit tests run with the module as the working directory). */
    fun shippedPack(): PairPack = PairParser.parse(File("src/main/assets/pairs.json").readText())
}
