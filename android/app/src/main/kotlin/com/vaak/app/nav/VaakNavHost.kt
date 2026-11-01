package com.vaak.app.nav

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
import com.vaak.app.feature.practice.PracticeScreen
import com.vaak.app.feature.words.WordsScreen

@Composable
fun VaakNavHost(
    navController: NavHostController = rememberNavController(),
    // --- M3: onboarding start, and Today wired to its destinations (a slot so tests can stub it) ---
    startDestination: Any = TodayRoute,
    onOnboardingFinished: () -> Unit = {},
    todayScreen: NavGraphBuilder.(NavHostController) -> Unit = { todayDestination(it) },
    // --- end M3 ---
) {
    val backStack by navController.currentBackStackEntryAsState()
    // M3: the bar shows on the 4 tabs only (not onboarding or full-screen flows).
    val onTab = TopLevel.entries.any { tab -> backStack?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    Scaffold(
        bottomBar = {
            // Default M3 colours render as designed (design-system §9.1): card bar, brand-tint pill, brand icon.
            if (onTab) NavigationBar {
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
        },
    ) { padding ->
        NavHost(navController, startDestination = startDestination, modifier = Modifier.padding(padding)) {
            todayScreen(navController) // M3
            m3Destinations(navController, onOnboardingFinished) // M3
            composable<PracticeRoute> { PracticeScreen() }
            composable<WordsRoute> { WordsScreen() }
            composable<MeRoute> { MeScreen() }
        }
    }
}
