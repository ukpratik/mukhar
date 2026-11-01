package com.vaak.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.composable
import com.vaak.app.feature.today.TodayScreen
import com.vaak.app.nav.TodayRoute
import com.vaak.app.nav.VaakNavHost
import com.vaak.ui.theme.VaakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaakNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourTabsAndSwitching() {
        // M3: Today is stubbed with the M0 placeholder (the real one needs Hilt; see TodayContentTest).
        compose.setContent { VaakTheme { VaakNavHost(todayScreen = { composable<TodayRoute> { TodayScreen() } }) } }
        compose.onAllNodesWithText("Today").assertCountEquals(2) // tab label + page title
        compose.onNodeWithText("Your streak, today's talk, revision and words will live here.").assertExists()
        for (tab in listOf("Practice", "Words", "Me")) compose.onNodeWithText(tab).assertExists()

        compose.onNodeWithText("Words").performClick()
        compose.onNodeWithText("Word Gym, your word bank and quick revision will live here.").assertExists()
        compose.onNodeWithText("Me").performClick()
        compose.onNodeWithText("Progress, settings and Premium will live here.").assertExists()
    }
}
