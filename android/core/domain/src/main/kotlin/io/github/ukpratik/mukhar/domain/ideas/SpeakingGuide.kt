package io.github.ukpratik.mukhar.domain.ideas

import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.prompt.Difficulty
import io.github.ukpratik.mukhar.domain.prompt.Prompt
import io.github.ukpratik.mukhar.domain.prompt.Track

/**
 * The six goal tracks a speaking guide can follow (US-078). Keys match the onboarding goal tracks. Prompts carry
 * only the four talk tracks; content creation and word power come from the user's goals.
 */
enum class GuideTrack(val key: String) {
    PROFESSIONAL("professional"),
    INTERVIEWS("interviews"),
    PUBLIC_SPEAKING("public_speaking"),
    CONTENT_CREATION("content_creation"),
    WORD_POWER("word_power"),
    EVERYDAY("everyday");

    companion object {
        fun fromKey(key: String?): GuideTrack? = entries.firstOrNull { it.key == key }
        fun of(track: Track): GuideTrack = fromKey(track.key) ?: EVERYDAY
    }
}

/** One step of the structure. [forThisPrompt] is the prompt's own `prep` line for that step, when it has one. */
data class GuideStep(val label: String, val cue: String, val forThisPrompt: String? = null)

/**
 * The free, offline speaking guide (US-078 a): a structure for the track, a fill-in prompt per step, an opening line
 * to borrow and up to 3 of the user's own words. Never a script: every line is a cue, not something to read out.
 */
data class SpeakingGuide(
    val track: GuideTrack,
    val lang: Lang,
    val difficulty: Difficulty,
    /** "STAR", "PREP"… */
    val structureName: String,
    /** "Situation → Task → Action → Result". */
    val structureLine: String,
    val steps: List<GuideStep>,
    /** The prompt's prep points when they don't map one-to-one onto the steps (non-PREP structures). */
    val promptPoints: List<String>,
    val opening: String,
    val wordsToTry: List<String>,
    /** The prompt's own tip ("Try: what it was → what you did…"), when it has one. */
    val tip: String?,
    /** How much to say, by difficulty. */
    val pace: String,
)

/**
 * Builds a [SpeakingGuide] from the bundled templates (pure; D-046). The prompt's `prep` lines are PREP-shaped, so
 * PREP structures (Professional, Word power) put each line under its step; the other structures list them as
 * "for this prompt" points. The opening is chosen by the prompt id, so it stays the same for a prompt.
 */
object SpeakingGuideBuilder {
    const val WORDS_TO_TRY = 3

    fun build(prompt: Prompt, track: GuideTrack, words: List<String>): SpeakingGuide {
        val lang = prompt.lang
        val t = SpeakingGuideTemplates.template(track, lang)
        val prepFits = t.prepShaped && prompt.prep.size == t.steps.size
        val steps = t.steps.mapIndexed { i, (label, cue) -> GuideStep(label, cue, if (prepFits) prompt.prep[i] else null) }
        return SpeakingGuide(
            track = track,
            lang = lang,
            difficulty = prompt.difficulty,
            structureName = t.name,
            structureLine = t.steps.joinToString(" → ") { it.first },
            steps = steps,
            promptPoints = if (prepFits) emptyList() else prompt.prep,
            opening = t.openings[Math.floorMod(prompt.id.hashCode(), t.openings.size)],
            wordsToTry = words.map(String::trim).filter(String::isNotEmpty).distinct().take(WORDS_TO_TRY),
            tip = prompt.tip?.takeIf(String::isNotBlank),
            pace = SpeakingGuideTemplates.pace(prompt.difficulty, lang),
        )
    }
}

/** The template copy, in English and in natural Hindi (Devanagari, everyday English words allowed, D-005). */
internal object SpeakingGuideTemplates {
    class Template(val name: String, val steps: List<Pair<String, String>>, val openings: List<String>, val prepShaped: Boolean = false)

    fun template(track: GuideTrack, lang: Lang): Template = if (lang == Lang.HI) hi(track) else en(track)

