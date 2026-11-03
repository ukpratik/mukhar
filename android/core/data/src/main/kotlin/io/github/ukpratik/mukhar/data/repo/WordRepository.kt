package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.content.CuratedWord
import io.github.ukpratik.mukhar.data.content.WordCatalog
import io.github.ukpratik.mukhar.data.db.CardEntity
import io.github.ukpratik.mukhar.data.db.DailyWordEntity
import io.github.ukpratik.mukhar.data.db.ReviewLogEntity
import io.github.ukpratik.mukhar.data.db.WordDao
import io.github.ukpratik.mukhar.data.db.WordEntity
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.feedback.Mistake
import io.github.ukpratik.mukhar.domain.feedback.UpgradedWord
import io.github.ukpratik.mukhar.domain.fsrs.Card
import io.github.ukpratik.mukhar.domain.fsrs.CardState
import io.github.ukpratik.mukhar.domain.fsrs.Fsrs
import io.github.ukpratik.mukhar.domain.fsrs.Rating
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.revise.CardType
import io.github.ukpratik.mukhar.domain.revise.QueueBuilder
import io.github.ukpratik.mukhar.domain.revise.QueueItem
import io.github.ukpratik.mukhar.domain.streak.Activity
import io.github.ukpratik.mukhar.domain.streak.CUTOFF_HOUR
import io.github.ukpratik.mukhar.domain.streak.nextCutoff
import io.github.ukpratik.mukhar.domain.streak.streakDay
import io.github.ukpratik.mukhar.domain.text.Normalizer
import io.github.ukpratik.mukhar.domain.words.DailyWordPicker
import io.github.ukpratik.mukhar.domain.words.HarvestPolicy
import io.github.ukpratik.mukhar.domain.words.WordCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** S14 status chip. [SAVED_FOR_LATER] = harvested over the daily cap, waiting for its day (US-021). */
enum class WordStatus { NEW, LEARNING, KNOWN, SAVED_FOR_LATER }

enum class WordSource(val key: String) { CURATED("curated"), TALK("talk") }

/** A word in the bank, its curated extras (part of speech, gloss, transliteration) and its review state. */
data class WordItem(
    val id: String,
    val lang: Lang,
    val lemma: String,
    val meaning: String,
    val example: String,
    val register: String,
    val tracks: List<String>,
    val source: WordSource,
    val sourceSessionId: String?,
    val addedAt: Instant,
    val pos: String?,
    val gloss: String?,
    val translit: String?,
    val exampleGloss: String?,
    val status: WordStatus,
    /** When a word saved over the daily cap enters the queue. */
    val releaseDay: LocalDate?,
    val nextReview: Instant?,
    val inGym: Boolean,
)

/** One card in a review session (S13, S15). [word] is set for word cards. */
data class ReviewCard(
    val id: String,
    val type: CardType,
    val lang: Lang,
    val front: String,
    val back: String,
    val word: WordItem?,
    val sessionId: String?,
    val createdAt: Instant,
    val fsrs: Card,
)

sealed interface HarvestResult {
    /** Added to the Word Gym; [releaseDay] is today unless the daily cap was reached (then it waits). */
    data class Added(val releaseDay: LocalDate, val carriedOver: Boolean) : HarvestResult
    data object AlreadyInGym : HarvestResult
}

enum class ReviewMode { GYM, REVISE }

