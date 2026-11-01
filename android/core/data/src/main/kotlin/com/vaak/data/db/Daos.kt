package com.vaak.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Talk sessions and their analysis results (US-010 to US-018). */
@Dao
interface TalkDao {
    @Insert
    suspend fun insertSession(session: TalkSessionEntity)

    @Query("SELECT * FROM talk_session WHERE id = :id")
    fun observeSession(id: String): Flow<TalkSessionEntity?>

    @Query("SELECT * FROM talk_session WHERE id = :id")
    suspend fun session(id: String): TalkSessionEntity?

    @Update
    suspend fun updateSession(session: TalkSessionEntity)

    /** Persisted every few seconds while recording, so a crash keeps a useful duration and speech time (US-018). */
    @Query("UPDATE talk_session SET duration_ms = :durationMs, speech_ms = :speechMs, updated_at = :now WHERE id = :id AND status = 'recording'")
    suspend fun updateProgress(id: String, durationMs: Int, speechMs: Int, now: Long)

    /** The newest talk recovered after the app closed mid-recording that the user hasn't acted on (S09 state 8). */
    @Query("SELECT * FROM talk_session WHERE status = 'recorded' AND error_code = 'RECOVERED' ORDER BY created_at DESC LIMIT 1")
    fun observeRecovered(): Flow<TalkSessionEntity?>

    /** The latest re-take of [parentId] (US-017). */
    @Query("SELECT * FROM talk_session WHERE parent_id = :parentId AND kind = 'retake' AND status != 'discarded' ORDER BY created_at DESC LIMIT 1")
    suspend fun latestRetake(parentId: String): TalkSessionEntity?

    @Query("SELECT * FROM transcript WHERE session_id = :id")
    fun observeTranscript(id: String): Flow<TranscriptEntity?>

    @Query("SELECT * FROM transcript WHERE session_id = :id")
    suspend fun transcript(id: String): TranscriptEntity?

    @Query("SELECT * FROM metrics WHERE session_id = :id")
    fun observeMetrics(id: String): Flow<MetricsEntity?>

    @Query("SELECT * FROM metrics WHERE session_id = :id")
    suspend fun metrics(id: String): MetricsEntity?

    @Query("SELECT * FROM feedback WHERE session_id = :id")
    fun observeFeedback(id: String): Flow<FeedbackEntity?>

    @Query("SELECT * FROM feedback WHERE session_id = :id")
    suspend fun feedback(id: String): FeedbackEntity?

    @Query("UPDATE feedback SET rating = :rating WHERE session_id = :id")
    suspend fun rateFeedback(id: String, rating: Int)

    @Query("UPDATE feedback SET reported = 1 WHERE session_id = :id")
    suspend fun markReported(id: String)

    @Query("SELECT * FROM talk_session WHERE status IN (:statuses) ORDER BY created_at")
    suspend fun sessionsWithStatus(statuses: List<String>): List<TalkSessionEntity>

