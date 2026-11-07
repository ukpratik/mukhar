package io.github.ukpratik.mukhar.data.repo

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.TestStores
import io.github.ukpratik.mukhar.data.debug.CannedChecks
import io.github.ukpratik.mukhar.data.debug.DebugControls
import io.github.ukpratik.mukhar.data.debug.FakeAnalysisPipeline
import io.github.ukpratik.mukhar.data.net.AppError
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.util.IdGenerator
import io.github.ukpratik.mukhar.data.work.SentenceCheckRequest
import io.github.ukpratik.mukhar.data.work.SentenceCheckResult
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.quota.QuotaMirror
import io.github.ukpratik.mukhar.domain.words.SentenceVerdict
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** US-023: outcome mapping, the client quota mirror (10 free a day, server wins) and the clip never kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SentenceCheckerTest {
    private val stores = TestStores()
    private val clock = FixedClock(Instant.parse("2026-11-06T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val pipeline = ScriptedPipeline()
    private val premium = MutableStateFlow(false)
    private var n = 0
    private val entitlements = object : Entitlements {
        override val isPremium = premium
    }
    private val ids = IdGenerator { "00000000-0000-4000-8000-%012d".format(++n) }

    // The quota cases cover Mukhar Cloud's rules (kept, off in R1), so the server quota is switched on for them.
    private val checker = SentenceChecker(pipeline, stores.settings, entitlements, clock, ids, serverQuota = { true })
    private val dir: File = Files.createTempDirectory("chk").toFile()

    @After
    fun tearDown() = stores.close()

    private fun clip() = File(dir, "c${n}.aac").apply { writeBytes(ByteArray(64)) }

    private fun answer(result: String, found: Boolean, transcript: String = "That call was pivotal") =
        Outcome.Ok(SentenceCheckResult(transcript, found, result, "fixed", "example", "note"))

    @Test
    fun mapsTheThreeOutcomesAndCountsChecks() = runTest {
        pipeline.checkAnswers += answer("ok", true)
        pipeline.checkAnswers += answer("almost", true)
        pipeline.checkAnswers += answer("no", false, "I moved the meeting")
        val verdicts = (1..3).map {
            val audio = clip()
            val r = checker.check(Lang.EN, "pivotal", "very important", audio, 6_000) as Outcome.Ok
            assertThat(audio.exists()).isFalse() // the clip is never kept
            r.value
        }
        assertThat(verdicts.map { it.verdict }).containsExactly(SentenceVerdict.NICELY_USED, SentenceVerdict.ALMOST, SentenceVerdict.NOT_YET).inOrder()
        assertThat(verdicts.last().quota).isEqualTo(CheckQuota(3, 10))
        assertThat(pipeline.checkCalls.first().targetWord).isEqualTo("pivotal")
        assertThat(pipeline.checkCalls.map { it.clientSessionId }.toSet()).hasSize(3)
    }

    @Test
    fun okWithoutTheWordIsOnlyAlmost() = runTest {
        pipeline.checkAnswers += answer("ok", false, "That call was very important")
        val r = checker.check(Lang.EN, "pivotal", "very important", clip(), 6_000) as Outcome.Ok
        assertThat(r.value.verdict).isEqualTo(SentenceVerdict.ALMOST)
    }

    @Test
    fun theTenthCheckIsTheLastFreeOneAndTheMirrorResetsAt0300Ist() = runTest {
        pipeline.checkAnswers += answer("ok", true)
        repeat(10) { assertThat(checker.check(Lang.EN, "pivotal", "", clip(), 5_000)).isInstanceOf(Outcome.Ok::class.java) }
        assertThat(checker.quota.first()).isEqualTo(CheckQuota(10, 10))
        val denied = checker.check(Lang.EN, "pivotal", "", clip(), 5_000)
        assertThat((denied as Outcome.Err).error).isInstanceOf(AppError.Quota::class.java)
        assertThat(pipeline.checkCalls).hasSize(10) // no call once the mirror says no
        clock.instant = QuotaMirror.resetsAt(QuotaMirror.quotaDay(clock.now())).plusSeconds(1)
        assertThat(checker.quota.first()).isEqualTo(CheckQuota(0, 10))
        premium.value = true
        assertThat(checker.quota.first().limit).isEqualTo(200)
    }

    @Test
    fun theServerIsTheAuthorityAndOfflineCountsNothing() = runTest {
        pipeline.checkAnswers += Outcome.Err(AppError.Offline)
        assertThat((checker.check(Lang.EN, "pivotal", "", clip(), 5_000) as Outcome.Err).error).isEqualTo(AppError.Offline)
        assertThat(checker.quota.first().used).isEqualTo(0)
        pipeline.checkAnswers.clear()
        pipeline.checkAnswers += Outcome.Err(AppError.Quota(null))
        checker.check(Lang.EN, "pivotal", "", clip(), 5_000)
        assertThat(checker.quota.first().exhausted).isTrue() // a 429 fills the local mirror
        clock.advanceBy(Duration.ofHours(1))
    }

    @Test
    fun fakePipelineCyclesTheCannedAnswersWithHonestWordFound() = runTest {
        val fake = FakeAnalysisPipeline(DebugControls(ApplicationProvider.getApplicationContext()).also { c -> c.update { it.copy(delayMs = 0) } })
        val results = (0 until 3).map {
            (fake.checkSentence(SentenceCheckRequest("id$it", Lang.EN, "pivotal", "", 4_000), clip()) as Outcome.Ok).value
        }
        assertThat(results.map { it.result }).containsExactly("ok", "almost", "no").inOrder()
        assertThat(results.map { it.wordFound }).containsExactly(true, true, false).inOrder()
        assertThat(CannedChecks.answer(Lang.HI, "सटीक", 0).wordFound).isTrue()
    }

    @Test
    fun onThePhoneAndOnOwnKeysThereIsNoDailyLimit() = runTest {
        val local = SentenceChecker(pipeline, stores.settings, entitlements, clock, ids)
        assertThat(local.quota.first()).isEqualTo(CheckQuota.UNLIMITED)
        assertThat(CheckQuota.UNLIMITED.exhausted).isFalse()
        pipeline.checkAnswers += answer("ok", true)
        repeat(12) {
            val r = local.check(Lang.EN, "pivotal", "very important", clip(), 4_000)
            assertThat(r).isInstanceOf(Outcome.Ok::class.java)
        }
        // Nothing was counted: the mirror isn't used without Mukhar Cloud.
        assertThat(stores.settings.checksUsed.first().second).isEqualTo(0)
        assertThat(local.quota.first().exhausted).isFalse()
    }
}
