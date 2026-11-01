package com.vaak.app.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vaak.app.R
import com.vaak.app.feature.PlaceholderScreen
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

/** M2: the placeholder plus a temporary Today's Talk entry (straight to recording, D-020). The full S07 is M3. */
@Composable
fun TodayScreen(onTodaysTalk: () -> Unit = {}) {
    Column {
        PlaceholderScreen(title = R.string.tab_today, body = R.string.placeholder_today, modifier = Modifier.weight(1f, fill = false))
        Button(
            onClick = onTodaysTalk,
            colors = ButtonDefaults.buttonColors(containerColor = VaakTheme.colors.accent, contentColor = VaakTheme.colors.onAccent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = VaakSpace.gutter).height(54.dp),
        ) {
            Icon(Icons.Rounded.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.todays_talk))
        }
    }
}
