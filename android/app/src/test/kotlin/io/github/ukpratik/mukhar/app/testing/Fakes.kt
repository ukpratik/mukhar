package io.github.ukpratik.mukhar.app.testing

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ukpratik.mukhar.app.feature.onboarding.OnboardingViewModel
import io.github.ukpratik.mukhar.app.feature.onboarding.warmup.WarmupMeter
import io.github.ukpratik.mukhar.data.baseline.BaselineProgress
import io.github.ukpratik.mukhar.data.baseline.BaselineRecorder
import io.github.ukpratik.mukhar.data.baseline.BaselineResult
import io.github.ukpratik.mukhar.data.db.HabitDao
import io.github.ukpratik.mukhar.data.db.HabitStateEntity
import io.github.ukpratik.mukhar.data.db.InsightEntity
import io.github.ukpratik.mukhar.data.db.PromptHistoryEntity
import io.github.ukpratik.mukhar.data.db.ReminderLogEntity
import io.github.ukpratik.mukhar.data.db.StreakDayEntity
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.repo.HabitRepository
import io.github.ukpratik.mukhar.data.work.ReminderScheduler
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

/** In-memory HabitDao (the app module doesn't see Room; HabitRepository runs on this). */
class FakeHabitDao : HabitDao {
    private val days = MutableStateFlow<Map<String, StreakDayEntity>>(emptyMap())
    private val habit = MutableStateFlow<HabitStateEntity?>(null)
    val reminders = mutableListOf<ReminderLogEntity>()

    override suspend fun insertStreakDay(day: StreakDayEntity): Long {
        if (day.day in days.value) return -1
        days.value = days.value + (day.day to day)
        return 1
    }
    override fun observeStreakDays(): Flow<List<StreakDayEntity>> = days.map { it.values.sortedBy(StreakDayEntity::day) }
    override suspend fun streakDays(): List<StreakDayEntity> = days.value.values.sortedBy(StreakDayEntity::day)
    override suspend fun habitState(): HabitStateEntity? = habit.value
    override suspend fun upsertHabitState(state: HabitStateEntity) { habit.value = state }
    override suspend fun insertReminderLog(entry: ReminderLogEntity): Long {
        val id = reminders.size + 1L
        reminders += entry.copy(id = id)
        return id
    }
    override suspend fun recentReminders(limit: Int): List<ReminderLogEntity> = reminders.sortedByDescending { it.scheduledFor }.take(limit)
    override suspend fun insertPromptHistory(entry: PromptHistoryEntity) = Unit
    override suspend fun promptsShownSince(since: Long): List<String> = emptyList()
    override suspend fun recentPromptIds(limit: Int): List<String> = emptyList()
    override suspend fun insertInsight(insight: InsightEntity): Long = 1
    override suspend fun achievedInsightKeys(): List<String> = emptyList()
    override fun observeHabitState(): Flow<HabitStateEntity?> = habit
    override suspend fun markReminderOpened(id: Long, at: Long) = Unit
    override suspend fun markReminderIgnored(id: Long) = Unit
    override suspend fun wordCount(): Int = 0
}

/** Emits a 20 s read: 0.6 s of quiet, then a steady voice-level tone. */
class FakeWarmupMeter(private val speech: Boolean = true) : WarmupMeter {
    override fun frames(): Flow<ShortArray> = flow {
        var i = 0
        while (true) {
            val amp = if (i < 30 || !speech) 30.0 else 6_000.0
            emit(ShortArray(320) { n -> (amp * sin(2 * PI * 220 * n / 16_000.0)).toInt().toShort() })
            i++
        }
    }
}

class FakeBaselineRecorder(
    override val isAvailable: Boolean = true,
    private val result: BaselineResult = BaselineResult.Saved("baseline-1", 60_000),
) : BaselineRecorder {
    private val _progress = MutableStateFlow<BaselineProgress?>(null)
    override val progress: StateFlow<BaselineProgress?> = _progress.asStateFlow()
    var kept: Boolean? = null
    override suspend fun start(lang: Lang, prompt: String): Boolean {
        _progress.value = BaselineProgress(1_000, 0.5f)
        return true
    }
    override suspend fun stop(): BaselineResult {
        _progress.value = null
        return result
    }
    override suspend fun discard() { _progress.value = null }
    override suspend fun keepAudio(sessionId: String, keep: Boolean) { kept = keep }
    val abandoned = mutableListOf<String>()
    val resumed = mutableListOf<String>()
    override fun abandon(sessionId: String) { abandoned += sessionId }
    override suspend fun resume(sessionId: String): BaselineResult {
        resumed += sessionId
        return result
    }
}

/** Everything an OnboardingViewModel needs, on fakes and a temp DataStore. */
class OnboardingHarness(
    val clock: FixedClock = FixedClock(Instant.parse("2026-11-02T04:30:00Z"), ZoneId.of("Asia/Kolkata")),
    val recorder: BaselineRecorder = FakeBaselineRecorder(),
    meter: WarmupMeter = FakeWarmupMeter(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("mukhar-app-test").toFile()
    val context: Context = ApplicationProvider.getApplicationContext()
    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
    val dao = FakeHabitDao()
    val habit = HabitRepository(dao, settings, clock)
    val scheduler = ReminderScheduler(context, settings, dao, clock)
    /** Created on first use, so a test can write settings first (the init reads them). */
    val vm: OnboardingViewModel by lazy { OnboardingViewModel(settings, habit, scheduler, recorder, meter, clock) }

    /** Starts the flow at [step] as if the user had got there before (ages gate passed). */
    fun resumeAt(step: io.github.ukpratik.mukhar.app.feature.onboarding.OnboardingStep) = kotlinx.coroutines.runBlocking {
        settings.setAgeGate(io.github.ukpratik.mukhar.data.prefs.AgeGate.PASSED)
        settings.setOnboardingStep(step.name)
    }

    fun close() {
        // Wait for DataStore's in-flight write to stop before deleting its folder; deleting first made a late write
        // throw "Inoperable file" into the next test under a loaded full-suite run (flaky OnboardingUiTest, M6B).
        // Don't block the main thread while joining: in-flight work may need the main looper to finish (a blocking
        // join here deadlocked the suite). Keep idling the looper until the scope's job completes, up to 5 s.
        val job = scope.coroutineContext[kotlinx.coroutines.Job]!!
        job.cancel()
        val end = System.currentTimeMillis() + 5_000
        while (!job.isCompleted && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        dir.deleteRecursively()
    }
}

/** Runs main-looper work (viewModelScope) until [condition] holds; DataStore work happens on IO threads. */
fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        shadowOf(Looper.getMainLooper()).idle()
        check(System.currentTimeMillis() < end) { "Timed out waiting" }
        Thread.sleep(5)
    }
}
