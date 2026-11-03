package io.github.ukpratik.mukhar.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.composable
import io.github.ukpratik.mukhar.app.feature.PlaceholderScreen
import io.github.ukpratik.mukhar.app.nav.MeRoute
import io.github.ukpratik.mukhar.app.nav.TodayRoute
import io.github.ukpratik.mukhar.app.nav.MukharNavHost
import io.github.ukpratik.mukhar.app.nav.WordsRoute
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MukharNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourTabsAndSwitching() {
        // Today is stubbed with the M0 placeholder (the real one needs Hilt; see TodayContentTest).
        compose.setContent {
            MukharTheme {
                MukharNavHost(
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