/**
 * Words, cards and reviews (US-020 to US-023, US-040; LLD §2.1, §4.5). Curated words come from [WordCatalog];
 * harvested ones from a talk's better versions. Each word gets a word → meaning card when it's released and a
 * meaning → word card a day later. New cards carry their release time as `due`, so the daily cap of harvested
 * words is just a later `due`. Scheduling is the domain [Fsrs]; every grade writes a `review_log` row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WordRepository(
    private val dao: WordDao,
    private val catalog: WordCatalog,
    private val settings: SettingsStore,
    private val habits: HabitRepository,
    private val clock: Clock,
    private val fsrs: Fsrs = Fsrs(),
) {
    private val mutex = Mutex()

    // ---- Daily words (US-020) ----

    fun today(): LocalDate = streakDay(clock.now(), clock.zone())

    /** Picks and persists the day's words once; later calls on the same streak day return the same words. */
    suspend fun ensureDailyWords(day: LocalDate = today()): List<String> = mutex.withLock {
        dao.dailyWords(day.toString()).takeIf { it.isNotEmpty() }?.let { rows -> return@withLock rows.map { it.wordId } }
        val s = settings.current()
        val langs = Lang.entries.filter { it in s.langs }.ifEmpty { listOf(Lang.EN) }
        val tracks = s.tracks.map { it.key }
        val endOfDay = dayStart(day.plusDays(1)).toEpochMilli()
        val talk = dao.talkWordsForDaily(endOfDay).filter { w -> langs.any { it.code == w.lang } }
            .map { WordCandidate(it.id, it.lang, it.tracks.split(',').toSet()) }
        val known = dao.allWordIds().toSet()
        val curated = langs.flatMap { catalog.words(it) }.filter { it.id !in known }
        val picked = DailyWordPicker.pick(s.dailyNewWords, talk, curated.map { WordCandidate(it.id, it.lang.code, it.tracks.toSet()) }, langs.map { it.code }, tracks, day)
        val byId = curated.associateBy { it.id }
        val now = clock.now()
        picked.forEach { c -> byId[c.id]?.let { addCurated(it, now) } }
        dao.upsertDailyWords(picked.mapIndexed { i, c -> DailyWordEntity(day.toString(), c.id, i) })
        picked.map { it.id }
    }

    /** Today's words for S07 (picked on first read of the day). */
    fun observeDailyWords(day: LocalDate): Flow<List<WordItem>> = flow {
        ensureDailyWords(day)
        emit(Unit)
    }.flatMapLatest {
        combine(dao.observeDailyWords(day.toString()), observeBank(includeRemoved = true)) { rows, bank ->
            val byId = bank.associateBy { it.id }
            rows.mapNotNull { byId[it.wordId] }
        }
    }

    private suspend fun addCurated(w: CuratedWord, now: Instant) {
        dao.insertWord(
            WordEntity(
                id = w.id, lang = w.lang.code, lemma = w.lemma, meaning = w.meaning, example = w.example, register = w.register,
                tracks = w.tracks.joinToString(","), source = WordSource.CURATED.key, sourceSessionId = null, addedAt = now.toEpochMilli(),
            ),
        )
        addWordCards(w.id, w.lang, w.lemma, w.meaning, release = now)
    }

    /** `created_at` is the release time, so new cards are introduced in release order (QueueBuilder sorts by it). */
    private suspend fun addWordCards(wordId: String, lang: Lang, lemma: String, meaning: String, release: Instant) {
        val at = release.toEpochMilli()
        dao.insertCard(newCard("wm:$wordId", CardType.WORD_MEANING, wordId, null, lemma, meaning, lang, at, at))
        // Meaning → word comes the day after the word is introduced, so the two directions aren't drilled back to back;
        // a word introduced that same morning goes first (+1 ms).
        val next = nextCutoff(release, clock.zone()).toEpochMilli()
        dao.insertCard(newCard("mw:$wordId", CardType.MEANING_WORD, wordId, null, meaning, lemma, lang, next, next + 1))
    }

    // ---- Harvest from talks (US-021, US-040) ----

    /** A tapped upgraded word: deduplicated, at most 5 new a day, the rest carried to the next day with room. */
    suspend fun harvest(sessionId: String, lang: Lang, track: String, word: UpgradedWord, example: String): HarvestResult = mutex.withLock {
        val id = wordId(lang, word.word)
        val existing = dao.word(id)
        if (existing != null) {
            dao.setWordSuspended(id, false)
            return@withLock HarvestResult.AlreadyInGym
        }
        val today = today()
        val counts = dao.talkReleaseTimes(dayStart(today).toEpochMilli())
            .groupingBy { streakDay(Instant.ofEpochMilli(it), clock.zone()) }.eachCount()
        val day = HarvestPolicy.releaseDay(today, counts)
        val now = clock.now()
        val release = if (day == today) now else dayStart(day)
        val meaning = word.meaning.ifBlank { catalog.byId(id)?.meaning.orEmpty() }
        dao.insertWord(
            WordEntity(
                id = id, lang = lang.code, lemma = word.word.trim(), meaning = meaning, example = example, register = word.register.key,
                tracks = track, source = WordSource.TALK.key, sourceSessionId = sessionId, addedAt = now.toEpochMilli(),
            ),
        )
        addWordCards(id, lang, word.word.trim(), meaning, release)
        HarvestResult.Added(day, carriedOver = day != today)
    }

    /** Undo on Results: removes a word this talk added (and its cards). Curated words stay. */
    suspend fun unharvest(sessionId: String, lang: Lang, word: String) = mutex.withLock {
        val id = wordId(lang, word)
        val w = dao.word(id) ?: return@withLock
        if (w.source != WordSource.TALK.key || w.sourceSessionId != sessionId) return@withLock
        dao.deleteCardsForWord(id)
        dao.deleteWord(id)
    }

    /** Mistake → fix cards from the coach's notes (US-022): "You said *discuss about*" → "discuss". Idempotent. */
    suspend fun harvestMistakes(sessionId: String, lang: Lang, mistakes: List<Mistake>) {
        val now = clock.now().toEpochMilli()
        mistakes.forEachIndexed { i, m ->
            if (m.quote.isBlank() || m.fix.isBlank() || Normalizer.norm(m.quote) == Normalizer.norm(m.fix)) return@forEachIndexed
            dao.insertCard(newCard("fix:$sessionId:$i", CardType.MISTAKE_FIX, null, sessionId, m.quote, m.fix, lang, now, now))
        }
    }

    // ---- Word bank (S14) ----

    fun observeBank(includeRemoved: Boolean = false): Flow<List<WordItem>> = combine(dao.observeWords(), dao.observeCards()) { words, cards ->
        val byWord = cards.filter { it.wordId != null }.groupBy { it.wordId!! }
        val now = clock.now()
        words.mapNotNull { w -> item(w, byWord[w.id].orEmpty(), now).takeIf { includeRemoved || it.inGym } }
            // Words from the user's talks first, newest first within a group (S14 notes).
            .sortedWith(compareBy<WordItem> { it.source != WordSource.TALK }.thenByDescending { it.addedAt })
    }

    fun observeWord(id: String): Flow<WordItem?> = combine(dao.observeWord(id), dao.observeCardsForWord(id)) { w, cards ->
        w?.let { item(it, cards, clock.now()) }
    }

    fun observeSavedLines(): Flow<List<CardEntity>> = dao.observeCards().map { cards ->
        cards.filter { it.type == CardType.SAVED_LINE.key }.sortedByDescending { it.createdAt }
    }

    /** Word cards due before the next 03:00 (the Word Gym row on S14). */
    fun observeGymDue(): Flow<Int> = dao.observeCards().map { cards ->
        val cutoff = nextCutoff(clock.now(), clock.zone()).toEpochMilli()
        cards.count { !it.suspended && it.wordId != null && it.due < cutoff }.coerceAtMost(GYM_MAX)
    }

    /** "Remove from Word Gym" suspends the word's cards (Undo restores them, S14 notes). */
    suspend fun setInGym(wordId: String, inGym: Boolean) = dao.setWordSuspended(wordId, !inGym)

    private suspend fun item(w: WordEntity, cards: List<CardEntity>, now: Instant): WordItem {
        val curated = catalog.byId(w.id)
        val wm = cards.firstOrNull { it.type == CardType.WORD_MEANING.key }
        val active = cards.filter { !it.suspended }
        val waiting = wm != null && wm.state == CardState.NEW.value && wm.due > now.toEpochMilli()
        val status = when {
            wm == null || wm.state == CardState.NEW.value -> if (waiting) WordStatus.SAVED_FOR_LATER else WordStatus.NEW
            wm.state == CardState.REVIEW.value && wm.scheduledDays >= KNOWN_DAYS -> WordStatus.KNOWN
            else -> WordStatus.LEARNING
        }
        return WordItem(
            id = w.id, lang = Lang.fromCode(w.lang), lemma = w.lemma, meaning = w.meaning, example = w.example, register = w.register,
            tracks = w.tracks.split(',').filter(String::isNotEmpty), source = if (w.source == WordSource.TALK.key) WordSource.TALK else WordSource.CURATED,
            sourceSessionId = w.sourceSessionId, addedAt = Instant.ofEpochMilli(w.addedAt), pos = curated?.pos, gloss = curated?.gloss,
            translit = curated?.translit, exampleGloss = curated?.exampleGloss, status = status,
            releaseDay = if (waiting) streakDay(Instant.ofEpochMilli(wm!!.due), clock.zone()) else null,
            nextReview = active.filter { it.state != CardState.NEW.value }.minOfOrNull { it.due }?.let(Instant::ofEpochMilli),
            inGym = active.isNotEmpty() || cards.isEmpty(),
        )
    }

    // ---- Review (US-022, US-040) ----

    /**
     * The session queue via [QueueBuilder]: due before the next 03:00, oldest first, new cards capped per day, types
     * interleaved. Word Gym (S13) takes word cards only; quick revision (S15) mixes words, fixes and saved lines,
     * ≤ 15 items. [focusWordId] reviews one word now (S14 "Practise in a sentence").
     */
    suspend fun queue(mode: ReviewMode, focusWordId: String? = null): List<ReviewCard> {
        val now = clock.now()
        if (focusWordId != null) {
            return dao.cardsForWord(focusWordId).filter { it.type == CardType.WORD_MEANING.key }.map { toReview(it) }
        }
        val cutoff = nextCutoff(now, clock.zone())
        val types = if (mode == ReviewMode.GYM) WORD_TYPES else CardType.entries.toSet()
        val items = dao.reviewCandidates(cutoff.toEpochMilli()).mapNotNull { c ->
            val type = CardType.entries.firstOrNull { it.key == c.type } ?: return@mapNotNull null
            if (type !in types) null else QueueItem(c.toDomain(), type, Instant.ofEpochMilli(c.createdAt), c.suspended) to c
        }
        val byId = items.associate { it.first.card.id to it.second }
        val introduced = dao.newIntroducedSince(dayStart(today()).toEpochMilli())
        val built = QueueBuilder.build(
            items.map { it.first }, cutoff,
            maxItems = if (mode == ReviewMode.GYM) GYM_MAX else QueueBuilder.MAX_ITEMS,
            newPerDay = NEW_CARDS_PER_DAY, newIntroducedToday = introduced, newCapTypes = WORD_TYPES,
        )
        return built.mapNotNull { byId[it.card.id]?.let { e -> toReview(e) } }
    }

    /** The next interval for each grade, shown on the four buttons (S13 state 2). */
    suspend fun preview(cardId: String): Map<Rating, Duration> {
        val c = dao.card(cardId) ?: return emptyMap()
        val now = clock.now()
        return fsrs.preview(c.toDomain(), now).mapValues { (_, card) -> Duration.between(now, card.due).coerceAtLeast(Duration.ZERO) }
    }

    /** FSRS review → card row + `review_log` (works fully offline). */
    suspend fun grade(cardId: String, rating: Rating, durationMs: Long?): Card? = mutex.withLock {
        val c = dao.card(cardId) ?: return@withLock null
        val (next, log) = fsrs.review(c.toDomain(), rating, clock.now(), durationMs)
        dao.upsertCard(c.withFsrs(next))
        dao.insertReviewLog(
            ReviewLogEntity(
                cardId = cardId, rating = rating.value, reviewedAt = log.reviewedAt.toEpochMilli(), stateBefore = log.stateBefore.value,
                stabilityAfter = log.stabilityAfter, durationMs = log.durationMs,
            ),
        )
        next
    }

    /** US-031: a review session of ≥ 5 cards credits the streak day (the 5th grade credits immediately, S15 notes). */
    suspend fun creditReview(cards: Int): CreditResult = habits.credit(Activity.Review(clock.now(), clock.zone(), cards))

    /** Cards due tomorrow (after this session), for "Next N cards due tomorrow" (S15 done). */
    suspend fun dueTomorrow(): Int {
        val cutoff = nextCutoff(clock.now(), clock.zone())
        val tomorrow = dao.reviewCandidates(cutoff.plus(Duration.ofDays(1)).toEpochMilli())
        return tomorrow.count { it.due >= cutoff.toEpochMilli() || it.state != CardState.NEW.value && it.due >= clock.now().toEpochMilli() }
    }

    private suspend fun toReview(c: CardEntity): ReviewCard {
        val type = CardType.entries.first { it.key == c.type }
        val word = c.wordId?.let { id -> dao.word(id)?.let { item(it, dao.cardsForWord(id), clock.now()) } }
        return ReviewCard(c.id, type, Lang.fromCode(c.lang), c.front, c.back, word, c.sessionId, Instant.ofEpochMilli(c.createdAt), c.toDomain())
    }

    private fun dayStart(day: LocalDate): Instant = day.atTime(CUTOFF_HOUR, 0).atZone(clock.zone()).toInstant()

    companion object {
        /** Word → meaning plus meaning → word (a day later) for up to 5 new words a day. */
        const val NEW_CARDS_PER_DAY = 2 * HarvestPolicy.DAILY_CAP
        const val GYM_MAX = 20
        /** A word is "Known" once its interval reaches 3 weeks. */
        const val KNOWN_DAYS = 21
        private val WORD_TYPES = setOf(CardType.WORD_MEANING, CardType.MEANING_WORD)

        fun wordId(lang: Lang, word: String): String = "${lang.code}:${Normalizer.norm(word)}"

        fun newCard(
            id: String, type: CardType, wordId: String?, sessionId: String?, front: String, back: String, lang: Lang, due: Long, created: Long,
        ) = CardEntity(
            id = id, type = type.key, wordId = wordId, sessionId = sessionId, front = front, back = back, lang = lang.code, due = due,
            stability = null, difficulty = null, elapsedDays = 0, scheduledDays = 0, reps = 0, lapses = 0, state = CardState.NEW.value,
            step = 0, lastReview = null, createdAt = created, suspended = false,
        )

        fun CardEntity.toDomain() = Card(
            id = id, due = Instant.ofEpochMilli(due), stability = stability, difficulty = difficulty, elapsedDays = elapsedDays,
            scheduledDays = scheduledDays, reps = reps, lapses = lapses, state = CardState.of(state), step = step,
            lastReview = lastReview?.let(Instant::ofEpochMilli),
        )

        fun CardEntity.withFsrs(c: Card) = copy(
            due = c.due.toEpochMilli(), stability = c.stability, difficulty = c.difficulty, elapsedDays = c.elapsedDays,
            scheduledDays = c.scheduledDays, reps = c.reps, lapses = c.lapses, state = c.state.value, step = c.step,
            lastReview = c.lastReview?.toEpochMilli(),
        )
    }
}
