package com.vaak.app.feature.me

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.vaak.app.R
import com.vaak.app.debug.DebugMenuEntry
import com.vaak.app.feature.PlaceholderScreen

@Composable
fun MeScreen() {
    Column {
        PlaceholderScreen(title = R.string.tab_me, body = R.string.placeholder_me)
        DebugMenuEntry() // debug builds only (src/debug); a no-op in release
    }
}
