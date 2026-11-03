package com.vaak.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vaak.data.repo.EntitlementRepository
import com.vaak.data.repo.TalkRepository
import com.vaak.data.util.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VaakApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var talks: TalkRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var entitlements: EntitlementRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        // NFR-002: no disk or network work on the main thread during cold start. Debug builds log every violation
        // (Hilt injection included, so it is set before super.onCreate()); release builds skip StrictMode entirely.
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
        // Crash recovery and queue resume (US-018, US-072): recording rows → recovered, unfinished analyses re-enqueued.
        appScope.launch { talks.recoverOnStart() }
        // Restore on reinstall and the daily re-check (US-061): Play purchases → Worker verification → cache.
        appScope.launch { entitlements.refresh() }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build(),
        )
    }
}
