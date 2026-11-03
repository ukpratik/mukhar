package io.github.ukpratik.mukhar.data.audio

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.domain.audio.EnergyVad
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/** A mic that "speaks" a 220 Hz tone after [silentFrames] frames of near-silence; one 20 ms frame per 20 ms. */
private class FakeSource(private val silentFrames: Int = 30, private val failOnStart: Boolean = false) : PcmSource {
    var started = 0
    var stopped = 0
    var released = false
    private var frame = 0
    private var n = 0L
    override fun start(): Boolean {
        if (failOnStart) return false
        started++
        return true
    }
    override suspend fun read(buffer: ShortArray): Int {
        delay(EnergyVad.FRAME_MS.toLong())
        val speaking = frame++ >= silentFrames
        for (i in buffer.indices) {
            buffer[i] = if (speaking) (8000 * sin(2 * PI * 220 * n++ / EnergyVad.SAMPLE_RATE)).toInt().toShort() else ((i % 7) - 3).toShort()
        }
        return buffer.size
    }
    override fun stop() { stopped++ }
    override fun release() { released = true }
}

private class CountingSink : AudioSink {
    var samples = 0L
    var finished = false
    var aborted = false
    override fun write(pcm: ShortArray, length: Int) { samples += length }
    override fun finish() { finished = true }
    override fun abort() { aborted = true }
}

class RecorderTest {
    private val file = File.createTempFile("talk", ".aac")
    private val sink = CountingSink()

    private fun TestScope.recorder(source: FakeSource = FakeSource()) =
        Recorder({ source }, { sink }, StandardTestDispatcher(testScheduler))

    @Test
    fun recordsUntilStopAndReportsSpeech() = runTest {
        val r = recorder()
        assertThat(r.start(file, capMs = 180_000)).isTrue()
        advanceTimeBy(5_000)
        val active = r.state.value as RecorderState.Active
        assertThat(active.elapsedMs).isAtLeast(4_800)
        assertThat(active.waveform).isNotEmpty()
        val result = r.stop()!!
        assertThat(result.durationMs).isIn(4_900L..5_100L)
        assertThat(result.speechMs).isAtLeast(4_000) // 0.6 s of silence, then tone
        assertThat(result.stoppedAtCap).isFalse()
        assertThat(sink.finished).isTrue()
        assertThat(sink.samples).isEqualTo(result.durationMs * 16)
        assertThat(r.state.value).isInstanceOf(RecorderState.Finished::class.java)
    }

    @Test
    fun stopsByItselfAtTheCap() = runTest {
        val r = recorder()
        r.start(file, capMs = 2_000)
        advanceTimeBy(3_000)
        runCurrent()
        val finished = r.state.value as RecorderState.Finished
        assertThat(finished.result.stoppedAtCap).isTrue()
        assertThat(finished.result.durationMs).isEqualTo(2_000)
    }

    @Test
    fun userPausesAreLimitedToTwoButAutoPauseAlwaysWorks() = runTest {
        val source = FakeSource()
        val r = recorder(source)
        r.start(file, 180_000)
        advanceTimeBy(1_000)
        assertThat(r.pause()).isTrue()
        runCurrent()
        val paused = r.state.value as RecorderState.Active
        assertThat(paused.paused).isTrue()
        assertThat(paused.pausesLeft).isEqualTo(1)
        val elapsedAtPause = paused.elapsedMs
        advanceTimeBy(5_000) // no time passes in the recording while paused
        assertThat((r.state.value as RecorderState.Active).elapsedMs).isEqualTo(elapsedAtPause)
        assertThat(source.stopped).isAtLeast(1)

        assertThat(r.resume()).isTrue()
        advanceTimeBy(1_000)
        assertThat(r.pause()).isTrue()
        r.resume()
        advanceTimeBy(500)
        assertThat(r.pause()).isFalse() // both user pauses used
        assertThat(r.pause(auto = true)).isTrue() // backgrounding still pauses
        val auto = r.state.value as RecorderState.Active
        assertThat(auto.autoPaused).isTrue()
        assertThat(auto.pausesLeft).isEqualTo(0)
        r.resume()
        advanceTimeBy(500)
        val result = r.stop()!!
        assertThat(result.durationMs).isIn(2_800L..3_200L)
    }

    @Test
    fun stopWhilePausedFinishes() = runTest {
        val r = recorder()
        r.start(file, 180_000)
        advanceTimeBy(1_000)
        r.pause()
        runCurrent()
        val result = r.stop()
        assertThat(result).isNotNull()
        assertThat(sink.finished).isTrue()
    }

    @Test
    fun discardDeletesTheFile() = runTest {
        val r = recorder()
        r.start(file, 180_000)
        advanceTimeBy(1_000)
        r.discard()
        assertThat(file.exists()).isFalse()
        assertThat(sink.aborted).isTrue()
        assertThat(r.state.value).isEqualTo(RecorderState.Idle)
    }

    @Test
    fun micThatWontOpenRefusesToStart() = runTest {
        val r = recorder(FakeSource(failOnStart = true))
        assertThat(r.start(file, 180_000)).isFalse()
        assertThat(r.state.value).isEqualTo(RecorderState.Idle)
    }

    @Test
    fun secondStartWhileRecordingIsRefused() = runTest {
        val r = recorder()
        assertThat(r.start(file, 180_000)).isTrue()
        assertThat(r.start(file, 180_000)).isFalse()
        r.stop()
    }

    @Test
    fun micCheckReportsAQuietRoom() = runTest {
        val r = recorder(FakeSource(silentFrames = 1_000))
        val check = r.micCheck(500)!!
        assertThat(check.noisy).isFalse()
        assertThat(check.bars).isAtLeast(1)
    }
}
