package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.content.WordCatalog
import io.github.ukpratik.mukhar.data.db.MukharDatabase
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.tempSettingsStore
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.fsrs.Fsrs
import kotlinx.serialization.json.Json
import java.io.File

/** The bundled word packs, read from the module's assets folder (JVM tests run from the module directory). */
fun assetCatalog(json: Json = Json { ignoreUnknownKeys = true }): WordCatalog =
    WordCatalog({ lang -> File("src/main/assets/${WordCatalog.asset(lang)}").readText() }, json)

/** A WordRepository on [db] with fuzz off, so FSRS intervals are deterministic. */
fun testWordRepository(
    db: MukharDatabase,
    clock: Clock,
    settings: SettingsStore = tempSettingsStore(),
    habits: HabitRepository = HabitRepository(db.habitDao(), settings, clock),
    catalog: WordCatalog = assetCatalog(),
): WordRepository = WordRepository(db.wordDao(), catalog, settings, habits, clock, Fsrs(enableFuzzing = false))
