package io.github.ukpratik.mukhar.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `mukhar.db` v2 (LLD §2.1; v2 adds `word_deck`, US-081). Migrations: additive AutoMigration where possible, hand-written otherwise, each with a
 * MigrationTestHelper test. Never `fallbackToDestructiveMigration`.
 */
@Database(
    version = 2,
    exportSchema = true,
    entities = [
        TalkSessionEntity::class, TranscriptEntity::class, MetricsEntity::class, FeedbackEntity::class,
        WordEntity::class, CardEntity::class, ReviewLogEntity::class, StreakDayEntity::class, HabitStateEntity::class,
        ReminderLogEntity::class, PromptHistoryEntity::class, DailyWordEntity::class, InsightEntity::class, WordDeckEntity::class,
    ],
)
abstract class MukharDatabase : RoomDatabase() {
    abstract fun talkDao(): TalkDao
    abstract fun wordDao(): WordDao
    abstract fun habitDao(): HabitDao
    abstract fun progressDao(): ProgressDao

    companion object {
        const val NAME = "mukhar.db"

        /**
         * v1 (1.0.0) → v2: the new word deck (US-081). Curated words already in the Word Gym were being learnt, so
         * they're marked done (seq 0) and never come back in the deck.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `word_deck` (`word_id` TEXT NOT NULL, `lang` TEXT NOT NULL, `choice` TEXT NOT NULL, " +
                        "`decided_at` INTEGER NOT NULL, `seq` INTEGER NOT NULL, PRIMARY KEY(`word_id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_deck_seq` ON `word_deck` (`seq`)")
                db.execSQL("INSERT OR IGNORE INTO word_deck (word_id, lang, choice, decided_at, seq) SELECT id, lang, 'done', added_at, 0 FROM word WHERE source = 'curated'")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
