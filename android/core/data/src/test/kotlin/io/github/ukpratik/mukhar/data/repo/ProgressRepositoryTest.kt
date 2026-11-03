package io.github.ukpratik.mukhar.data.repo

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.TestStores
import io.github.ukpratik.mukhar.data.db.MetricsEntity
import io.github.ukpratik.mukhar.data.db.StreakDayEntity
import io.github.ukpratik.mukhar.data.db.TalkSessionEntity
import io.github.ukpratik.mukhar.data.db.WordEntity
import io.github.ukpratik.mukhar.domain.FixedClock
import io.github.ukpratik.mukhar.domain.insight.InsightRules
import io.github.ukpratik.mukhar.domain.model.Lang
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** US-050/051 on Room: analysed talks → progress data, and insights persisted once in `insight` (seen flag). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressRepositoryTest {
    private val stores = TestStores()
    private val clock = FixedClock(Instant.parse("2026-11-06T14:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val repo = ProgressRepository(stores.db.progressDao(), stores.db.habitDao(), clock)

    @After
    fun tearDown() = stores.close()

    private suspend fun talk(i: Int, kind: String = "talk", fillers: Double = 4.0, tokens: Int = 80, status: String = "feedback_ready", lang: Lang = Lang.EN) {
        val at = clock.now().minus(Duration.ofDays(20L - i)).toEpochMilli()
        val id = "t$i"
        stores.db.talkDao().insertSession(
            TalkSessionEntity(
                id = id, kind = kind, parentId = null, lang = lang.code, track = "everyday", promptId = "p", createdAt = at,
                zoneId = "Asia/Kolkata", durationMs = 90_000, speechMs = 80_000, snrDb = 20.0, status = status, leaseUntil = null,
                attempts = 0, errorCode = null, audioPath = null, audioKept = false, serverSessionId = "s$i", quotaDay = null, updatedAt = at,
            ),
        )
        stores.db.talkDao().upsertMetrics(
            MetricsEntity(id, 140.0, "comfortable", tokens, 8, fillers, 2, 1.0, 1, if (tokens >= 100) 0.7 else null, 80_000, 1),
        )
    }

    @Test
    fun observesTalksBaselineAndTotals() = runTest {
        talk(0, kind = "baseline", fillers = 5.8)
        talk(1, fillers = 3.0)
        talk(2, kind = "retake", fillers = 2.5)
        talk(3, fillers = 2.0, status = "discarded")
        stores.db.wordDao().insertWord(WordEntity("en:pivotal", "en", "pivotal", "m", "e", "formal", "", "curated", null, 0))
        val data = repo.observe().first()
        assertThat(data.records.map { it.id }).containsExactly("t0", "t1", "t2").inOrder()
        assertThat(data.talks.map { it.id }).containsExactly("t1")
        assertThat(data.baseline?.id).isEqualTo("t0")
        assertThat(data.latestComparable?.id).isEqualTo("t1")
        assertThat(data.wordsLearned).isEqualTo(1)
        assertThat(data.today).isEqualTo(LocalDate.of(2026, 11, 6))
    }

    @Test
    fun insightsArePersistedOnceAndDismissedForGood() = runTest {
        // 10 talks with fillers falling from 6 to 2 a minute, one of them over 100 tokens.
        (0 until 10).forEach { i -> talk(i, fillers = if (i < 3) 6.0 else 2.0, tokens = if (i == 4) 120 else 80) }
        (1..7).forEach { d ->
            stores.db.habitDao().insertStreakDay(StreakDayEntity(LocalDate.of(2026, 10, d).toString(), "talk", 0, "Asia/Kolkata"))
        }

        val first = repo.evaluateInsights()
        assertThat(first).containsExactly(
            InsightRules.fillersDownKey(Lang.EN), InsightRules.talksKey(10), InsightRules.FIRST_100_TOKENS, InsightRules.streakKey(7),
        ).inOrder()
        assertThat(repo.observe().first().insights.map { it.ruleKey to it.seen })
            .containsExactlyElementsIn(first.map { it to false })

        // Each insight fires once.
        assertThat(repo.evaluateInsights()).isEmpty()

        repo.dismissInsight(InsightRules.talksKey(10))
        val insights = repo.observe().first().insights.associate { it.ruleKey to it.seen }
        assertThat(insights[InsightRules.talksKey(10)]).isTrue()
        assertThat(insights[InsightRules.FIRST_100_TOKENS]).isFalse()
        assertThat(repo.evaluateInsights()).isEmpty()
    }
}
