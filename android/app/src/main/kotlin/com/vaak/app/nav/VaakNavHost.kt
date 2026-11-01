package com.vaak.app.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vaak.app.feature.me.MeScreen
import com.vaak.app.feature.talk.prompt.PromptScreen
import com.vaak.app.feature.talk.record.RecordScreen
import com.vaak.app.feature.talk.record.RecoveredScreen
import com.vaak.app.feature.talk.report.ReportActions
import com.vaak.app.feature.talk.report.ReportScreen
import com.vaak.app.feature.talk.sayagain.SayAgainScreen
import com.vaak.app.feature.words.WordsScreen

@Composable
fun VaakNavHost(
    navController: NavHostController = rememberNavController(),
    // Onboarding until it's done, then Today (VaakRoot picks it from DataStore, LLD §5).
    startDestination: Any = TodayRoute,
    onOnboardingFinished: () -> Unit = {},
    // Today wired to its destinations (a slot so tests can stub it).
    todayScreen: NavGraphBuilder.(NavHostController) -> Unit = { todayDestination(it) },
) {
    val backStack by navController.currentBackStackEntryAsState()
    // The bar shows on the 4 tabs only (not onboarding or the full-screen talk flow).
    val onTab = TopLevel.entries.any { tab -> backStack?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    // The talk screens draw edge to edge and pad themselves; everything else takes the Scaffold's insets.
    val ownInsets = backStack?.destination?.let { d -> FULL_SCREEN_TALK.any { d.hasRoute(it) } } == true
    // Leaves the talk flow and returns to the tab it started from.
    val home: () -> Unit = {
        if (!navController.popBackStack(PracticeRoute, inclusive = false) && !navController.popBackStack(TodayRoute, inclusive = false)) {
            navController.navigate(TodayRoute)
        }
    }
    Scaffold(
        bottomBar = {
            // Default M3 colours render as designed (design-system §9.1): card bar, brand-tint pill, brand icon.
            if (onTab) {
                NavigationBar {
                    TopLevel.entries.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = startDestination, modifier = Modifier.padding(if (ownInsets) PaddingValues() else padding)) {
            todayScreen(navController)
            m3Destinations(navController, onOnboardingFinished)
            composable<PracticeRoute> {
                PromptScreen(onRecord = { prompt, track -> navController.navigate(RecordRoute(prompt.id, prompt.lang.code, track)) })
            }
            composable<WordsRoute> { WordsScreen() }
            composable<MeRoute> { MeScreen() }

            composable<RecordRoute> {
                RecordScreen(
                    onLeave = { if (!navController.popBackStack()) home() },
                    onReport = { id -> navController.navigate(ReportRoute(id)) { popUpTo<RecordRoute> { inclusive = true } } },
                    onSayAgain = { id -> navController.navigate(SayAgainRoute(id)) { popUpTo<RecordRoute> { inclusive = true } } },
                )
            }
            composable<ReportRoute> {
                ReportScreen(
                    ReportActions(
                        onBack = home,
                        onSayAgain = { s ->
                            val m = s.meta ?: return@ReportActions
                            navController.navigate(RecordRoute(s.promptId, m.lang.code, m.track.key, parentId = s.sessionId, capS = s.retakeCapS?.toInt()))
                        },
                        onRecordAgain = { s ->
                            val m = s.meta ?: return@ReportActions
                            navController.navigate(RecordRoute(s.promptId, m.lang.code, m.track.key)) { popUpTo<ReportRoute> { inclusive = true } }
                        },
                        onSettings = { navController.navigate(MeRoute) { popUpTo(navController.graph.findStartDestination().id) } },
                    ),
                )
            }
            composable<SayAgainRoute> {
                SayAgainScreen(
                    onBack = { if (!navController.popBackStack()) home() },
                    onDone = home,
                    onReport = { id -> navController.navigate(ReportRoute(id)) },
                    onOnceMore = { s ->
                        navController.navigate(RecordRoute(s.promptId, s.lang.code, s.track, parentId = s.parentId, capS = (s.capMs / 1000).toInt())) {
                            popUpTo<SayAgainRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<RecoveredRoute> {
                RecoveredScreen(
                    onClose = { if (!navController.popBackStack()) home() },
                    onReport = { id -> navController.navigate(ReportRoute(id)) { popUpTo<RecoveredRoute> { inclusive = true } } },
                    onRecordAgain = { s ->
                        navController.navigate(RecordRoute(s.promptId, s.lang, s.track, replaceId = s.id)) { popUpTo<RecoveredRoute> { inclusive = true } }
                    },
                )
            }
        }
    }
}

/** Full-screen talk routes (S09 to S12): no bottom bar, and they handle their own window insets. */
private val FULL_SCREEN_TALK = listOf(RecordRoute::class, ReportRoute::class, SayAgainRoute::class, RecoveredRoute::class)
