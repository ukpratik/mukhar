package com.vaak.app.feature.plan

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaak.app.R
import com.vaak.app.feature.onboarding.CtaBar
import com.vaak.app.feature.onboarding.OutlineCta
import com.vaak.app.feature.onboarding.PrimaryCta
import com.vaak.app.feature.onboarding.TextCta
import com.vaak.app.feature.onboarding.largeFont
import com.vaak.app.feature.progress.shortDate
import com.vaak.app.feature.settings.Links
import com.vaak.app.feature.talk.common.InfoNote
import com.vaak.data.billing.PlanOffer
import com.vaak.domain.billing.BillingPeriod
import com.vaak.domain.billing.Plan
import com.vaak.domain.billing.Prices
import com.vaak.domain.billing.SubscriptionStatus
import com.vaak.ui.theme.VaakCard
import com.vaak.ui.theme.VaakSpace
import com.vaak.ui.theme.VaakTheme
import java.time.Instant
import java.time.ZoneId

data class PlanActions(
    val onClose: () -> Unit = {},
    val onSelect: (Plan) -> Unit = {},
    val onBuy: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onKeep: () -> Unit = {},
    val onManage: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
)

/** S18: the paywall for free users, the Plan facts (with the day-6 banner) for subscribers. */
@Composable
fun PlanScreen(onClose: () -> Unit, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uri = LocalUriHandler.current
    val res = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.effect.collect { e ->
            when (e) {
                is PlanEffect.Toast -> Toast.makeText(
                    context,
                    if (e.arg != null) res.getString(e.message, e.arg) else res.getString(e.message),
                    Toast.LENGTH_LONG,
                ).show()
                PlanEffect.Close -> onClose()
                PlanEffect.OpenManage -> context.openPlaySubscriptions()
            }
        }
    }
    PlanContent(
        state = state,
        actions = PlanActions(
            onClose = onClose,
            onSelect = viewModel::onSelect,
            onBuy = { viewModel.onBuy(activity) },
            onRestore = viewModel::onRestore,
            onRetry = viewModel::onRetry,
            onKeep = viewModel::onKeep,
            onManage = viewModel::onManage,
            onOpenUrl = { runCatching { uri.openUri(it) } },
        ),
    )
}

