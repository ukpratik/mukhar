package io.github.ukpratik.mukhar.data.repo

import io.github.ukpratik.mukhar.data.analytics.AnalyticsEvent
import io.github.ukpratik.mukhar.data.analytics.AnalyticsSink
import io.github.ukpratik.mukhar.data.work.AccountSync
import io.github.ukpratik.mukhar.data.work.TrialReminderScheduler
import java.time.Instant

/** Records the trial alarm: an Instant per schedule, null per cancel. */
class RecordingTrialReminders : TrialReminderScheduler {
    val log = mutableListOf<Instant?>()
    val last: Instant? get() = log.lastOrNull()
    override fun schedule(at: Instant) {
        log += at
    }
    override fun cancel() {
        log += null
    }
}

class RecordingAccountSync : AccountSync {
    var refreshes = 0
    var deletes = 0
    var cancels = 0
    override fun enqueueEntitlementRefresh() {
        refreshes++
    }
    override fun enqueueDeleteMe() {
        deletes++
    }
    override fun cancelAll() {
        cancels++
    }
}

class RecordingSink : AnalyticsSink {
    val events = mutableListOf<AnalyticsEvent>()
    val names: List<String> get() = events.map { it.name }
    override fun record(event: AnalyticsEvent) {
        events += event
    }
}
