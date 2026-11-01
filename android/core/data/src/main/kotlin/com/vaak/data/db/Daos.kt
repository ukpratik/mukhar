package com.vaak.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Talk sessions and their analysis results (US-010 to US-018). */
@Dao
interface TalkDao {
    @Insert
    suspend fun insertSession(session: TalkSessionEntity)

    @Query("SELECT * FROM talk_session WHERE id = :id")
    fun observeSession(id: String): Flow<TalkSessionEntity?>

    @Query("SELECT * FROM talk_session WHERE status IN (:statuses) ORDER BY created_at")
    suspend fun sessionsWithStatus(statuses: List<String>): List<TalkSessionEntity>

    @Query("UPDATE talk_session SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    /**
     * Claims the analysis lease (LLD §4.9). Returns 1 if this caller won, 0 if another path holds it.
     */
    @Query(
        "UPDATE talk_session SET status = 'uploading', lease_until = :now + :leaseMs, attempts = attempts + 1, updated_at = :now " +
            "WHERE id = :id AND (status IN ('recorded', 'queued', 'failed') OR (status = 'uploading' AND lease_until < :now))",
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

    @Query("SELECT prompt_id FROM prompt_history WHERE shown_at >= :since")
    suspend fun promptsShownSince(since: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInsight(insight: InsightEntity): Long

    @Query("SELECT rule_key FROM insight")
    suspend fun achievedInsightKeys(): List<String>
}
