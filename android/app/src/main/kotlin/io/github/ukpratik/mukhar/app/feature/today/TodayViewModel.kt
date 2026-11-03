package io.github.ukpratik.mukhar.app.feature.today

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.mukhar.app.feature.plan.TrialBannerUi
import io.github.ukpratik.mukhar.data.db.WordDao
import io.github.ukpratik.mukhar.data.repo.EntitlementRepository
import io.github.ukpratik.mukhar.data.prefs.SettingsStore
import io.github.ukpratik.mukhar.data.repo.HabitRepository
import io.github.ukpratik.mukhar.data.repo.WordItem
import io.github.ukpratik.mukhar.data.repo.WordRepository
import io.github.ukpratik.mukhar.data.tts.Speaker
import io.github.ukpratik.mukhar.data.system.ConnectivityObserver
import io.github.ukpratik.mukhar.data.today.RecoveredTalk
import io.github.ukpratik.mukhar.data.today.TalkStatusSource
import io.github.ukpratik.mukhar.data.today.WaitingTalks
import io.github.ukpratik.mukhar.data.work.ReminderScheduler
import io.github.ukpratik.mukhar.domain.Clock
import io.github.ukpratik.mukhar.domain.streak.nextCutoff
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

private data class Extras(
    val waiting: WaitingTalks?,
    val recovered: RecoveredTalk?,
    val shuffle: Int,
    val notificationsAllowed: Boolean,
    val online: Boolean,
)

/** S07 (US-030): Room flows + settings + connectivity, mapped by [TodayStateMapper]. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val habit: HabitRepository,
    private val settings: SettingsStore,
    private val wordDao: WordDao,
    private val words: WordRepository,
    private val speaker: Speaker,
    private val clock: Clock,
    private val scheduler: ReminderScheduler,
    connectivity: ConnectivityObserver,
    talkStatus: TalkStatusSource,
    @param:ApplicationContext private val context: Context,
    entitlements: EntitlementRepository,
) : ViewModel() {

    private val shuffle = MutableStateFlow(0)
    private val notificationsAllowed = MutableStateFlow(true)
    private val _wordsLearned = MutableStateFlow(0)
    val wordsLearned: StateFlow<Int> = _wordsLearned.asStateFlow()

    /** Re-evaluates "today" each minute, so the 03:00 boundary passes while the screen is open. */
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), replay = 1)

    private val due = ticks.map { nextCutoff(clock.now(), clock.zone()) }
        .distinctUntilChanged()
        .flatMapLatest { cutoff ->
            combine(
                wordDao.observeDueCount(cutoff.toEpochMilli()),
                wordDao.observeDueCount(cutoff.plus(Duration.ofDays(1)).toEpochMilli()),
            ) { now, byTomorrow -> now to (byTomorrow - now).coerceAtLeast(0) }
        }

    private val extras = combine(talkStatus.waiting, talkStatus.recovered, shuffle, notificationsAllowed, connectivity.isOnline) {
            waiting, recovered, n, allowed, online ->
        Extras(waiting, recovered, n, allowed, online)
    }

    /** Today's words, re-picked when the streak day changes (03:00). */
    private val todaysWords = ticks.map { words.today() }.distinctUntilChanged().flatMapLatest { words.observeDailyWords(it) }

    private val wordsAndVoices = combine(todaysWords, speaker.voices) { w, v -> w to v }

    /** Re-checked each minute so the banner appears when trial day 6 starts while Today is open. */
    private val trialBanner = combine(entitlements.snapshot, settings.trialBannerDismissedDay, ticks) { e, dismissed, _ ->
        TrialBannerUi.from(e, clock.now(), clock.zone(), dismissed)
    }

    private val extrasAndBanner = combine(extras, trialBanner) { x, b -> x to b }

    val state: StateFlow<TodayUiState> = combine(habit.observe(ticks), settings.settings, due, extrasAndBanner, wordsAndVoices) { h, s, (dueNow, dueTomorrow), (x, banner), (w, v) ->
        TodayStateMapper.map(
            TodayInputs(
                now = clock.now().atZone(clock.zone()),
                settings = s,
                habit = h,
                dueNow = dueNow,
                dueTomorrow = dueTomorrow,
                online = x.online,
                waiting = x.waiting,
                recovered = x.recovered,
                notificationsAllowed = x.notificationsAllowed,
                shuffle = x.shuffle,
            ),
        ).copy(words = w, voices = v, trialBanner = banner)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), TodayUiState())

    init {
        // App open: spend freezes on missed days (US-032), then re-plan the reminder (US-033).
        viewModelScope.launch {
            habit.refresh()
            scheduler.reschedule()
        }
        refreshNotificationPermission()
        speaker.warmUp()
    }

    fun onSpeak(word: WordItem) = speaker.speak(word.lemma, word.lang)

    fun onShuffle() = shuffle.update { it + 1 }

    fun onWeeklyGoal(goal: Int) {
        viewModelScope.launch { habit.setWeeklyGoal(goal) }
    }

    fun onSheetOpened() {
        viewModelScope.launch { _wordsLearned.value = habit.wordsLearned() }
    }

    /** Closing the sheet acknowledges the one-time freeze or fresh-start message. */
    fun onSheetDismissed() {
        val h = state.value.habit ?: return
        viewModelScope.launch {
            if (h.freezeNoticeDay != null) habit.acknowledgeFreezeNotice()
            if (h.freshStart) habit.acknowledgeFreshStart()
        }
    }

    /** [Keep] hides the trial banner for the rest of the day (S18 notes). */
    fun onKeepTrial() {
        viewModelScope.launch { settings.dismissTrialBanner(clock.now().atZone(clock.zone()).toLocalDate()) }
    }

    fun onDismissTip() {
        viewModelScope.launch { settings.dismissNotificationTip() }
    }

    fun refreshNotificationPermission() {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        notificationsAllowed.value = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private inline fun MutableStateFlow<Int>.update(f: (Int) -> Int) {
        value = f(value)
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val STOP_MS = 5_000L
    }
}
