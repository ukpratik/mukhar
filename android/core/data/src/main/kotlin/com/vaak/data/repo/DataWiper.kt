package com.vaak.data.repo

import com.vaak.data.analytics.Analytics
import com.vaak.data.analytics.AnalyticsEvent
import com.vaak.data.db.VaakDatabase
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.util.VaakLog
import com.vaak.data.work.AccountSync
import com.vaak.data.work.TrialReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Delete all my data" (US-063, NFR-013): on this phone right away (Room, the settings DataStore, recordings and
 * share images), then `DELETE /me` queued with WorkManager so it's retried until the server confirms (within 30
 * days). The Play subscription itself is untouched (S19 copy). The app then restarts at onboarding.
 */
class DataWiper(
    private val db: VaakDatabase,
    private val settings: SettingsStore,
    /** Folders whose contents go: `files/recordings`, `cache/share`. */
    private val folders: List<File>,
    private val sync: AccountSync,
    private val trialReminders: TrialReminderScheduler,
    private val analytics: Analytics,
    /** Re-plans the daily reminder from the now-empty settings, which cancels its alarm. */
    private val afterWipe: suspend () -> Unit,
) {
    suspend fun wipeAll() {
        // Logged while consent is still known; nothing after this point can be sent.
        analytics.log(AnalyticsEvent.dataDeleted)
        sync.cancelAll()
        trialReminders.cancel()
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            folders.forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
        }
        settings.clearAll()
        afterWipe()
        sync.enqueueDeleteMe()
        VaakLog.i("data: wiped on device, server deletion queued")
    }
}
