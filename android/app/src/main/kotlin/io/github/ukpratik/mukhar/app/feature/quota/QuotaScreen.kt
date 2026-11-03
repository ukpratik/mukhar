package io.github.ukpratik.mukhar.app.feature.quota

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.mukhar.app.R
import io.github.ukpratik.mukhar.app.feature.onboarding.CtaBar
import io.github.ukpratik.mukhar.app.feature.onboarding.OutlineCta
import io.github.ukpratik.mukhar.app.feature.onboarding.PrimaryCta
import io.github.ukpratik.mukhar.app.feature.onboarding.timeLabel
import io.github.ukpratik.mukhar.app.feature.talk.common.ArtCircle
import io.github.ukpratik.mukhar.app.feature.talk.common.InfoNote
import io.github.ukpratik.mukhar.ui.theme.MukharCard
import io.github.ukpratik.mukhar.ui.theme.MukharSpace
import java.time.LocalTime

data class QuotaUiState(
    val loading: Boolean = true,
    val premium: Boolean = false,
    /** When the next coached talk opens: 03:00 IST in the user's local time (US-060). */
    val opensAt: LocalTime = LocalTime.of(3, 0),
    val due: Int = 0,
    val checksLeft: Int = 0,
    val checksLimit: Int = 0,
    /** A talk the server refused waits, saved, for the next quota day. */
    val talkWaiting: Boolean = false,
)

data class QuotaActions(
    val onClose: () -> Unit = {},
    val onTomorrow: () -> Unit = {},
    val onPremium: () -> Unit = {},
    val onRevise: () -> Unit = {},
    val onGym: () -> Unit = {},
)

@Composable
fun QuotaScreen(
    onClose: () -> Unit,
    onPremium: () -> Unit,
    onRevise: () -> Unit,
    onGym: () -> Unit,
    viewModel: QuotaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuotaContent(state, QuotaActions(onClose = onClose, onTomorrow = onClose, onPremium = onPremium, onRevise = onRevise, onGym = onGym))
}

/** S17: friendly, honest, easy to leave. "Come back tomorrow" is primary, Premium secondary, Close always visible. */
@Composable
fun QuotaContent(state: QuotaUiState, actions: QuotaActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().testTag(TAG_QUOTA)) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = actions.onClose, modifier = Modifier.testTag(TAG_QUOTA_CLOSE)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MukharSpace.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.loading) return@Column
            Spacer(Modifier.heightIn(min = 12.dp))
            ArtCircle(Icons.Outlined.Bedtime, size = 88.dp)
            Text(
                stringResource(if (state.premium) R.string.quota_title_premium else R.string.quota_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp).semantics { heading() },
            )
            Text(
                stringResource(if (state.premium) R.string.quota_lead_premium else R.string.quota_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.quota_opens_at, timeLabel(state.opensAt)),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp).testTag(TAG_QUOTA_RESET),
                    )
                }
            }
            if (state.talkWaiting) {
                Spacer(Modifier.heightIn(min = 14.dp))
                InfoNote(Icons.Outlined.Info, stringResource(R.string.quota_talk_waiting))
            }
            StillFree(state, actions)
        }
        CtaBar {
            PrimaryCta(stringResource(R.string.quota_tomorrow), actions.onTomorrow, modifier = Modifier.testTag(TAG_QUOTA_TOMORROW))
            if (!state.premium) {
                OutlineCta(stringResource(R.string.quota_try_premium), actions.onPremium, modifier = Modifier.padding(top = 6.dp).testTag(TAG_QUOTA_PREMIUM))
                Text(
                    stringResource(R.string.quota_fine),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StillFree(state: QuotaUiState, actions: QuotaActions) {
    // Rows with nothing to do are hidden (S17 notes); Word Gym always has reviews or checks.
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            stringResource(R.string.quota_still_free),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).semantics { heading() },
        )
        MukharCard(Modifier.fillMaxWidth()) {
            if (state.due > 0) {
                FreeRow(Icons.Outlined.AutoStories, stringResource(R.string.quota_revise), pluralStringResource(R.plurals.quota_due, state.due, state.due), actions.onRevise)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            FreeRow(
                Icons.Outlined.FitnessCenter,
                stringResource(R.string.quota_gym),
                if (state.premium) stringResource(R.string.quota_checks_unlimited) else pluralStringResource(R.plurals.quota_checks_left, state.checksLimit, state.checksLeft, state.checksLimit),
                actions.onGym,
            )
        }
    }
}

@Composable
private fun FreeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        // Title and count stack in one weighted column, so neither is squeezed at large font sizes.
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

const val TAG_QUOTA = "quota"
const val TAG_QUOTA_CLOSE = "quota_close"
const val TAG_QUOTA_RESET = "quota_reset"
const val TAG_QUOTA_TOMORROW = "quota_tomorrow"
const val TAG_QUOTA_PREMIUM = "quota_premium"
