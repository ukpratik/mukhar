package io.github.ukpratik.mukhar.app.feature.today

import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.domain.model.Lang
import java.time.LocalDate

/** A talk prompt for the Today card. */
data class TalkPrompt(val id: String, val lang: Lang, val track: GoalTrack, val text: String)

/**
 * A handful of starter prompts so Today's Talk works before the M4 content packs (≥ 150 prompts per language,
 * `prompts.json`) land. One per track and language; the first talk is the interview-style intro (S07 state 1).
 */
object StarterPrompts {
    private val EN = listOf(
        TalkPrompt("en-int-1", Lang.EN, GoalTrack.INTERVIEWS, "Introduce yourself as you would at the start of an interview."),
        TalkPrompt("en-pro-1", Lang.EN, GoalTrack.PROFESSIONAL, "Tell us about a project you're proud of, and what you'd do differently next time."),
        TalkPrompt("en-pub-1", Lang.EN, GoalTrack.PUBLIC_SPEAKING, "What would you change about your city's traffic?"),
        TalkPrompt("en-cre-1", Lang.EN, GoalTrack.CONTENT_CREATION, "Explain what you do at work to someone who has never had a job."),
        TalkPrompt("en-wrd-1", Lang.EN, GoalTrack.WORD_POWER, "Describe a teacher who changed how you think."),
        TalkPrompt("en-eve-1", Lang.EN, GoalTrack.EVERYDAY, "Describe a small habit that made your week better."),
    )
    private val HI = listOf(
        TalkPrompt("hi-int-1", Lang.HI, GoalTrack.INTERVIEWS, "अपने बारे में बताइए, जैसे किसी इंटरव्यू की शुरुआत में बताते हैं।"),
        TalkPrompt("hi-pro-1", Lang.HI, GoalTrack.PROFESSIONAL, "किसी ऐसे काम के बारे में बताइए जिस पर आपको गर्व है।"),
        TalkPrompt("hi-pub-1", Lang.HI, GoalTrack.PUBLIC_SPEAKING, "आप अपने शहर के ट्रैफ़िक में क्या बदलना चाहेंगे?"),
        TalkPrompt("hi-cre-1", Lang.HI, GoalTrack.CONTENT_CREATION, "अपना काम किसी ऐसे व्यक्ति को समझाइए जिसने कभी नौकरी नहीं की।"),
        TalkPrompt("hi-wrd-1", Lang.HI, GoalTrack.WORD_POWER, "किसी ऐसे शिक्षक के बारे में बताइए जिसने आपकी सोच बदली।"),
        TalkPrompt("hi-eve-1", Lang.HI, GoalTrack.EVERYDAY, "किसी छोटी आदत के बारे में बताइए जिसने आपका हफ़्ता बेहतर बनाया।"),
    )

    /** Deterministic per day; [shuffle] (the corner button) steps through the user's tracks and languages. */
    fun pick(langs: Set<Lang>, tracks: Set<GoalTrack>, day: LocalDate, shuffle: Int, firstTalk: Boolean): TalkPrompt {
        val pool = (if (Lang.HI in langs) HI else emptyList()) + (if (Lang.EN in langs || langs.isEmpty()) EN else emptyList())
        if (firstTalk && shuffle == 0) {
            val intro = if (Lang.EN in langs || langs.isEmpty()) EN.first() else HI.first()
            return intro
        }
        val mine = pool.filter { tracks.isEmpty() || it.track in tracks }.ifEmpty { pool }
        val i = Math.floorMod(day.toEpochDay() + shuffle, mine.size.toLong()).toInt()
        return mine[i]
    }
}