    fun pace(d: Difficulty, lang: Lang): String = when (lang) {
        Lang.EN -> when (d) {
            Difficulty.EASY -> "Keep it simple: one or two sentences for each step."
            Difficulty.MEDIUM -> "Aim for about 30 seconds on each step."
            Difficulty.HARD -> "Stretch yourself: raise a counter-point and answer it before you close."
        }
        Lang.HI -> when (d) {
            Difficulty.EASY -> "आसान रखिए: हर कदम पर एक-दो वाक्य।"
            Difficulty.MEDIUM -> "हर कदम पर लगभग 30 सेकंड बोलिए।"
            Difficulty.HARD -> "थोड़ा आगे बढ़िए: एक उलटी दलील रखिए और समापन से पहले उसका जवाब दीजिए।"
        }
    }

    private val EN_PREP = listOf(
        "Point" to "Say your main point in one clear sentence.",
        "Reason" to "Give the one reason that matters most to your listener.",
        "Example" to "Share one real moment, number or story that proves it.",
        "Point again" to "Repeat your point and say what happens next.",
    )
    private val EN_PREP_OPENINGS = listOf(
        "Here's my view, in one line: …",
        "The short answer is …, and here's why.",
        "Let me start with the bottom line: …",
    )

    private fun en(track: GuideTrack): Template = when (track) {
        GuideTrack.PROFESSIONAL -> Template("PREP", EN_PREP, EN_PREP_OPENINGS, prepShaped = true)
        GuideTrack.WORD_POWER -> Template(
            "PREP + words",
            listOf(
                EN_PREP[0],
                EN_PREP[1],
                "Example" to "Tell one real moment, and use one of your words to try in it.",
                "Point again" to "Repeat your point, and fit in a second new word.",
            ),
            EN_PREP_OPENINGS,
            prepShaped = true,
        )
        GuideTrack.INTERVIEWS -> Template(
            "STAR",
            listOf(
                "Situation" to "Set the scene in a sentence or two: where, when, who.",
                "Task" to "What was your job, or the challenge you owned?",
                "Action" to "What did you do? Say \"I\", and name two or three steps.",
                "Result" to "What changed? Give a number if you can, and one thing you learnt.",
            ),
            listOf(
                "A good example is from my time at …",
                "Let me tell you about a time when …",
                "One situation that comes to mind is …",
            ),
        )
        GuideTrack.PUBLIC_SPEAKING -> Template(
            "Hook → 3 points → close",
            listOf(
                "Hook" to "Open with a question, a surprising fact or a quick story.",
                "Point 1" to "Your first idea, with one example.",
                "Point 2" to "Your second idea, a little different from the first.",
                "Point 3" to "Your strongest idea, saved for last.",
                "Close" to "Come back to your hook and leave them one thing to do.",
            ),
            listOf(
                "Raise your hand if you've ever …",
                "Here's a number that surprised me: …",
                "Picture this: …",
            ),
        )
        GuideTrack.CONTENT_CREATION -> Template(
            "Hook → story → takeaway → call to action",
            listOf(
                "Hook (3 s)" to "One bold line that makes people stop scrolling.",
                "Story" to "A quick, real moment: what happened, told fast.",
                "Takeaway" to "The one lesson your viewer can use today.",
                "Call to action" to "Tell them what to do next: try it, comment or follow.",
            ),
            listOf(
                "Nobody tells you this about …",
                "I tried … so you don't have to.",
                "Stop doing … Here's why.",
            ),
        )
        GuideTrack.EVERYDAY -> Template(
            "What happened → how it felt → what's next",
            listOf(
                "What happened" to "Tell it in order: the setting first, then the moment.",
                "How it felt" to "Name the feeling, and why it hit you that way.",
                "What's next" to "What you'll do now, or what you'd tell a friend.",
            ),
            listOf(
                "So, something happened the other day …",
                "You won't believe what happened when …",
                "I've been thinking about …",
            ),
        )
    }

    private val HI_PREP = listOf(
        "बात" to "अपनी मुख्य बात एक साफ़ वाक्य में कहिए।",
        "वजह" to "सुनने वाले के लिए सबसे ज़रूरी एक वजह बताइए।",
        "उदाहरण" to "एक असली पल, आँकड़ा या किस्सा सुनाइए जो इसे साबित करे।",
        "फिर से बात" to "अपनी बात दोहराइए और बताइए कि आगे क्या होगा।",
    )
    private val HI_PREP_OPENINGS = listOf(
        "सीधी बात कहूँ तो …",
        "छोटा जवाब है …, और इसकी वजह यह है।",
        "सबसे पहले नतीजा: …",
    )