    @Query("UPDATE talk_session SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    /**
     * Claims the analysis lease (LLD §4.9). Returns 1 if this caller won, 0 if another path holds it.
     */
    @Query(
        "UPDATE talk_session SET status = 'uploading', lease_until = :now + :leaseMs, attempts = attempts + 1, updated_at = :now " +
            "WHERE id = :id AND (status IN ('recorded', 'queued', 'failed', 'transcribed', 'feedback_failed') " +
            "OR (status = 'uploading' AND lease_until < :now))",
    )
    suspend fun claimLease(id: String, now: Long, leaseMs: Long = 90_000): Int

    @Upsert
    suspend fun upsertTranscript(transcript: TranscriptEntity)

    @Upsert
    suspend fun upsertMetrics(metrics: MetricsEntity)

    @Upsert
    suspend fun upsertFeedback(feedback: FeedbackEntity)

    @Query(
        "SELECT m.* FROM metrics m JOIN talk_session s ON s.id = m.session_id " +
            "WHERE s.lang = :lang AND s.kind = 'talk' AND s.created_at < :before ORDER BY s.created_at DESC LIMIT :limit",
    )
    suspend fun recentMetrics(lang: String, before: Long, limit: Int = 5): List<MetricsEntity>
}

/** Words, FSRS cards and review history (US-020 to US-023, US-040). */
@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: WordEntity): Long

    @Upsert
    suspend fun upsertCard(card: CardEntity)

    @Query("SELECT * FROM card WHERE suspended = 0 AND (state = 0 OR due < :cutoff)")
    suspend fun reviewCandidates(cutoff: Long): List<CardEntity>

    @Query("SELECT COUNT(*) FROM card WHERE suspended = 0 AND state != 0 AND due < :cutoff")
    fun observeDueCount(cutoff: Long): Flow<Int>

    @Insert
    suspend fun insertReviewLog(log: ReviewLogEntity)

    @Query("DELETE FROM word WHERE id = :id AND source = 'talk' AND source_session_id = :sessionId")
    suspend fun deleteTalkWord(id: String, sessionId: String)

    /** Words collected from one talk's better versions (Word Gym itself is M4). */
    @Query("SELECT id FROM word WHERE source = 'talk' AND source_session_id = :sessionId")
    fun observeTalkWordIds(sessionId: String): Flow<List<String>>

    @Query("SELECT id FROM word WHERE source = 'talk' AND source_session_id = :sessionId")
    suspend fun talkWordIds(sessionId: String): List<String>

    @Query("SELECT id FROM card WHERE session_id = :sessionId AND type = 'saved_line'")
    suspend fun savedLineIds(sessionId: String): List<String>

    @Query("DELETE FROM card WHERE id = :id")
    suspend fun deleteCard(id: String)

    @Query("SELECT id FROM card WHERE session_id = :sessionId AND type = 'saved_line'")
    fun observeSavedLineIds(sessionId: String): Flow<List<String>>

    @Upsert
    suspend fun upsertDailyWords(words: List<DailyWordEntity>)

    @Query("SELECT * FROM daily_word WHERE day = :day ORDER BY position")
    fun observeDailyWords(day: String): Flow<List<DailyWordEntity>>
}

/** Streak, habit, reminders, prompts and insights (US-031 to US-033, US-050). */
@Dao
interface HabitDao {
    /** IGNORE: a stored day is never overwritten, so no day is credited twice (US-031). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStreakDay(day: StreakDayEntity): Long

    @Query("SELECT * FROM streak_day ORDER BY day")
    fun observeStreakDays(): Flow<List<StreakDayEntity>>

    @Query("SELECT * FROM streak_day ORDER BY day")
    suspend fun streakDays(): List<StreakDayEntity>

    @Query("SELECT * FROM habit_state WHERE id = 1")
    suspend fun habitState(): HabitStateEntity?

    @Upsert
    suspend fun upsertHabitState(state: HabitStateEntity)

    @Insert
    suspend fun insertReminderLog(entry: ReminderLogEntity): Long

    @Query("SELECT * FROM reminder_log ORDER BY scheduled_for DESC LIMIT :limit")
    suspend fun recentReminders(limit: Int = 30): List<ReminderLogEntity>

    @Insert
    suspend fun insertPromptHistory(entry: PromptHistoryEntity)

    /** Most recent first (US-010 no-repeat-of-last-30). */
    @Query("SELECT prompt_id FROM prompt_history ORDER BY shown_at DESC, id DESC LIMIT :limit")
    suspend fun recentPromptIds(limit: Int): List<String>

    @Query("SELECT prompt_id FROM prompt_history WHERE shown_at >= :since")
    suspend fun promptsShownSince(since: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInsight(insight: InsightEntity): Long

    @Query("SELECT rule_key FROM insight")
    suspend fun achievedInsightKeys(): List<String>

    // --- M3: habit and reminders ---

    @Query("SELECT * FROM habit_state WHERE id = 1")
    fun observeHabitState(): Flow<HabitStateEntity?>

    @Query("UPDATE reminder_log SET outcome = 'opened', opened_at = :at WHERE id = :id")
    suspend fun markReminderOpened(id: Long, at: Long)

    /** Finalises a shown reminder whose day is over without a tap or practice (US-033 "ignored"). */
    @Query("UPDATE reminder_log SET outcome = 'ignored' WHERE id = :id AND outcome = 'shown'")
    suspend fun markReminderIgnored(id: Long)

    /** Words learned, shown as kept on the fresh-start sheet (S20). */
    @Query("SELECT COUNT(*) FROM word")
    suspend fun wordCount(): Int
    // --- end M3 ---
}
