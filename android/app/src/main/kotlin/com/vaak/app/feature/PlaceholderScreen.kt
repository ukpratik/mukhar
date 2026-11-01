package com.vaak.app.feature

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.vaak.app.R
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme

/** M0 placeholder for a tab: page title (headlineLarge) and one VaakCard. Replaced screen by screen from M2. */
@Composable
fun PlaceholderScreen(@StringRes title: Int, @StringRes body: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = VaakSpace.gutter, vertical = VaakSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        VaakCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(VaakSpace.l), verticalArrangement = Arrangement.spacedBy(VaakSpace.s)) {
                Text(
                    text = stringResource(R.string.placeholder_coming).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = VaakTheme.colors.accentInk,
                )
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderLightPreview() {
    VaakTheme(darkTheme = false) { PlaceholderScreen(R.string.tab_today, R.string.placeholder_today) }
}

@Preview(showBackground = true, backgroundColor = 0xFF15131F)
@Composable
private fun PlaceholderDarkPreview() {
    VaakTheme(darkTheme = true) { PlaceholderScreen(R.string.tab_today, R.string.placeholder_today) }
}
