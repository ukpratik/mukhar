package io.github.ukpratik.mukhar.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.domain.talk.RecordingRetention
import java.util.Locale

/** "0.7 MB", "42 MB", "1.2 GB" for recording sizes (US-080). */
@Composable
fun sizeLabel(bytes: Long): String {
    val mb = bytes.toDouble() / RecordingRetention.MB
    return when {
        mb >= 1024 -> stringResource(R.string.gb_value, "%.1f".format(Locale.US, mb / 1024))
        mb >= 10 || mb == 0.0 -> stringResource(R.string.mb_value, "%.0f".format(Locale.US, mb))
        else -> stringResource(R.string.mb_value, "%.1f".format(Locale.US, mb))
    }
}

/** The limit options: "100 MB", "500 MB", "1 GB", "2 GB". */
@Composable
fun capLabel(mb: Int): String =
    if (mb >= 1_000) stringResource(R.string.gb_value, (mb / 1_000).toString()) else stringResource(R.string.mb_value, mb.toString())
