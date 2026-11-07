package io.github.ukpratik.mukhar.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.mukhar.app.feature.plan.PremiumGate
import io.github.ukpratik.mukhar.app.feature.progress.PlanRowUi
import io.github.ukpratik.mukhar.data.byok.ByokKeyRepository
import io.github.ukpratik.mukhar.data.local.ModelManager
import io.github.ukpratik.mukhar.domain.analysis.AnalysisMode
import io.github.ukpratik.mukhar.data.db.ProgressDao
import io.github.ukpratik.mukhar.data.prefs.ByokUsage
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.data.prefs.ReminderSettings
import io.github.ukpratik.mukhar.data.prefs.Routine
import io.github.ukpratik.mukhar.data.prefs.Settings
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.repo.DataWiper
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.data.repo.HabitRepository
import io.github.ukpratik.mukhar.data.repo.TalkRepository
import io.github.ukpratik.mukhar.data.work.ReminderScheduler
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.billing.EntitlementRules
import io.github.ukpratik.mukhar.domain.model.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

enum class DeleteStage { NONE, CONFIRM, DELETING, DONE }

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: Settings = Settings(),
    val weeklyGoal: Int = 5,
    val plan: PlanRowUi = PlanRowUi(io.github.ukpratik.mukhar.domain.billing.SubscriptionStatus.FREE, null),
    val delete: DeleteStage = DeleteStage.NONE,
    val deleteUnderstood: Boolean = false,
    val talkCount: Int = 0,
    val wordCount: Int = 0,
    val analysis: AnalysisUi = AnalysisUi(),
    /** D-039: the Plan row is hidden while Premium is switched off. */
    val showPlan: Boolean = PremiumGate.PAYWALL_ENABLED,
)

private data class Transient(val delete: DeleteStage = DeleteStage.NONE, val understood: Boolean = false, val talks: Int = 0, val words: Int = 0)

/** S19 (US-063): every change saves instantly. Delete all my data wipes the phone and queues DELETE /me. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val habits: HabitRepository,
    private val reminders: ReminderScheduler,
    private val talks: TalkRepository,
    private val progressDao: ProgressDao,
    private val wiper: DataWiper,
    entitlements: EntitlementRepository,
    private val clock: Clock,
    private val models: ModelManager,
    keys: ByokKeyRepository,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())

    /** The last reminder, so switching it off and on again restores the user's time and phrase. */
    private var lastReminder: ReminderSettings? = null

    private val analysis = combine(store.settings, models.models, keys.keys, store.byokUsage) { s, m, k, u ->
        val month = YearMonth.from(clock.now().atZone(clock.zone())).toString()
        AnalysisUi(
            mode = s.analysisMode, models = m, canDownload = models.canDownload, keys = k,
            usage = if (u.month == month) u else ByokUsage(month),
            softCapOn = s.softCapOn, softCapInr = s.softCapInr,
        )
    }

    val state: StateFlow<SettingsUiState> = combine(
        store.settings,
        habits.observe().map { it.week.goal },
        entitlements.snapshot,
        transient,
        analysis,
    ) { s, goal, e, t, a ->
        if (s.reminder != null) lastReminder = s.reminder
        SettingsUiState(
            loading = false,
            settings = s,
            weeklyGoal = goal,
            plan = PlanRowUi(
                EntitlementRules.status(e, clock.now()),
                e.expiryMs?.let { Instant.ofEpochMilli(it).atZone(clock.zone()).toLocalDate() },
            ),
            delete = t.delete,
            deleteUnderstood = t.understood,
            talkCount = t.talks,
            wordCount = t.words,
            analysis = a,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), SettingsUiState())

    fun onLangs(langs: Set<Lang>) = save { store.setLangsAndTracks(langs, state.value.settings.tracks.ifEmpty { setOf(GoalTrack.EVERYDAY) }) }

    fun onTracks(tracks: Set<GoalTrack>) = save {
        store.setLangsAndTracks(state.value.settings.langs.ifEmpty { setOf(Lang.EN) }, tracks)
    }

    fun onDailyWords(lang: Lang, n: Int) = save { store.setDailyNewWords(lang, n) }

    fun onWeeklyGoal(goal: Int) = save { habits.setWeeklyGoal(goal) }

    fun onReminderOn(on: Boolean) = save {
        val r = if (on) lastReminder ?: ReminderSettings(Routine.DINNER.suggestedTime, Routine.DINNER) else null
        store.setReminder(r)
        reminders.reschedule()
    }

    fun onReminderTime(time: LocalTime) = save {
        val current = state.value.settings.reminder ?: lastReminder ?: ReminderSettings(time, Routine.CUSTOM)
        store.setReminder(current.copy(time = time))
        reminders.reschedule()
    }

    /** A custom phrase makes the routine "custom" (≤ 30 characters, as S06). Blank keeps the routine as it was. */
    fun onReminderPhrase(phrase: String) = save {
        val current = state.value.settings.reminder ?: return@save
        val trimmed = phrase.trim().take(ReminderSettings.MAX_PHRASE)
        if (trimmed.isEmpty()) return@save
        store.setReminder(current.copy(routine = Routine.CUSTOM, customPhrase = trimmed))
        reminders.reschedule()
    }

    fun onStutterFriendly(on: Boolean) = save { store.setStutterFriendly(on) }

    fun onTransliteration(on: Boolean) = save { store.setTransliteration(on) }

    fun onAnalytics(on: Boolean) = save { store.setAnalyticsConsent(on) }

    /** Off deletes the kept baseline recording now; the numbers stay (S19 notes). */
    fun onKeepBaselineAudio(on: Boolean) = save {
        store.setKeepBaselineAudio(on)
        if (!on) progressDao.keptBaselines().forEach { talks.setAudioKept(it.id, false) }
    }

    /** ADR-008: switching mode re-runs talks that were waiting for an engine. Mukhar Cloud can't be picked in R1. */
    fun onAnalysisMode(mode: AnalysisMode) = save {
        if (mode == AnalysisMode.CLOUD && !AnalysisMode.CLOUD_ENABLED) return@save
        store.setAnalysisMode(mode)
        talks.retryWaitingForEngine()
    }

    fun onDownloadModel(lang: Lang) = save { if (models.canDownload) models.download(lang) }

    fun onSoftCap(on: Boolean) = save { store.setSoftCap(on, state.value.analysis.softCapInr) }

    fun onDeleteOpen() {
        viewModelScope.launch {
            val t = progressDao.talkCount()
            val w = progressDao.wordCount()
            transient.update { Transient(DeleteStage.CONFIRM, understood = false, talks = t, words = w) }
        }
    }

    fun onDeleteUnderstood(on: Boolean) = transient.update { it.copy(understood = on) }

    fun onDeleteCancel() = transient.update { Transient() }

    fun onDeleteConfirm() {
        if (!transient.value.understood || transient.value.delete != DeleteStage.CONFIRM) return
        transient.update { it.copy(delete = DeleteStage.DELETING) }
        viewModelScope.launch {
            wiper.wipeAll()
            transient.update { it.copy(delete = DeleteStage.DONE) }
        }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
