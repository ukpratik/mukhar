package io.github.ukpratik.mukhar.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1 (1.0.0) → v2 keeps every row and marks curated Word Gym words done in the new deck (US-081). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MukharDatabase::class.java)

    @Test
    fun v1ToV2MarksCuratedWordsDone() {
        helper.createDatabase(DB, 1).use { db ->
            val insert = "INSERT INTO word (id, lang, lemma, meaning, example, register, tracks, source, source_session_id, added_at) VALUES "
            db.execSQL("$insert('en:absolutely', 'en', 'absolutely', 'm', 'e', 'casual', 'everyday', 'curated', NULL, 1000)")
            db.execSQL("$insert('en:pivotal', 'en', 'pivotal', 'm', 'e', 'power', 'professional', 'talk', 's1', 2000)")
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, *MukharDatabase.MIGRATIONS)
        db.query("SELECT word_id, lang, choice, decided_at, seq FROM word_deck").use { c ->
            assertThat(c.count).isEqualTo(1)
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("en:absolutely")
            assertThat(c.getString(2)).isEqualTo("done")
            assertThat(c.getLong(3)).isEqualTo(1000)
            assertThat(c.getLong(4)).isEqualTo(0)
        }
        db.query("SELECT COUNT(*) FROM word").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(2)
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
