package io.github.ukpratik.mukhar.app.feature

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
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import io.github.ukpratik.mukhar.ui.theme.MukharTheme

/** M0 placeholder for a tab: page title (headlineLarge) and one MukharCard. Replaced screen by screen from M2. */
@Composable
fun PlaceholderScreen(@StringRes title: Int, @StringRes body: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = MukharSpace.gutter, vertical = MukharSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(MukharSpace.m),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        MukharCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(MukharSpace.l), verticalArrangement = Arrangement.spacedBy(MukharSpace.s)) {
                Text(
                    text = stringResource(R.string.placeholder_coming).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MukharTheme.colors.accentInk,
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
    MukharTheme(darkTheme = false) { PlaceholderScreen(R.string.tab_today, R.string.placeholder_today) }
}

@Preview(showBackground = true, backgroundColor = 0xFF15131F)
@Composable
private fun PlaceholderDarkPreview() {
    MukharTheme(darkTheme = true) { PlaceholderScreen(R.string.tab_today, R.string.placeholder_today) }
}
