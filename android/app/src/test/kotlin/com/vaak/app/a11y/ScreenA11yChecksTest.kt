package com.vaak.app.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaak.app.feature.plan.PlanActions
import com.vaak.app.feature.plan.PlanContent
import com.vaak.app.feature.plan.PlanFixtures
import com.vaak.app.feature.progress.ProgressActions
import com.vaak.app.feature.progress.ProgressContent
import com.vaak.app.feature.progress.ProgressFixtures
import com.vaak.app.feature.progress.ProgressMapper
import com.vaak.app.feature.quota.QuotaActions
import com.vaak.app.feature.quota.QuotaContent
import com.vaak.app.feature.quota.QuotaUiState
import com.vaak.app.feature.settings.SettingsActions
import com.vaak.app.feature.settings.SettingsContent
import com.vaak.app.feature.settings.SettingsUiState
import com.vaak.app.feature.share.ShareActions
import com.vaak.app.feature.share.ShareContent
import com.vaak.app.feature.share.ShareUiState
import com.vaak.app.feature.talk.TalkFixtures
import com.vaak.app.feature.talk.report.ReportActions
import com.vaak.app.feature.talk.report.ReportContent
import com.vaak.app.feature.today.TodayActions
import com.vaak.app.feature.today.TodayContent
import com.vaak.app.feature.today.TodayFixtures
import com.vaak.app.testing.A11yChecks.assertAccessible
import com.vaak.data.prefs.GoalTrack
import com.vaak.data.prefs.ReminderSettings
import com.vaak.data.prefs.Routine
import com.vaak.data.prefs.Settings
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.domain.model.Lang
import com.vaak.domain.progress.TrendRange
import com.vaak.ui.theme.VaakTheme
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
 * ([com.vaak.app.testing.A11yChecks], the ATF stand-in, D-036). Per-screen semantics live in each feature's tests.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenA11yChecksTest(private val fontScale: Float, private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    private fun check(content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { VaakTheme(darkTheme = dark) { content() } }
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