    private fun hi(track: GuideTrack): Template = when (track) {
        GuideTrack.PROFESSIONAL -> Template("PREP", HI_PREP, HI_PREP_OPENINGS, prepShaped = true)
        GuideTrack.WORD_POWER -> Template(
            "PREP + नए शब्द",
            listOf(
                HI_PREP[0],
                HI_PREP[1],
                "उदाहरण" to "एक असली पल सुनाइए, और उसमें अपने नए शब्दों में से एक इस्तेमाल कीजिए।",
                "फिर से बात" to "अपनी बात दोहराइए, और एक और नया शब्द आज़माइए।",
            ),
            HI_PREP_OPENINGS,
            prepShaped = true,
        )
        GuideTrack.INTERVIEWS -> Template(
            "STAR",
            listOf(
                "हालात" to "एक-दो वाक्य में माहौल बताइए: कहाँ, कब, कौन।",
                "ज़िम्मेदारी" to "आपका काम या आपकी चुनौती क्या थी?",
                "कदम" to "आपने क्या किया? \"मैंने\" कहकर दो-तीन कदम बताइए।",
                "नतीजा" to "क्या बदला? हो सके तो कोई नंबर दीजिए, और एक सीख।",
            ),
            listOf(
                "इसका एक अच्छा उदाहरण … के समय का है।",
                "एक बार ऐसा हुआ कि …",
                "मुझे एक मौक़ा याद आता है, जब …",
            ),
        )
        GuideTrack.PUBLIC_SPEAKING -> Template(
            "हुक → तीन बातें → समापन",
            listOf(
                "हुक" to "एक सवाल, कोई चौंकाने वाली बात या छोटे किस्से से शुरू कीजिए।",
                "पहली बात" to "आपका पहला विचार, एक उदाहरण के साथ।",
                "दूसरी बात" to "दूसरा विचार, पहले से थोड़ा अलग।",
                "तीसरी बात" to "सबसे दमदार विचार, आख़िर के लिए बचाकर।",
                "समापन" to "हुक पर लौटिए और सुनने वालों को करने के लिए एक काम दीजिए।",
            ),
            listOf(
                "आप में से कितने लोगों ने कभी … किया है?",
                "एक आँकड़ा जिसने मुझे चौंका दिया: …",
                "ज़रा सोचिए: …",
            ),
        )
        GuideTrack.CONTENT_CREATION -> Template(
            "हुक → किस्सा → सीख → कॉल टू एक्शन",
            listOf(
                "हुक (3 सेकंड)" to "एक दमदार लाइन, जिस पर लोग स्क्रॉल करना रोक दें।",
                "किस्सा" to "एक छोटा, असली पल: क्या हुआ, तेज़ी से बताइए।",
                "सीख" to "एक बात जो देखने वाला आज ही काम में ले सके।",
                "कॉल टू एक्शन" to "बताइए आगे क्या करें: आज़माएँ, कमेंट करें या फ़ॉलो करें।",
            ),
            listOf(
                "… के बारे में यह बात कोई नहीं बताता।",
                "मैंने … आज़माया, ताकि आपको न आज़माना पड़े।",
                "… करना बंद कीजिए। वजह सुनिए।",
            ),
        )
        GuideTrack.EVERYDAY -> Template(
            "क्या हुआ → कैसा लगा → आगे क्या",
            listOf(
                "क्या हुआ" to "क्रम से बताइए: पहले माहौल, फिर वह पल।",
                "कैसा लगा" to "उस एहसास का नाम लीजिए, और बताइए कि ऐसा क्यों लगा।",
                "आगे क्या" to "अब आप क्या करेंगे, या किसी दोस्त को क्या सलाह देंगे।",
            ),
            listOf(
                "अभी कुछ दिन पहले की बात है …",
                "आप यक़ीन नहीं करेंगे कि जब … तो क्या हुआ।",
                "आजकल मेरे मन में … की बात चल रही है।",
            ),
        )
    }
}
