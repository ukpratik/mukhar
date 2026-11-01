package com.vaak.data.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** The offline queue (US-072): one unique job per talk. Faked in repository tests. */
interface AnalysisScheduler {
    /** `analysis-<id>`, KEEP: a talk never has two queue jobs. */
    fun enqueue(sessionId: String)

    /** Busy / quota: run again after [delayMs] (appended, so it never cancels a running job). */
    fun enqueueAfter(sessionId: String, delayMs: Long)

    fun cancel(sessionId: String)
}

/** LLD §4.9: `NetworkType.CONNECTED`, exponential backoff from 30 s, at most 8 attempts (enforced by the worker). */
class WorkManagerAnalysisScheduler(private val workManager: () -> WorkManager) : AnalysisScheduler {
    override fun enqueue(sessionId: String) {
        workManager().enqueueUniqueWork(uniqueName(sessionId), ExistingWorkPolicy.KEEP, request(sessionId, 0))
    }

    override fun enqueueAfter(sessionId: String, delayMs: Long) {
        workManager().enqueueUniqueWork(uniqueName(sessionId), ExistingWorkPolicy.APPEND_OR_REPLACE, request(sessionId, delayMs))
    }

    override fun cancel(sessionId: String) {
        workManager().cancelUniqueWork(uniqueName(sessionId))
    }

    private fun request(sessionId: String, delayMs: Long) = OneTimeWorkRequestBuilder<AnalysisWorker>()
        .setInputData(workDataOf(AnalysisWorker.KEY_SESSION_ID to sessionId))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .addTag(TAG)
        .build()

    companion object {
        const val TAG = "analysis"
        const val BACKOFF_S = 30L
        fun uniqueName(sessionId: String) = "analysis-$sessionId"
    }
}
