package com.vaak.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaak.data.repo.AnalysisRun
import com.vaak.data.repo.TalkRepository
import com.vaak.data.repo.TalkStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The queue job (LLD §4.9, US-072): claims the talk's lease and runs the same [AnalysisPipeline] code as the
 * foreground path. Offline/STT failures retry with backoff; busy/quota re-schedule for later; after
 * [MAX_ATTEMPTS] the talk stays saved and is retried at the next app start or by the user.
 */
@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val talks: TalkRepository,
    private val scheduler: AnalysisScheduler,
    private val notifier: TalkNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        if (runAttemptCount >= MAX_ATTEMPTS) return Result.success()
        return when (val run = talks.analyze(id)) {
            AnalysisRun.Done -> {
                if (talks.session(id)?.status == TalkStatus.FEEDBACK_READY) notifier.resultsReady(id)
                Result.success()
            }
            AnalysisRun.Retry, AnalysisRun.LeaseHeld -> Result.retry()
            is AnalysisRun.RetryAfter -> {
                scheduler.enqueueAfter(id, run.delayMs)
                Result.success()
            }
            AnalysisRun.Gone -> Result.success()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val MAX_ATTEMPTS = 8
    }
}