@Composable
fun PlanContent(state: PlanUiState, actions: PlanActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        if (state.showPaywall) Paywall(state, actions) else PlanFacts(state, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String, close: Boolean, onClose: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag(TAG_PLAN_CLOSE)) {
                if (close) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                } else {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.plan_back_a11y))
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Paywall(state: PlanUiState, actions: PlanActions) {
    // Close is always visible (US-060/061: no dark patterns).
    TopBar(stringResource(R.string.paywall_bar_title), close = true, onClose = actions.onClose)
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).testTag(TAG_PAYWALL),
        verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        Text(stringResource(R.string.paywall_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.paywall_lead), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Included()
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                val loading = stringResource(R.string.plan_loading_a11y)
                CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
            }
            state.loadFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.paywall_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.paywall_retry)) }
            }
            else -> Plans(state, actions.onSelect)
        }
        state.selectedOffer?.let { offer -> offer.trialDays?.let { Timeline(offer, it) } }
        InfoBlock(Icons.Outlined.HelpOutline, stringResource(R.string.paywall_cancel_title), stringResource(R.string.paywall_cancel_body))
        // Restore, the fine print and the links end the scroll, so the sticky bar stays short at 200% font scale.
        Column(Modifier.fillMaxWidth().padding(bottom = VaakSpace.l)) {
            TextCta(stringResource(R.string.paywall_restore), actions.onRestore)
            Text(
                if (state.selected == Plan.FOUNDING) stringResource(R.string.paywall_fine_founding) else stringResource(R.string.paywall_fine),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { actions.onOpenUrl(Links.TERMS) }, modifier = Modifier.heightIn(min = VaakSpace.touch)) {
                    Text(stringResource(R.string.paywall_terms), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { actions.onOpenUrl(Links.PRIVACY_POLICY) }, modifier = Modifier.heightIn(min = VaakSpace.touch)) {
                    Text(stringResource(R.string.paywall_privacy), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    CtaBar {
        val offer = state.selectedOffer
        if (offer != null) {
            val yearly = offer.plan.period == BillingPeriod.YEAR
            val trial = offer.trialDays
            Text(
                when {
                    trial != null && yearly -> pluralStringResource(R.plurals.paywall_cta_note_year, trial, trial, offer.formattedPrice)
                    trial != null -> pluralStringResource(R.plurals.paywall_cta_note_month, trial, trial, offer.formattedPrice)
                    else -> stringResource(R.string.paywall_cta_note_no_trial)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag(TAG_CTA_NOTE),
            )
            PrimaryCta(
                text = when {
                    trial != null -> pluralStringResource(R.plurals.paywall_cta_trial, trial, trial)
                    yearly -> stringResource(R.string.paywall_cta_buy_year, offer.formattedPrice)
                    else -> stringResource(R.string.paywall_cta_buy_month, offer.formattedPrice)
                },
                onClick = actions.onBuy,
                enabled = !state.busy,
                modifier = Modifier.testTag(TAG_BUY),
            )
        }
        if (state.confirming) {
            Text(
                stringResource(R.string.paywall_confirming),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Included() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.paywall_incl_talks, R.string.paywall_incl_checks, R.string.paywall_incl_sayagain, R.string.paywall_incl_free).forEach {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun Plans(state: PlanUiState, onSelect: (Plan) -> Unit) {
    val desc = stringResource(R.string.paywall_plans_a11y)
    Column(Modifier.selectableGroup().semantics { contentDescription = desc }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.offers.forEach { offer -> PlanCard(offer, offer.plan == state.selected) { onSelect(offer.plan) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val perMonth = Prices.format(Prices.perMonthMicros(offer.priceMicros, offer.plan.period), offer.currencyCode)
    val perWeek = Prices.format(Prices.perWeekMicros(offer.priceMicros, offer.plan.period), offer.currencyCode)
    val yearly = offer.plan.period == BillingPeriod.YEAR
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = border,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).testTag(TAG_PLAN_PREFIX + offer.plan.basePlanId),
    ) {
        val large = largeFont()
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = if (large) Alignment.Top else Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(planName(offer.plan)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (offer.plan == Plan.FOUNDING) {
                        Surface(shape = CircleShape, color = VaakTheme.colors.accentTint, contentColor = VaakTheme.colors.accentInk) {
                            Text(stringResource(R.string.paywall_launch_offer), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                Text(
                    if (yearly) stringResource(R.string.paywall_per_year_line, perMonth, perWeek) else stringResource(R.string.paywall_per_month_line, perWeek),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // At large font sizes the price moves under the name instead of starving it of width.
                if (large) Price(offer, yearly, Modifier.padding(top = 6.dp), Alignment.Start)
            }
            if (!large) Price(offer, yearly, Modifier.padding(start = 8.dp), Alignment.End)
        }
    }
}

@Composable
private fun Price(offer: PlanOffer, yearly: Boolean, modifier: Modifier, align: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = align) {
        Text(offer.formattedPrice, style = VaakTheme.type.metric.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(if (yearly) R.string.paywall_per_year else R.string.paywall_per_month), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How the trial runs: a visible heading, then each step read with its day and price (nothing hidden behind a summary). */
@Composable
private fun Timeline(offer: PlanOffer, trialDays: Int) {
    VaakCard(Modifier.fillMaxWidth().testTag(TAG_TIMELINE)) {
        Column(Modifier.padding(VaakSpace.l), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.paywall_tl_a11y),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Step(Icons.Rounded.LockOpen, pluralStringResource(R.plurals.paywall_tl_today, trialDays, trialDays), stringResource(R.string.paywall_tl_today_sub))
            Step(Icons.Rounded.Notifications, stringResource(R.string.paywall_tl_remind, trialDays - 1), stringResource(R.string.paywall_tl_remind_sub))
            Step(
                Icons.Outlined.Event,
                stringResource(R.string.paywall_tl_charge, trialDays, offer.formattedPrice),
                stringResource(if (offer.plan.period == BillingPeriod.YEAR) R.string.paywall_tl_charge_sub_year else R.string.paywall_tl_charge_sub_month),
            )
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, sub: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoBlock(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlanFacts(state: PlanUiState, actions: PlanActions) {
    TopBar(stringResource(R.string.plan_screen_title), close = false, onClose = actions.onClose)
    val e = state.snapshot
    val zone = ZoneId.systemDefault()
    val expiry = e.expiryMs?.let { shortDate(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }.orEmpty()
    val trial = state.status == SubscriptionStatus.TRIAL || state.status == SubscriptionStatus.TRIAL_CANCELLED
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = VaakSpace.gutter).testTag(TAG_PLAN_FACTS),
        verticalArrangement = Arrangement.spacedBy(VaakSpace.m),
    ) {
        state.banner?.let { TrialBanner(it, actions.onKeep, actions.onManage) }
        VaakCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(VaakSpace.l)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = VaakTheme.colors.accent, modifier = Modifier.size(22.dp))
                    Text(
                        stringResource(if (trial) R.string.plan_trial_title else R.string.plan_premium_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                val day = state.trialDay
                if (trial && day != null) {
                    // One stop for TalkBack: the bar says "Day 6 of 7"; the visible caption is not read twice.
                    val dayText = stringResource(R.string.plan_trial_day, day, TRIAL_DAYS)
                    LinearProgressIndicator(
                        progress = { day / TRIAL_DAYS.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(6.dp))
                            .semantics { stateDescription = dayText }.testTag(TAG_TRIAL_PROGRESS),
                        trackColor = VaakTheme.colors.track,
                        drawStopIndicator = {},
                    )
                    Text(
                        dayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics {},
                    )
                }
                Column(Modifier.padding(top = 8.dp)) {
                    e.plan?.let { plan ->
                        val name = stringResource(planName(plan))
                        FactRow(stringResource(if (trial) R.string.plan_row_after_trial else R.string.plan_row_plan), listOfNotNull(name, e.priceLabel).joinToString(" · "))
                    }
                    when (state.status) {
                        SubscriptionStatus.TRIAL -> FactRow(stringResource(R.string.plan_row_first_charge), expiry)
                        SubscriptionStatus.ACTIVE -> FactRow(stringResource(R.string.plan_row_renews), expiry)
                        else -> FactRow(stringResource(R.string.plan_row_ends), expiry)
                    }
                    FactRow(stringResource(R.string.plan_row_talks), stringResource(R.string.plan_row_talks_value))
                }
            }
        }
        OutlineCta(stringResource(R.string.plan_manage), actions.onManage, leading = Icons.AutoMirrored.Rounded.OpenInNew)
        InfoNote(
            Icons.Outlined.Info,
            when (state.status) {
                SubscriptionStatus.TRIAL -> stringResource(R.string.plan_note_trial, expiry)
                SubscriptionStatus.ACTIVE -> stringResource(R.string.plan_note_active)
                else -> stringResource(R.string.plan_note_cancelled, expiry)
            },
        )
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(top = 8.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(end = 8.dp))
        // The value may wrap but never pushes the label off screen.
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

fun planName(plan: Plan): Int = when (plan) {
    Plan.FOUNDING -> R.string.plan_name_founding
    Plan.YEARLY -> R.string.plan_name_yearly
    Plan.MONTHLY -> R.string.plan_name_monthly
}

private const val TRIAL_DAYS = 7
const val TAG_PAYWALL = "paywall"
const val TAG_PLAN_FACTS = "plan_facts"
const val TAG_PLAN_CLOSE = "plan_close"
const val TAG_BUY = "paywall_buy"
const val TAG_CTA_NOTE = "paywall_cta_note"
const val TAG_PLAN_PREFIX = "plan_"
const val TAG_TIMELINE = "paywall_timeline"
const val TAG_TRIAL_PROGRESS = "plan_trial_progress"
