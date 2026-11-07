package io.github.ukpratik.mukhar.app.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.mukhar.data.prefs.GoalTrack
import io.github.ukpratik.mukhar.data.prefs.ReminderSettings
import io.github.ukpratik.mukhar.data.prefs.Routine
import io.github.ukpratik.mukhar.data.prefs.Settings
import io.github.ukpratik.mukhar.domain.model.Lang
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
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
        compose.setContent { MukharTheme { SettingsContent(SettingsUiState(loading = false, settings = settings), SettingsActions()) } }
        compose.onNodeWithText("Practice languages").assertExists()
        compose.onNodeWithText("New words a day").performScrollTo()
        compose.onNodeWithText("Stutter-friendly mode").performScrollTo()
        compose.onNodeWithTag(TAG_SWITCH_ANALYTICS).performScrollTo().assertIsOff()
        // D-039: no Plan row; Analysis and the "coming soon" card instead.
        compose.onNodeWithText("Free plan").assertDoesNotExist()
        compose.onNodeWithTag(TAG_MODE_DEVICE).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(TAG_MODE_CLOUD).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(TAG_CLOUD_SOON).performScrollTo()
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
        compose.setContent { MukharTheme { SettingsContent(state, actions) } }
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

    @Test
    fun goalPickerSaysWhyAFourthIsOffAndHeadingsNameTheSheet() {
        compose.setContent { MukharTheme { SettingsContent(SettingsUiState(loading = false, settings = settings), SettingsActions()) } }
        // Privacy policy says it opens the browser.
        val privacy = compose.onNodeWithText("Privacy policy").performScrollTo().fetchSemanticsNode()
        assertThat(privacy.config[SemanticsActions.OnClick].label).isEqualTo("open in browser")
        compose.onNodeWithText("Goal tracks").performScrollTo().performClick()
        compose.onNodeWithTag(TAG_PICK_MAX).assertDoesNotExist()
        compose.onNodeWithText("Public speaking").performClick()
        compose.onNodeWithTag(TAG_PICK_MAX).assertExists().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithText("You can pick up to 3").assertExists()
    }

    @Test
    fun deleteSheetSubheadsAreHeadings() {
        compose.setContent { MukharTheme { SettingsContent(SettingsUiState(loading = false, settings = settings, delete = DeleteStage.CONFIRM), SettingsActions()) } }
        compose.onNodeWithText("On this phone, right away").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("On our servers, within 30 days").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
