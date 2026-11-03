package io.github.ukpratik.mukhar.app.feature.words

import io.github.ukpratik.mukhar.app.feature.words.review.DoneUi
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewUiState
import io.github.ukpratik.mukhar.app.feature.words.review.SentencePhase
import io.github.ukpratik.mukhar.app.feature.words.review.SentenceUi
import io.github.ukpratik.mukhar.data.repo.CheckQuota
import io.github.ukpratik.mukhar.data.repo.ReviewCard
import io.github.ukpratik.mukhar.data.repo.ReviewMode
import io.github.ukpratik.mukhar.data.repo.SentenceCheck
import io.github.ukpratik.mukhar.data.repo.WordItem
import io.github.ukpratik.mukhar.data.repo.WordSource
import io.github.ukpratik.mukhar.data.repo.WordStatus
import io.github.ukpratik.mukhar.domain.fsrs.Card
import io.github.ukpratik.mukhar.domain.fsrs.Rating
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.revise.CardType
import io.github.ukpratik.mukhar.domain.words.SentenceVerdict
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** S13 to S15 fixtures, copy from the final designs. */
object WordsFixtures {
    val at: Instant = Instant.parse("2026-11-06T03:00:00Z")

    fun word(
        lemma: String,
        meaning: String,
        status: WordStatus = WordStatus.LEARNING,
        source: WordSource = WordSource.TALK,
        lang: Lang = Lang.EN,
        pos: String = "adjective",
        register: String = "power",
        gloss: String? = "निर्णायक, सबसे अहम",
        example: String = "The client call was pivotal: it decided our whole quarter.",
        tracks: List<String> = listOf("professional"),
        releaseDay: LocalDate? = null,
        translit: String? = null,
        exampleGloss: String? = null,
    ) = WordItem(
        id = "${lang.code}:$lemma", lang = lang, lemma = lemma, meaning = meaning, example = example, register = register,
        tracks = tracks, source = source, sourceSessionId = if (source == WordSource.TALK) "talk-1" else null, addedAt = at,
        pos = pos, gloss = gloss, translit = translit, exampleGloss = exampleGloss, status = status, releaseDay = releaseDay,
        nextReview = at.plus(Duration.ofDays(4)), inGym = true,
    )

    val pivotal = word("pivotal", "Of central importance.")
    val articulate = word("articulate", "To say an idea clearly so others understand it.", pos = "verb", gloss = "साफ़-साफ़ शब्दों में कहना", example = "She articulated the risk in one line.")
    val undocumented = word("undocumented", "Not written down anywhere.", status = WordStatus.NEW, register = "formal")
    val concise = word("concise", "Short and clear, with no extra words.", status = WordStatus.KNOWN, source = WordSource.CURATED, register = "formal", gloss = "कम शब्दों में, साफ़", example = "Keep the client update concise: three points, one ask.", tracks = listOf("interviews"))
    val streamline = word("streamline", "Make a process simpler and faster.", status = WordStatus.SAVED_FOR_LATER, pos = "verb", releaseDay = LocalDate.of(2026, 11, 8))
    val sateek = word(
        "सटीक", "बिल्कुल सही, ज़रा भी ग़लती नहीं", lang = Lang.HI, register = "formal", gloss = "Exact; precise.",
        example = "मुझे इस रिपोर्ट के सटीक आँकड़े चाहिए।", translit = "sateek", exampleGloss = "I need the exact figures for this report.",
    )

    private fun card(id: String, type: CardType, front: String, back: String, word: WordItem?) =
        ReviewCard(id, type, word?.lang ?: Lang.EN, front, back, word, "talk-1", at, Card(id, at))

    val articulateCard = card("wm:en:articulate", CardType.WORD_MEANING, "articulate", articulate.meaning, articulate)
    val fixCard = card("fix:talk-1:0", CardType.MISTAKE_FIX, "Let's discuss about the budget on Monday.", "Let's discuss the budget on Monday.", null)
    val lineCard = card("line:talk-1:2", CardType.SAVED_LINE, "the project went live two weeks before deadline", "We launched two weeks ahead of schedule.", null)

    val intervals = mapOf(
        Rating.AGAIN to Duration.ofSeconds(50),
        Rating.HARD to Duration.ofDays(2),
        Rating.GOOD to Duration.ofDays(4),
        Rating.EASY to Duration.ofDays(9),
    )

    private val cards = listOf(card("a", CardType.WORD_MEANING, "x", "y", concise), card("b", CardType.WORD_MEANING, "x", "y", pivotal), card("c", CardType.WORD_MEANING, "x", "y", pivotal), articulateCard) +
        (5..12).map { card("w$it", CardType.WORD_MEANING, "x", "y", pivotal) }

    /** S13 state 1: card 4 of 12. */
    val front = ReviewUiState(loading = false, cards = cards, index = 3, intervals = intervals, voices = mapOf(Lang.EN to true))
    val back = front.copy(flipped = true)

    private fun check(verdict: SentenceVerdict, transcript: String, corrected: String = "", example: String = "", note: String, used: Int = 3) =
        SentenceCheck(verdict, transcript, corrected, example, note, CheckQuota(used, 10))

    val ok = back.copy(
        sentence = SentenceUi(
            SentencePhase.RESULT,
            result = check(SentenceVerdict.NICELY_USED, "In the review, I articulated why the launch slipped by a week.", note = "Right meaning, natural grammar. That's a sentence you could say in a real meeting."),
        ),
    )
    val almost = back.copy(
        sentence = SentenceUi(
            SentencePhase.RESULT,
            result = check(
                SentenceVerdict.ALMOST, "I will articulate about the plan in tomorrow meeting.",
                corrected = "I will articulate the plan in tomorrow's meeting.",
                note = "Good use of the word. \"Articulate\" takes the thing directly, no \"about\".",
            ),
        ),
    )
    val notYet = back.copy(
        sentence = SentenceUi(
            SentencePhase.RESULT,
            result = check(
                SentenceVerdict.NOT_YET, "I pivotal the meeting to Thursday because the client was busy.",
                example = "That client call was pivotal: it decided the whole quarter.",
                note = "\"Pivotal\" describes something very important. It isn't an action.",
            ),
        ),
    )
    val offline = back.copy(online = false)
    val checksDone = back.copy(quota = CheckQuota(10, 10))
    val talksOff = back.copy(talksEnabled = false)

    val reviseFix = ReviewUiState(loading = false, mode = ReviewMode.REVISE, cards = listOf(fixCard, lineCard), index = 0, intervals = intervals, voices = mapOf(Lang.EN to true))
    val reviseLine = reviseFix.copy(index = 1, flipped = true)

    /** S15 state 4: 12 cards in 2 min 40 s, counts for today. */
    val done = ReviewUiState(
        loading = false, mode = ReviewMode.REVISE,
        done = DoneUi(cards = 12, elapsedMs = 160_000, words = 7, fixes = 3, lines = 2, streakDay = 14, dueTomorrow = 6, more = 4),
    )
}
