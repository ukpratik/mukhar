package io.github.ukpratik.mukhar.app.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.ukpratik.mukhar.app.feature.plan.PlanActions
import io.github.ukpratik.mukhar.app.feature.plan.PlanContent
import io.github.ukpratik.mukhar.app.feature.plan.PlanFixtures
import io.github.ukpratik.mukhar.app.feature.progress.ProgressActions
import io.github.ukpratik.mukhar.app.feature.progress.ProgressContent
import io.github.ukpratik.mukhar.app.feature.progress.ProgressFixtures
import io.github.ukpratik.mukhar.app.feature.progress.ProgressMapper
import io.github.ukpratik.mukhar.app.feature.quota.QuotaActions
import io.github.ukpratik.mukhar.app.feature.quota.QuotaContent
import io.github.ukpratik.mukhar.app.feature.quota.QuotaUiState
import io.github.ukpratik.mukhar.app.feature.settings.SettingsActions
import io.github.ukpratik.mukhar.app.feature.settings.SettingsContent
import io.github.ukpratik.mukhar.app.feature.settings.SettingsUiState
import io.github.ukpratik.mukhar.app.feature.share.ShareActions
import io.github.ukpratik.mukhar.app.feature.share.ShareContent
import io.github.ukpratik.mukhar.app.feature.share.ShareUiState
import io.github.ukpratik.mukhar.app.feature.talk.TalkFixtures
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportActions
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportContent
import io.github.ukpratik.mukhar.app.feature.today.TodayActions
import io.github.ukpratik.mukhar.app.feature.today.TodayContent
import io.github.ukpratik.mukhar.app.feature.today.TodayFixtures
import io.github.ukpratik.mukhar.app.testing.A11yChecks.assertAccessible
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.data.prefs.ReminderSettings
import io.github.ukpratik.mukhar.data.prefs.Routine
import io.github.ukpratik.mukhar.data.prefs.Settings
import io.github.ukpratik.mukhar.domain.billing.EntitlementSnapshot
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.domain.progress.TrendRange
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalTime

/**
 * NFR-008 / TC-263 / TC-265 across the main screens, in light and dark and at 100% and 200% font scale, on a small
 * phone (360 × 640 dp): every interactive node is labelled and ≥ 48 dp, and no laid-out text is clipped
 * ([io.github.ukpratik.mukhar.app.testing.A11yChecks], the ATF stand-in, D-036). Per-screen semantics live in each feature's tests.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenA11yChecksTest(private val fontScale: Float, private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    private fun check(content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { MukharTheme(darkTheme = dark) { content() } }
        compose.assertAccessible()
    }

    @Test fun todayFirstDay() = check { TodayContent(TodayFixtures.firstDay, TodayActions()) }

    @Test fun todayMidStreak() = check { TodayContent(TodayFixtures.midDay, TodayActions()) }

    @Test fun resultsEnglish() = check { ReportContent(TalkFixtures.report(), {}, ReportActions({}, {}, {}, {})) }

    @Test fun resultsHindi() = check { ReportContent(TalkFixtures.report(lang = Lang.HI), {}, ReportActions({}, {}, {}, {})) }

    @Test fun resultsNotesPending() =
        check { ReportContent(TalkFixtures.report(withFeedback = false), {}, ReportActions({}, {}, {}, {})) }

    @Test fun paywall() = check { PlanContent(PlanFixtures.paywall(), PlanActions()) }

    @Test fun planTrial() = check { PlanContent(PlanFixtures.trialFacts(), PlanActions()) }

    @Test fun quota() = check { QuotaContent(QuotaUiState(loading = false), QuotaActions()) }

    @Test fun settings() = check { SettingsContent(SettingsUiState(loading = false, settings = SETTINGS), SettingsActions()) }

    @Test fun progress() = check {
        val now = Instant.parse("2026-11-06T14:00:00Z")
        val state = ProgressMapper.map(
            ProgressFixtures.many, null, TrendRange.TWELVE, EntitlementSnapshot.FREE, now, ProgressFixtures.zone, talksEnabled = true,
        )
        ProgressContent(state, ProgressActions())
    }

    @Test fun shareEmpty() = check { ShareContent(ShareUiState(loading = false), ShareActions()) }

    companion object {
        private val SETTINGS = Settings(
            firstName = "Ananya", langs = setOf(Lang.EN, Lang.HI), tracks = setOf(GoalTrack.INTERVIEWS, GoalTrack.PROFESSIONAL),
            reminder = ReminderSettings(LocalTime.of(20, 30), Routine.DINNER), onboardingDone = true,
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "font {0}, dark {1}")
        fun params() = listOf(arrayOf<Any>(1f, false), arrayOf<Any>(2f, false), arrayOf<Any>(2f, true))
    }
}
