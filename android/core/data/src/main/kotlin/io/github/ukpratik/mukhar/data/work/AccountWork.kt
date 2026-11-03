package io.github.ukpratik.mukhar.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ukpratik.mukhar.data.billing.AccountServer
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.errorCode
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.data.repo.RefreshResult
import io.github.ukpratik.mukhar.data.util.MukharLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Account jobs that must survive going offline (US-061 verification, US-063 server deletion). Faked in tests. */
interface AccountSync {
    /** Re-verify the Play purchase with the Worker when online (a purchase made offline, or a 5xx). */
    fun enqueueEntitlementRefresh()

    /** DELETE /me, retried until it succeeds (deletion "within 30 days", US-063). */
    fun enqueueDeleteMe()

    /** Delete all my data: drop every queued analysis and refresh before the wipe. */
    fun cancelAll()
}

class WorkManagerAccountSync(private val workManager: () -> WorkManager) : AccountSync {
    override fun enqueueEntitlementRefresh() {
        workManager().enqueueUniqueWork(ENTITLEMENT, ExistingWorkPolicy.KEEP, request<EntitlementSyncWorker>())
    }

    override fun enqueueDeleteMe() {
        workManager().enqueueUniqueWork(DELETE_ME, ExistingWorkPolicy.KEEP, request<DeleteMeWorker>())
    }

    override fun cancelAll() {
        workManager().cancelAllWorkByTag(WorkManagerAnalysisScheduler.TAG)
        workManager().cancelUniqueWork(ENTITLEMENT)
    }

    private inline fun <reified W : androidx.work.ListenableWorker> request() = OneTimeWorkRequestBuilder<W>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
        .build()

    companion object {
        const val ENTITLEMENT = "entitlement-refresh"
        const val DELETE_ME = "delete-me"
        const val BACKOFF_S = 60L
    }
}

@HiltWorker
class EntitlementSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val entitlements: EntitlementRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_ATTEMPTS) return Result.success()
        return when (entitlements.refresh(scheduleRetry = false)) {
            RefreshResult.RETRY -> Result.retry()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}

/**
 * Server half of "Delete all my data" (US-063). Idempotent on the Worker. Retries offline, busy and auth failures
 * (Firebase auth isn't wired yet, so every call is a 401 until it is); gives up after [MAX_ATTEMPTS] (D-035).
 */
@HiltWorker
class DeleteMeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val server: AccountServer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_ATTEMPTS) {
            MukharLog.w("delete-me: gave up after $runAttemptCount attempts")
            return Result.failure()
        }
        return when (val r = server.deleteMe()) {
            is Outcome.Ok -> Result.success()
            is Outcome.Err -> {
                MukharLog.i("delete-me: ${r.error.errorCode}")
                if (r.error is AppError.UpdateRequired) Result.failure() else Result.retry()
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 20
    }
}
