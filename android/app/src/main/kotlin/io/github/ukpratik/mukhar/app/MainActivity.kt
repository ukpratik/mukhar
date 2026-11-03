package io.github.ukpratik.mukhar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ukpratik.mukhar.data.util.AppVisibility
import io.github.ukpratik.mukhar.ui.theme.MukharTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MukharTheme {
                // Onboarding gate, then the tab graph. A talk recovered after a crash (S09 state 8) waits as a card
                // on Today (TalkStatusSource.recovered), which opens RecoveredRoute.
                MukharRoot()
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
