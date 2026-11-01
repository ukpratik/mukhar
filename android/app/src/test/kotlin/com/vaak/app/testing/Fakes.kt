package com.vaak.app.testing

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.vaak.app.feature.onboarding.OnboardingViewModel
import com.vaak.app.feature.onboarding.warmup.WarmupMeter
import com.vaak.data.baseline.BaselineProgress
import com.vaak.data.baseline.BaselineRecorder
import com.vaak.data.baseline.BaselineResult
import com.vaak.data.db.HabitDao
import com.vaak.data.db.HabitStateEntity
import com.vaak.data.db.InsightEntity
import com.vaak.data.db.PromptHistoryEntity
import com.vaak.data.db.ReminderLogEntity
import com.vaak.data.db.StreakDayEntity
import com.vaak.data.prefs.SettingsStore
import com.vaak.data.repo.HabitRepository
import com.vaak.data.work.ReminderScheduler
import com.vaak.domain.FixedClock
import com.vaak.domain.model.Lang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
}

/** Everything an OnboardingViewModel needs, on fakes and a temp DataStore. */
class OnboardingHarness(
    val clock: FixedClock = FixedClock(Instant.parse("2026-11-02T04:30:00Z"), ZoneId.of("Asia/Kolkata")),
    val recorder: BaselineRecorder = FakeBaselineRecorder(),
    meter: WarmupMeter = FakeWarmupMeter(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("vaak-app-test").toFile()
    val context: Context = ApplicationProvider.getApplicationContext()
    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
    val dao = FakeHabitDao()
    val habit = HabitRepository(dao, settings, clock)
    val scheduler = ReminderScheduler(context, settings, dao, clock)
    /** Created on first use, so a test can write settings first (the init reads them). */
    val vm: OnboardingViewModel by lazy { OnboardingViewModel(settings, habit, scheduler, recorder, meter, clock) }

    /** Starts the flow at [step] as if the user had got there before (ages gate passed). */
    fun resumeAt(step: com.vaak.app.feature.onboarding.OnboardingStep) = kotlinx.coroutines.runBlocking {
        settings.setAgeGate(com.vaak.data.prefs.AgeGate.PASSED)
        settings.setOnboardingStep(step.name)
    }

    fun close() {
        scope.cancel()
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
