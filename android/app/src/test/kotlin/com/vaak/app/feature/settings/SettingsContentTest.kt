package com.vaak.app.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.vaak.data.prefs.GoalTrack
import com.vaak.data.prefs.ReminderSettings
import com.vaak.data.prefs.Routine
import com.vaak.data.prefs.Settings
import com.vaak.domain.model.Lang
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/** S19 (US-063): the analytics switch starts off, and Delete all my data needs the tick before it deletes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings = Settings(
        firstName = "Ananya", langs = setOf(Lang.EN, Lang.HI), tracks = setOf(GoalTrack.INTERVIEWS, GoalTrack.PROFESSIONAL),
        reminder = ReminderSettings(LocalTime.of(20, 30), Routine.DINNER), onboardingDone = true,
    )

    @Test
    fun defaultListHasTheGroupsAndAnalyticsOff() {
        compose.setContent { VaakTheme { SettingsContent(SettingsUiState(loading = false, settings = settings), SettingsActions()) } }
        compose.onNodeWithText("Practice languages").assertExists()
        compose.onNodeWithText("New words a day").performScrollTo()
        compose.onNodeWithText("Stutter-friendly mode").performScrollTo()
        compose.onNodeWithTag(TAG_SWITCH_ANALYTICS).performScrollTo().assertIsOff()
        compose.onNodeWithText("Free plan").performScrollTo()
        compose.onNodeWithText("Privacy policy").performScrollTo()
    }

    @Test
    fun deleteNeedsTheTickThenConfirms() {
        var state by mutableStateOf(SettingsUiState(loading = false, settings = settings))
        var confirmed = 0
        val actions = SettingsActions(
            onDeleteOpen = { state = state.copy(delete = DeleteStage.CONFIRM, talkCount = 46, wordCount = 128) },
            onDeleteUnderstood = { state = state.copy(deleteUnderstood = it) },
            onDeleteConfirm = { confirmed++; state = state.copy(delete = DeleteStage.DONE) },
            onDeleteCancel = { state = state.copy(delete = DeleteStage.NONE) },
        )
        compose.setContent { VaakTheme { SettingsContent(state, actions) } }
        compose.onNodeWithTag(TAG_DELETE).performScrollTo().performClick()
        compose.onNodeWithText("Delete all your data?").assertExists()
        compose.onNodeWithText("46 talks, reports and any saved audio").assertExists()
        compose.onNodeWithText("128 words, saved lines and your streak").assertExists()
        compose.onNodeWithText("Your Google Play subscription isn't cancelled by this. Manage it in Play.").assertExists()
        compose.onNodeWithTag(TAG_DELETE_CONFIRM).assertIsNotEnabled()
        compose.onNodeWithTag(TAG_DELETE_CHECK).performClick()
        compose.onNodeWithTag(TAG_DELETE_CONFIRM).assertIsEnabled().performClick()
        assertThat(confirmed).isEqualTo(1)
        compose.onNodeWithText("Your data is deleted").assertExists()
        compose.onNodeWithText("Start fresh").assertExists()
    }
}
