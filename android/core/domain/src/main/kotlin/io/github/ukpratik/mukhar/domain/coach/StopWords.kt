package io.github.ukpratik.mukhar.domain.coach

import io.github.ukpratik.mukhar.domain.model.Lang

/**
 * Function words the offline coach never calls "overused" (normalised form, see `Normalizer`: no nukta, no
 * punctuation, lower case). Hindi talks often borrow English, so the Hindi set includes the common English
 * function words too.
 */
object StopWords {
    private val EN = setOf(
        "a", "an", "the", "and", "or", "but", "if", "then", "so", "because", "as", "of", "to", "in", "on", "at", "by",
        "for", "with", "from", "about", "into", "over", "after", "before", "under", "between", "through", "during",
        "up", "down", "out", "off", "than", "too", "also", "not", "no", "yes", "is", "am", "are", "was", "were", "be",
        "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "will", "would", "shall",
        "should", "can", "could", "may", "might", "must", "i", "me", "my", "mine", "myself", "we", "us", "our", "ours",
        "you", "your", "yours", "he", "him", "his", "she", "her", "hers", "it", "its", "they", "them", "their",
        "theirs", "this", "that", "these", "those", "there", "here", "what", "which", "who", "whom", "whose", "when",
        "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "only",
        "own", "same", "very", "just", "now", "one", "two", "im", "ive", "id", "ill", "dont", "didnt", "doesnt", "its",
        "thats", "theres", "were", "youre", "theyre", "cant", "wont", "isnt", "wasnt", "arent", "let", "lets", "get",
        "got", "go", "going", "went", "like", "um", "uh", "er", "hmm", "okay", "ok", "oh", "well", "yeah",
    )

    private val HI = setOf(
        "और", "या", "लेकिन", "पर", "पे", "तो", "भी", "ही", "कि", "की", "का", "के", "को", "से", "में", "मैं", "मै",
        "हम", "हमें", "हमारा", "हमारी", "हमारे", "मेरा", "मेरी", "मेरे", "मुझे", "मुझसे", "आप", "आपका", "आपकी", "आपके",
        "आपको", "तुम", "तुम्हारा", "तू", "वह", "वो", "वे", "यह", "ये", "इस", "उस", "इन", "उन", "इसे", "उसे", "इसका",
        "उसका", "उसकी", "उसके", "उनका", "उनकी", "उनके", "जो", "जब", "तब", "जहां", "वहां", "यहां", "क्या", "क्यों", "कैसे",
        "कौन", "कब", "कहां", "है", "हैं", "था", "थी", "थे", "हूं", "हो", "होता", "होती", "होते", "होना", "हुआ", "हुई",
        "हुए", "रहा", "रही", "रहे", "गया", "गई", "गए", "कर", "करना", "करता", "करती", "करते", "किया", "की", "ने", "न",
        "नहीं", "ना", "हां", "जी", "एक", "दो", "कुछ", "सब", "सभी", "अब", "फिर", "बस", "अपना", "अपनी", "अपने", "लिए",
        "साथ", "बाद", "पहले", "तक", "वाला", "वाली", "वाले", "जैसे", "ऐसे", "वैसे", "यानी", "मतलब", "उम्म", "आ", "अ",
        "हम्म", "ठीक", "and", "the", "a", "to", "of", "is", "so", "i", "in", "it", "that", "like", "basically",
        "actually", "ok", "okay", "toh", "to", "hai", "hain", "main", "mai", "ki", "ka", "ke", "ko", "se", "aur",
    )

    fun of(lang: Lang): Set<String> = if (lang == Lang.HI) HI else EN
}
