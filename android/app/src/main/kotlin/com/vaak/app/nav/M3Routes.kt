package com.vaak.app.nav

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.vaak.app.R
import com.vaak.app.feature.PlaceholderScreen
import com.vaak.app.feature.onboarding.OnboardingRoute as OnboardingFlow
import com.vaak.app.feature.onboarding.OnboardingStep
import com.vaak.app.feature.onboarding.StandaloneStepRoute
import com.vaak.app.feature.today.TodayDestination
import kotlinx.serialization.Serializable

// M3 routes (LLD §5): the onboarding graph gated by DataStore, plus S04/S05 opened from Today.
@Serializable data object OnboardingRoute
@Serializable data object DisclosureRoute
@Serializable data object BaselineRoute

/** Where the Revise card goes. M4 builds the session (S15); placeholder until then. */
@Serializable data object ReviseEntryRoute

/** Today's Talk goes straight to Recording (S09) with a prompt picked for the last language and track (D-020). */
fun NavHostController.navigateToTalk() = navigate(RecordRoute())

/** Today wired to its destinations. Registered from [VaakNavHost]. */
fun NavGraphBuilder.todayDestination(navController: NavHostController) {
    composable<TodayRoute> {
        TodayDestination(
            onStartTalk = navController::navigateToTalk,
            onOpenRevise = { navController.navigate(ReviseEntryRoute) },
            onOpenWords = { navController.navigateTab(WordsRoute) },
            onOpenMe = { navController.navigateTab(MeRoute) },
            onTurnOnTalks = { navController.navigate(DisclosureRoute) },
            onRecordBaseline = { navController.navigate(BaselineRoute) },
            onOpenRecovered = { id -> navController.navigate(RecoveredRoute(id)) },
        )
    }
}

/**
 * Onboarding, the standalone S04/S05, and the placeholder revise destination. Finishing onboarding calls
 * [onOnboardingFinished]: the root then rebuilds the graph with Today as its start, so tab back-stack rules hold.
 */
fun NavGraphBuilder.m3Destinations(navController: NavHostController, onOnboardingFinished: () -> Unit) {
    composable<OnboardingRoute> { OnboardingFlow(onFinished = onOnboardingFinished) }
    composable<DisclosureRoute> { StandaloneStepRoute(OnboardingStep.DISCLOSURE, onClose = { navController.popBackStack() }) }
    composable<BaselineRoute> { StandaloneStepRoute(OnboardingStep.BASELINE, onClose = { navController.popBackStack() }) }
    composable<ReviseEntryRoute> { PlaceholderScreen(title = R.string.placeholder_revise_title, body = R.string.placeholder_revise) }
}

private fun NavHostController.navigateTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
