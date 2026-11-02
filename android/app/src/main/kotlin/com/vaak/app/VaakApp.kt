package com.vaak.app

import android.app.Application
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
        super.onCreate()
        // Crash recovery and queue resume (US-018, US-072): recording rows → recovered, unfinished analyses re-enqueued.
        appScope.launch { talks.recoverOnStart() }
        // Restore on reinstall and the daily re-check (US-061): Play purchases → Worker verification → cache.
        appScope.launch { entitlements.refresh() }
    }
}
