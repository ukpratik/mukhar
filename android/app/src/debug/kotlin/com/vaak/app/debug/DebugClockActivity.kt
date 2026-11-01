package com.vaak.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vaak.data.db.HabitDao
import com.vaak.data.db.ReminderLogEntity
import com.vaak.data.time.OverridableClock
import com.vaak.data.work.ReminderHandler
import com.vaak.data.work.ReminderScheduler
import com.vaak.domain.streak.CUTOFF_HOUR
import com.vaak.domain.streak.streakDay
import com.vaak.ui.theme.VaakTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Debug builds only (build plan "Debug hooks"): shift the injected clock to test streak days and reminders, fire
 * the reminder now, and read the local delivery log (US-033 QA). Restart the app after a shift so screens re-read it.
 */
@AndroidEntryPoint
class DebugClockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VaakTheme { DebugClockScreen() } }
    }
}

data class DebugState(val now: String = "", val streakDay: String = "", val overridden: Boolean = false, val next: String = "–", val log: List<ReminderLogEntity> = emptyList())

@HiltViewModel
class DebugClockViewModel @Inject constructor(
    private val clock: OverridableClock,
    private val scheduler: ReminderScheduler,
    private val handler: ReminderHandler,
    private val dao: HabitDao,
) : ViewModel() {
    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val now = clock.now()
            val zone = clock.zone()
            _state.value = DebugState(
                now = now.atZone(zone).format(FMT) + " " + zone.id,
                streakDay = streakDay(now, zone).toString(),
                overridden = clock.isOverridden,
                next = scheduler.next.value?.let { it.at.atZone(zone).format(FMT) + " (" + it.backoff + ")" } ?: "none",
                log = dao.recentReminders(30),
            )
        }
    }

    fun shift(d: Duration) = act { clock.shiftBy(d) }

    /** Jumps to [hour]:[minute] on the next day it occurs (local, in the current zone). */
    fun jumpTo(hour: Int, minute: Int) = act {
        val local = clock.now().atZone(clock.zone()).toLocalDateTime()
        var target = local.toLocalDate().atTime(hour, minute)
        if (!target.isAfter(local)) target = target.plusDays(1)
        clock.setNow(target.atZone(clock.zone()).toInstant())
    }

    fun zone(id: String?) = act { clock.setZone(id?.let(ZoneId::of)) }

    fun reset() = act { clock.reset() }

    fun reschedule() = act { }

    fun fireNow() {
        viewModelScope.launch {
            handler.onAlarm(clock.now())
            refresh()
        }
    }

    private fun act(block: () -> Unit) {
        block()
        viewModelScope.launch {
            scheduler.reschedule()
            refresh()
        }
    }

    companion object {
        val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebugClockScreen(vm: DebugClockViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Debug: clock and reminders", style = MaterialTheme.typography.headlineMedium)
            Text("Now: ${s.now}${if (s.overridden) "  (overridden)" else ""}")
            Text("Streak day: ${s.streakDay} (cutoff $CUTOFF_HOUR:00)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ vm.shift(Duration.ofHours(1)) }) { Text("+1 h") }
                FilledTonalButton({ vm.shift(Duration.ofDays(1)) }) { Text("+1 day") }
                FilledTonalButton({ vm.shift(Duration.ofDays(-1)) }) { Text("−1 day (clock back)") }
                FilledTonalButton({ vm.jumpTo(2, 59) }) { Text("Next 02:59") }
                FilledTonalButton({ vm.jumpTo(3, 0) }) { Text("Next 03:00") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ vm.zone("Asia/Kolkata") }) { Text("IST") }
                FilledTonalButton({ vm.zone("America/New_York") }) { Text("New York") }
                FilledTonalButton({ vm.zone("Europe/London") }) { Text("London") }
                FilledTonalButton({ vm.zone(null) }) { Text("Device zone") }
                FilledTonalButton(vm::reset) { Text("Reset clock") }
            }
            HorizontalDivider()
            Text("Next reminder: ${s.next}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(vm::reschedule) { Text("Reschedule") }
                FilledTonalButton(vm::fireNow) { Text("Fire reminder now") }
            }
            Text("Delivery log (newest first)", style = MaterialTheme.typography.titleMedium)
            s.log.forEach { row ->
                val zone = ZoneId.systemDefault()
                val planned = Instant.ofEpochMilli(row.scheduledFor).atZone(zone).toLocalDateTime()
                val delay = row.shownAt?.let { Duration.ofMillis(it - row.scheduledFor).toMinutes().toString() + " min" } ?: "–"
                Text("${planned.format(DebugClockViewModel.FMT)} · ${row.outcome} · delay $delay", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "adb: am broadcast -a com.vaak.debug.CLOCK -p <package> --es at 2026-11-05T19:29 | --el shift_minutes 60 | --es zone Asia/Kolkata | --ez reset true | --ez fire true",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** `adb shell am broadcast -a com.vaak.debug.CLOCK -p com.vaak.app.debug --es at 2026-11-05T19:29` (shell has DUMP). */
@AndroidEntryPoint
class DebugClockReceiver : BroadcastReceiver() {
    @Inject lateinit var clock: OverridableClock
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var handler: ReminderHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (intent.getBooleanExtra("reset", false)) clock.reset()
        intent.getStringExtra("zone")?.let { clock.setZone(ZoneId.of(it)) }
        intent.getStringExtra("at")?.let { clock.setNow(LocalDateTime.parse(it).atZone(clock.zone()).toInstant()) }
        intent.getLongExtra("shift_minutes", 0L).takeIf { it != 0L }?.let { clock.shiftBy(Duration.ofMinutes(it)) }
        val fire = intent.getBooleanExtra("fire", false)
        val pending = goAsync()
        scope.launch {
            try {
                if (fire) handler.onAlarm(clock.now()) else scheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.vaak.debug.CLOCK"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
