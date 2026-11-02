package com.vaak.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.composable
import com.vaak.app.feature.PlaceholderScreen
import com.vaak.app.nav.MeRoute
import com.vaak.app.nav.TodayRoute
import com.vaak.app.nav.VaakNavHost
import com.vaak.app.nav.WordsRoute
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
        // Today is stubbed with the M0 placeholder (the real one needs Hilt; see TodayContentTest).
        compose.setContent {
            VaakTheme {
                VaakNavHost(
                    todayScreen = { composable<TodayRoute> { PlaceholderScreen(R.string.tab_today, R.string.placeholder_today) } },
                    wordsScreen = { composable<WordsRoute> { PlaceholderScreen(R.string.tab_words, R.string.placeholder_coming) } },
                    meScreen = { composable<MeRoute> { PlaceholderScreen(R.string.tab_me, R.string.placeholder_coming) } },
                )
            }
        }
        compose.onAllNodesWithText("Today").assertCountEquals(2) // tab label + page title
        compose.onNodeWithText("Your streak, today's talk, revision and words will live here.").assertExists()
        for (tab in listOf("Practice", "Words", "Me")) compose.onNodeWithText(tab).assertExists()

        compose.onNodeWithText("Words").performClick()
        compose.onAllNodesWithText("Words").assertCountEquals(2) // tab label + the (stubbed) page title
        compose.onNodeWithText("Me").performClick()
        compose.onAllNodesWithText("Me").assertCountEquals(2) // tab label + the (stubbed) Progress title
    }
}
