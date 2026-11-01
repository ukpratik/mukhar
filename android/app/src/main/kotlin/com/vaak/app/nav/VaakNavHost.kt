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
import com.vaak.app.feature.today.TodayScreen
import com.vaak.app.feature.words.WordsScreen

@Composable
fun VaakNavHost(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val onTab = TopLevel.entries.any { tab -> backStack?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    // Leaves the talk flow and returns to the tab it started from.
    val home: () -> Unit = {
        if (!navController.popBackStack(PracticeRoute, inclusive = false) && !navController.popBackStack(TodayRoute, inclusive = false)) {
            navController.navigate(TodayRoute)
        }
    }
    Scaffold(
        bottomBar = {
            // Default M3 colours render as designed (design-system §9.1): card bar, brand-tint pill, brand icon.
            if (onTab || backStack == null) {
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
        NavHost(navController, startDestination = TodayRoute, modifier = Modifier.padding(if (onTab) padding else androidx.compose.foundation.layout.PaddingValues())) {
            composable<TodayRoute> { TodayScreen(onTodaysTalk = { navController.navigate(RecordRoute()) }) }
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
