package com.vaak.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * `vaak.db` v1 (LLD §2.1). Migrations: additive AutoMigration where possible, hand-written otherwise, each with a
 * MigrationTestHelper test. Never `fallbackToDestructiveMigration`.
 */
@Database(
    version = 1,
    exportSchema = true,
    entities = [
        TalkSessionEntity::class, TranscriptEntity::class, MetricsEntity::class, FeedbackEntity::class,
        WordEntity::class, CardEntity::class, ReviewLogEntity::class, StreakDayEntity::class, HabitStateEntity::class,
        ReminderLogEntity::class, PromptHistoryEntity::class, DailyWordEntity::class, InsightEntity::class,
    ],
)
abstract class VaakDatabase : RoomDatabase() {
    abstract fun talkDao(): TalkDao
    abstract fun wordDao(): WordDao
    abstract fun habitDao(): HabitDao

    companion object {
        const val NAME = "vaak.db"
    }
}
