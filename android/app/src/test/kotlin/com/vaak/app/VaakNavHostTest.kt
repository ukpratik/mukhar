package com.vaak.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        compose.setContent { VaakTheme { VaakNavHost() } }
        compose.onAllNodesWithText("Today").assertCountEquals(2) // tab label + page title
        compose.onNodeWithText("Your streak, today's talk, revision and words will live here.").assertExists()
        for (tab in listOf("Practice", "Words", "Me")) compose.onNodeWithText(tab).assertExists()

        compose.onNodeWithText("Words").performClick()
        compose.onNodeWithText("Word Gym, your word bank and quick revision will live here.").assertExists()
        compose.onNodeWithText("Me").performClick()
        compose.onNodeWithText("Progress, settings and Premium will live here.").assertExists()
    }
}
