package io.github.ukpratik.mukhar.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Room schema v1, exactly the LLD §2.1 table. Times are epoch ms; enums are stored as their lowercase keys.

@Entity(
    tableName = "talk_session",
    indices = [Index("status"), Index("created_at"), Index("parent_id")],
)
data class TalkSessionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    val lang: String,
    val track: String,
    @ColumnInfo(name = "prompt_id") val promptId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Int,
    @ColumnInfo(name = "speech_ms") val speechMs: Int,
    @ColumnInfo(name = "snr_db") val snrDb: Double,
    val status: String,
    @ColumnInfo(name = "lease_until") val leaseUntil: Long?,
    val attempts: Int,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "audio_path") val audioPath: String?,
    @ColumnInfo(name = "audio_kept") val audioKept: Boolean,
    @ColumnInfo(name = "server_session_id") val serverSessionId: String?,
    @ColumnInfo(name = "quota_day") val quotaDay: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "transcript",
    foreignKeys = [ForeignKey(TalkSessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
)
data class TranscriptEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "tokens_json") val tokensJson: String,
    val provider: String,
    val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "metrics",
    foreignKeys = [ForeignKey(TalkSessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
)
data class MetricsEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    val wpm: Double,
    @ColumnInfo(name = "wpm_band") val wpmBand: String,
    @ColumnInfo(name = "lexical_tokens") val lexicalTokens: Int,
    @ColumnInfo(name = "filler_count") val fillerCount: Int,
    @ColumnInfo(name = "fillers_per_min") val fillersPerMin: Double,
    /** Null when stutter-friendly mode was on (not scored). */
    @ColumnInfo(name = "long_pauses") val longPauses: Int?,
    @ColumnInfo(name = "long_pauses_per_min") val longPausesPerMin: Double?,
    val repetitions: Int?,
    val mattr: Double?,
    @ColumnInfo(name = "span_ms") val spanMs: Int,
    @ColumnInfo(name = "algo_version") val algoVersion: Int,
)

@Entity(
    tableName = "feedback",
    foreignKeys = [ForeignKey(TalkSessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
)
data class FeedbackEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    val json: String,
    @ColumnInfo(name = "prompt_version") val promptVersion: String,
    val model: String,
    val dropped: Int,
    @ColumnInfo(name = "fallback_glow") val fallbackGlow: Boolean,
    @ColumnInfo(name = "fallback_grow") val fallbackGrow: Boolean,
    val safety: String,
    val rating: Int?,
    val reported: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "word")
data class WordEntity(
    @PrimaryKey val id: String,
    val lang: String,
    val lemma: String,
    val meaning: String,
    val example: String,
    val register: String,
    val tracks: String,
    val source: String,
    @ColumnInfo(name = "source_session_id") val sourceSessionId: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "card", indices = [Index("due"), Index("word_id")])
data class CardEntity(
    @PrimaryKey val id: String,
    val type: String,
    @ColumnInfo(name = "word_id") val wordId: String?,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    val front: String,
    val back: String,
    val lang: String,
    val due: Long,
    val stability: Double?,
    val difficulty: Double?,
    @ColumnInfo(name = "elapsed_days") val elapsedDays: Int,
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Int,
    val reps: Int,
    val lapses: Int,
    val state: Int,
    val step: Int?,
    @ColumnInfo(name = "last_review") val lastReview: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val suspended: Boolean,
)

@Entity(tableName = "review_log", indices = [Index("card_id")])
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: String,
    val rating: Int,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    @ColumnInfo(name = "state_before") val stateBefore: Int,
    @ColumnInfo(name = "stability_after") val stabilityAfter: Double,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
)

/** A credited (or freeze/repair-covered) streak day, `YYYY-MM-DD`. Stored, never recomputed (US-031). */
@Entity(tableName = "streak_day")
data class StreakDayEntity(
    @PrimaryKey val day: String,
    val source: String,
    @ColumnInfo(name = "credited_at") val creditedAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
)

@Entity(tableName = "habit_state")
data class HabitStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "freezes_held") val freezesHeld: Int,
    @ColumnInfo(name = "last_repair_day") val lastRepairDay: String?,
    @ColumnInfo(name = "weekly_goal") val weeklyGoal: Int,
    @ColumnInfo(name = "last_freeze_award_streak") val lastFreezeAwardStreak: Int,
)

@Entity(tableName = "reminder_log", indices = [Index("scheduled_for")])
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scheduled_for") val scheduledFor: Long,
    @ColumnInfo(name = "shown_at") val shownAt: Long?,
    @ColumnInfo(name = "opened_at") val openedAt: Long?,
    val outcome: String,
)

@Entity(tableName = "prompt_history", indices = [Index("shown_at")])
data class PromptHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "prompt_id") val promptId: String,
    @ColumnInfo(name = "shown_at") val shownAt: Long,
)

@Entity(tableName = "daily_word", primaryKeys = ["day", "word_id"])
data class DailyWordEntity(
    val day: String,
    @ColumnInfo(name = "word_id") val wordId: String,
    val position: Int,
)

/** US-081 (schema v2): one row per word the user decided in the new word deck. [choice] is a `DeckChoice` key. */
@Entity(tableName = "word_deck", indices = [Index("seq")])
data class WordDeckEntity(
    @PrimaryKey @ColumnInfo(name = "word_id") val wordId: String,
    val lang: String,
    val choice: String,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
    val seq: Long,
)

@Entity(tableName = "insight")
data class InsightEntity(
    @PrimaryKey @ColumnInfo(name = "rule_key") val ruleKey: String,
    @ColumnInfo(name = "achieved_at") val achievedAt: Long,
    val seen: Boolean,
)
