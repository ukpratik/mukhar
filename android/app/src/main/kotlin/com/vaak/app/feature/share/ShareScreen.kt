package com.vaak.app.feature.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.onboarding.CtaBar
import com.vaak.app.feature.onboarding.PrimaryCta
import com.vaak.app.feature.talk.common.CenterState
import com.vaak.ui.theme.VaakSpace
import java.io.File

data class ShareActions(
    val onClose: () -> Unit = {},
    val onShowStreak: (Boolean) -> Unit = {},
    val onAddName: (Boolean) -> Unit = {},
    val onShare: (ShareCardText, Bitmap?) -> Unit = { _, _ -> },
)

@Composable
fun ShareScreen(onClose: () -> Unit, viewModel: ShareViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ShareContent(
        state,
        ShareActions(
            onClose = onClose,
            onShowStreak = viewModel::onShowStreak,
            onAddName = viewModel::onAddName,
            onShare = { _, bmp ->
                if (bmp != null && context.shareImage(bmp)) viewModel.onShared()
            },
        ),
    )
}

/** S21: the preview is the image itself, so what you see is exactly what is shared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContent(state: ShareUiState, actions: ShareActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().testTag(TAG_SHARE)) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) },
            navigationIcon = { IconButton(onClick = actions.onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) } },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        val model = state.model
        if (state.loading) return@Column
        if (model == null) {
            CenterState(null, stringResource(R.string.share_none_title), stringResource(R.string.share_none_body), Modifier.padding(horizontal = VaakSpace.gutter))
            return@Column
        }
        val context = LocalContext.current
        val text = ShareCardText.of(model, androidx.compose.ui.platform.LocalResources.current)
        val inPreview = LocalInspectionMode.current
        val bitmap = remember(text) { if (inPreview) null else runCatching { ShareCardRenderer.render(context, text, model.lang == com.vaak.domain.model.Lang.HI) }.getOrNull() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter)) {
            val desc = stringResource(R.string.share_image_a11y, text.all().joinToString(". "))
            val imageModifier = Modifier.fillMaxWidth().aspectRatio(ShareCardRenderer.WIDTH / ShareCardRenderer.HEIGHT.toFloat())
                .clip(MaterialTheme.shapes.extraLarge).testTag(TAG_SHARE_IMAGE).semantics { contentDescription = desc }
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = null, modifier = imageModifier)
            } else {
                Column(imageModifier.background(MaterialTheme.colorScheme.primary)) {}
            }
            Column(Modifier.padding(top = 14.dp)) {
                val streak = state.streak
                if (streak != null) {
                    SwitchRow(
                        title = stringResource(R.string.share_show_streak),
                        sub = if (state.showStreak) pluralStringResource(R.plurals.share_streak_days, streak, streak) else stringResource(R.string.share_hidden),
                        checked = state.showStreak,
                        onChange = actions.onShowStreak,
                        tag = TAG_SHARE_STREAK,
                    )
                }
                if (state.firstName != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        title = stringResource(R.string.share_add_name),
                        sub = stringResource(R.string.share_name_sub, state.firstName),
                        checked = state.addName,
                        onChange = actions.onAddName,
                        tag = TAG_SHARE_NAME,
                    )
                }
            }
        }
        CtaBar {
            PrimaryCta(stringResource(R.string.share_cta), { actions.onShare(text, bitmap) }, leading = Icons.Rounded.Share, modifier = Modifier.testTag(TAG_SHARE_CTA))
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Writes the PNG to `cache/share/` and opens the Android share sheet through the FileProvider. The sheet already
 * offers saving the image, so there's no separate Save button (design review).
 */
fun Context.shareImage(bitmap: Bitmap): Boolean = runCatching {
    val dir = File(cacheDir, SHARE_DIR).apply { mkdirs() }
    val file = File(dir, FILE_NAME)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    val uri = FileProvider.getUriForFile(this, "$packageName.share", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    startActivity(Intent.createChooser(send, getString(R.string.share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

private const val SHARE_DIR = "share"
private const val FILE_NAME = "vaak-progress.png"
private const val PNG_QUALITY = 100
const val TAG_SHARE = "share"
const val TAG_SHARE_IMAGE = "share_image"
const val TAG_SHARE_STREAK = "share_streak"
const val TAG_SHARE_NAME = "share_name"
const val TAG_SHARE_CTA = "share_cta"
