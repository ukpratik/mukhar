package com.vaak.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaak.data.db.VaakDatabase
import com.vaak.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files

/** A SettingsStore on a fresh temp file, plus an in-memory Room database. Close with [close]. */
class TestStores {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("vaak-test").toFile()

    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })

    val db: VaakDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VaakDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    fun close() {
        db.close()
        scope.cancel()
        dir.deleteRecursively()
    }
}

/** A SettingsStore on its own temp file, for tests that bring their own database (HabitRepository needs one). */
fun tempSettingsStore(): SettingsStore {
    val dir = Files.createTempDirectory("vaak-settings").toFile().apply { deleteOnExit() }
    val file = File(dir, "settings.preferences_pb").apply { deleteOnExit() }
    return SettingsStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { file })
}
