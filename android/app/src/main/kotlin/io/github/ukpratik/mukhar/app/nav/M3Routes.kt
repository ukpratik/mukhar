package io.github.ukpratik.mukhar.app.nav

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.ukpratik.mukhar.app.feature.onboarding.OnboardingRoute as OnboardingFlow
import io.github.ukpratik.mukhar.app.feature.onboarding.OnboardingStep
import io.github.ukpratik.mukhar.app.feature.onboarding.StandaloneStepRoute
import io.github.ukpratik.mukhar.app.feature.today.TodayDestination
import kotlinx.serialization.Serializable

// M3 routes (LLD §5): the onboarding graph gated by DataStore, plus S04/S05 opened from Today.
@Serializable data object OnboardingRoute
@Serializable data object DisclosureRoute
@Serializable data object BaselineRoute

/** S05 resuming a baseline that was recovered after the app closed mid-recording (S09 state 8). */
@Serializable data class BaselineResumeRoute(val sessionId: String)

/** Today's Talk goes straight to Recording (S09) with a prompt picked for the last language and track (D-020). */
fun NavHostController.navigateToTalk() = navigate(RecordRoute())

/** Today wired to its destinations. Registered from [MukharNavHost]. */
fun NavGraphBuilder.todayDestination(navController: NavHostController) {
    composable<TodayRoute> {
        TodayDestination(
            onStartTalk = navController::navigateToTalk,
            onOpenRevise = { navController.navigate(ReviewRoute(ReviewRoute.REVISE)) },
            onOpenWord = { id -> navController.navigate(WordDetailRoute(id)) },
            onOpenWords = { navController.navigateTab(WordsRoute) },
            onOpenDeck = { navController.navigate(DeckRoute()) },
            onOpenMe = { navController.navigateTab(MeRoute) },
            onTurnOnTalks = { navController.navigate(DisclosureRoute) },
            onRecordBaseline = { navController.navigate(BaselineRoute) },
            onOpenRecovered = { id -> navController.navigate(RecoveredRoute(id)) },
        )
    }
}

/**
 * Onboarding and the standalone S04/S05 (also S05 resuming a recovered baseline). Finishing onboarding calls
 * [onOnboardingFinished]: the root then rebuilds the graph with Today as its start, so tab back-stack rules hold.
 */
fun NavGraphBuilder.m3Destinations(navController: NavHostController, onOnboardingFinished: () -> Unit) {
    composable<OnboardingRoute> { OnboardingFlow(onFinished = onOnboardingFinished) }
    composable<DisclosureRoute> { StandaloneStepRoute(OnboardingStep.DISCLOSURE, onClose = { navController.popBackStack() }) }
    composable<BaselineRoute> { StandaloneStepRoute(OnboardingStep.BASELINE, onClose = { navController.popBackStack() }) }
    composable<BaselineResumeRoute> { entry ->
        val id = entry.toRoute<BaselineResumeRoute>().sessionId
        StandaloneStepRoute(OnboardingStep.BASELINE, onClose = { navController.popBackStack() }, resumeSessionId = id)
    }
}

private fun NavHostController.navigateTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
