package com.vaak.app.feature.plan

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaak.app.R
import com.vaak.app.feature.progress.shortDate
import com.vaak.data.billing.PlayLinks
import com.vaak.domain.billing.BillingPeriod
import com.vaak.domain.billing.EntitlementRules
import com.vaak.domain.billing.EntitlementSnapshot
import com.vaak.ui.theme.VaakTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The day-6 trial banner (US-062): the guarantee, shown even when notifications are denied. */
data class TrialBannerUi(val expiry: LocalDate, val today: LocalDate, val price: String?, val period: BillingPeriod?) {
    companion object {
        /** Null unless a converting trial is in its last 48 h and [Keep] wasn't tapped today. */
        fun from(e: EntitlementSnapshot, now: Instant, zone: ZoneId, dismissedDay: LocalDate?): TrialBannerUi? {
            if (!EntitlementRules.bannerVisible(e, now)) return null
            val today = now.atZone(zone).toLocalDate()
            if (dismissedDay == today) return null
            val expiry = Instant.ofEpochMilli(e.expiryMs ?: return null).atZone(zone).toLocalDate()
            return TrialBannerUi(expiry, today, e.priceLabel, e.plan?.period)
        }
    }
}

@Composable
fun TrialBanner(banner: TrialBannerUi, onKeep: () -> Unit, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val date = shortDate(banner.expiry)
    val title = when (banner.expiry.toEpochDay() - banner.today.toEpochDay()) {
        0L -> stringResource(R.string.trial_banner_today)
        1L -> stringResource(R.string.trial_banner_tomorrow)
        else -> stringResource(R.string.trial_banner_on, date)
    }
    val body = when {
        banner.price != null && banner.period == BillingPeriod.MONTH -> stringResource(R.string.trial_banner_body_month, banner.price, date)
        banner.price != null && banner.period == BillingPeriod.YEAR -> stringResource(R.string.trial_banner_body_year, banner.price, date)
        else -> stringResource(R.string.trial_banner_body, date)
    }
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(VaakTheme.colors.accentTint)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 2.dp).testTag(TAG_TRIAL_BANNER)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Event, null, tint = VaakTheme.colors.accentInk, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 8.dp))
            Row {
                TextButton(onClick = onKeep) { Text(stringResource(R.string.trial_keep)) }
                val manage = stringResource(R.string.trial_manage_a11y)
                TextButton(onClick = onManage, modifier = Modifier.semantics { contentDescription = manage }) {
                    Text(stringResource(R.string.trial_manage))
                }
            }
        }
    }
}

/** Manage: one tap to Play's subscription page for Vaak (US-062). */
fun Context.openPlaySubscriptions() {
    val intent = Intent(Intent.ACTION_VIEW, PlayLinks.manageSubscription(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser or Play Store (emulators): nothing to open.
    }
}

const val TAG_TRIAL_BANNER = "trial_banner"
