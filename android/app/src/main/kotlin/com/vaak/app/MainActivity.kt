package com.vaak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.vaak.app.nav.RecoveredRoute
import com.vaak.app.nav.VaakNavHost
import com.vaak.data.repo.TalkRepository
import com.vaak.data.util.AppVisibility
import com.vaak.ui.theme.VaakTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var talks: TalkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaakTheme {
                val nav = rememberNavController()
                val shown = remember { mutableSetOf<String>() }
                // S09 state 8: a talk saved when the app closed mid-recording is offered before Today.
                LaunchedEffect(Unit) {
                    talks.observeRecovered().collect { s ->
                        if (s != null && shown.add(s.id)) nav.navigate(RecoveredRoute(s.id))
                    }
                }
                VaakNavHost(nav)
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
