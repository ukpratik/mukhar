package com.vaak.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** An analysed talk with its metrics (S16, S17, S21). Numbers and ids only; no transcript. */
data class TalkMetricsRow(
    val id: String,
    val kind: String,
    val lang: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "speech_ms") val speechMs: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Int,
    @ColumnInfo(name = "audio_path") val audioPath: String?,
    @ColumnInfo(name = "audio_kept") val audioKept: Boolean,
    val wpm: Double,
    @ColumnInfo(name = "wpm_band") val wpmBand: String,
    @ColumnInfo(name = "fillers_per_min") val fillersPerMin: Double,
    @ColumnInfo(name = "long_pauses_per_min") val longPausesPerMin: Double?,
    val repetitions: Int?,
    val mattr: Double?,
    @ColumnInfo(name = "lexical_tokens") val lexicalTokens: Int,
)

/** M5 reads for Progress, insights and the quota mirror. Room schema unchanged (v1). */
@Dao
interface ProgressDao {
    @Query(
        "SELECT s.id, s.kind, s.lang, s.created_at, s.zone_id, s.speech_ms, s.duration_ms, s.audio_path, s.audio_kept, " +
            "m.wpm, m.wpm_band, m.fillers_per_min, m.long_pauses_per_min, m.repetitions, m.mattr, m.lexical_tokens " +
            "FROM talk_session s JOIN metrics m ON m.session_id = s.id " +
            "WHERE s.kind IN ('talk', 'retake', 'baseline') AND s.status != 'discarded' ORDER BY s.created_at",
    )
    fun observeAnalysedTalks(): Flow<List<TalkMetricsRow>>

    @Query(
        "SELECT s.id, s.kind, s.lang, s.created_at, s.zone_id, s.speech_ms, s.duration_ms, s.audio_path, s.audio_kept, " +
            "m.wpm, m.wpm_band, m.fillers_per_min, m.long_pauses_per_min, m.repetitions, m.mattr, m.lexical_tokens " +
            "FROM talk_session s JOIN metrics m ON m.session_id = s.id " +
            "WHERE s.kind IN ('talk', 'retake', 'baseline') AND s.status != 'discarded' ORDER BY s.created_at",
    )
    suspend fun analysedTalks(): List<TalkMetricsRow>

    @Query("SELECT * FROM insight ORDER BY achieved_at")
    fun observeInsights(): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insight ORDER BY achieved_at")
    suspend fun insights(): List<InsightEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInsight(insight: InsightEntity): Long

    @Query("UPDATE insight SET seen = 1 WHERE rule_key = :key")
    suspend fun markInsightSeen(key: String)

    @Query("SELECT COUNT(*) FROM word")
    fun observeWordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM word")
    suspend fun wordCount(): Int

    /**
     * Coached talks recorded in [fromMs, toMs) that count against the daily quota (the client mirror, US-060):
     * not discarded and not refused for being too short or too long.
     */
    @Query(
        "SELECT COUNT(*) FROM talk_session WHERE kind = 'talk' AND created_at >= :fromMs AND created_at < :toMs " +
            "AND status != 'discarded' AND (error_code IS NULL OR error_code NOT IN ('TOO_LITTLE_SPEECH', 'AUDIO_TOO_LONG'))",
    )
    suspend fun talksBetween(fromMs: Long, toMs: Long): Int

    /** A talk the server refused with QUOTA_EXHAUSTED since [sinceMs] (it waits for the next quota day). */
    @Query("SELECT COUNT(*) FROM talk_session WHERE status = 'queued' AND error_code = 'QUOTA_EXHAUSTED' AND updated_at >= :sinceMs")
    suspend fun quotaRefusedSince(sinceMs: Long): Int

    @Query("SELECT * FROM talk_session WHERE kind = 'baseline' AND audio_kept = 1")
    suspend fun keptBaselines(): List<TalkSessionEntity>

    @Query("SELECT COUNT(*) FROM talk_session WHERE kind IN ('talk', 'retake') AND status != 'discarded'")
    suspend fun talkCount(): Int
}
