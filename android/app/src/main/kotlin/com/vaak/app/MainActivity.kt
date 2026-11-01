package com.vaak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vaak.data.util.AppVisibility
import com.vaak.ui.theme.VaakTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaakTheme {
                // Onboarding gate, then the tab graph. A talk recovered after a crash (S09 state 8) waits as a card
                // on Today (TalkStatusSource.recovered), which opens RecoveredRoute.
                VaakRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onStart()
    }

    override fun onStop() {
        AppVisibility.onStop()
        super.onStop()
    }
}
