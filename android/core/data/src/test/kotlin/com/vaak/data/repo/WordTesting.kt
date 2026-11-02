package com.vaak.data.repo

import com.vaak.data.content.WordCatalog
import com.vaak.data.db.VaakDatabase
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.tempSettingsStore
import com.vaak.domain.Clock
import com.vaak.domain.fsrs.Fsrs
import kotlinx.serialization.json.Json
import java.io.File

/** The bundled word packs, read from the module's assets folder (JVM tests run from the module directory). */
fun assetCatalog(json: Json = Json { ignoreUnknownKeys = true }): WordCatalog =
    WordCatalog({ lang -> File("src/main/assets/${WordCatalog.asset(lang)}").readText() }, json)

/** A WordRepository on [db] with fuzz off, so FSRS intervals are deterministic. */
fun testWordRepository(
    db: VaakDatabase,
    clock: Clock,
    settings: SettingsStore = tempSettingsStore(),
    habits: HabitRepository = HabitRepository(db.habitDao(), settings, clock),
    catalog: WordCatalog = assetCatalog(),
): WordRepository = WordRepository(db.wordDao(), catalog, settings, habits, clock, Fsrs(enableFuzzing = false))
