package io.github.ukpratik.mukhar.app.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.ukpratik.mukhar.app.feature.plan.PlanScreen
import io.github.ukpratik.mukhar.app.feature.progress.ProgressScreen
import io.github.ukpratik.mukhar.app.feature.quota.QuotaScreen
import io.github.ukpratik.mukhar.app.feature.settings.KeysScreen
import io.github.ukpratik.mukhar.app.feature.settings.RecordingsScreen
import io.github.ukpratik.mukhar.app.feature.settings.SettingsScreen
import io.github.ukpratik.mukhar.app.feature.share.ShareScreen
import io.github.ukpratik.mukhar.app.feature.soundlab.SoundLabRoute // M6
import io.github.ukpratik.mukhar.app.feature.soundlab.soundLabDestination // M6
import io.github.ukpratik.mukhar.app.feature.talk.prompt.PromptScreen
import io.github.ukpratik.mukhar.app.feature.talk.record.RecordScreen
import io.github.ukpratik.mukhar.app.feature.talk.record.RecoveredScreen
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportActions
import io.github.ukpratik.mukhar.app.feature.talk.report.ReportScreen
import io.github.ukpratik.mukhar.app.feature.talk.sayagain.SayAgainScreen
import io.github.ukpratik.mukhar.app.feature.words.WordDetailScreen
import io.github.ukpratik.mukhar.app.feature.words.WordsScreen
import io.github.ukpratik.mukhar.app.feature.words.deck.DeckScreen
import io.github.ukpratik.mukhar.app.feature.words.revise.RevisionScreen
import io.github.ukpratik.mukhar.app.feature.words.review.ReviewScreen

/**
 * The bar has a fixed height, so its labels stop growing at 150 % (TalkBack still reads the full label, and the
 * icons carry the meaning); one line, ellipsised rather than clipped (design-system §10.5).
 */
@Composable
private fun TabLabel(text: String) {
    val density = LocalDensity.current
    val capped = Density(density.density, density.fontScale.coerceAtMost(MAX_TAB_LABEL_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val MAX_TAB_LABEL_SCALE = 1.5f

@Composable
fun MukharNavHost(
    navController: NavHostController = rememberNavController(),
    // Onboarding until it's done, then Today (MukharRoot picks it from DataStore, LLD §5).
    startDestination: Any = TodayRoute,
    onOnboardingFinished: () -> Unit = {},
    // "Start fresh" after Delete all my data: the root rebuilds the graph at onboarding (S19 state 3).
    onDataDeleted: () -> Unit = {},
    // Today wired to its destinations (a slot so tests can stub it).
    todayScreen: NavGraphBuilder.(NavHostController) -> Unit = { todayDestination(it) },
    // The Words tab (S14) wired to its destinations (a slot so tests can stub it; the real one needs Hilt).
    wordsScreen: NavGraphBuilder.(NavHostController) -> Unit = { wordsDestination(it) },
    // The Me tab (S16 Progress) wired to its destinations (a slot so tests can stub it; the real one needs Hilt).
    meScreen: NavGraphBuilder.(NavHostController) -> Unit = { meDestination(it) },
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
                            label = { TabLabel(stringResource(tab.label)) },
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
                PromptScreen(
                    onRecord = { prompt, track -> navController.navigate(RecordRoute(prompt.id, prompt.lang.code, track)) },
                    onOpenSoundLab = { navController.navigate(SoundLabRoute) }, // M6: Sound Lab entry (S22)
                    onOpenKeys = { navController.navigate(KeysRoute) }, // US-078: the locked AI ideas line
                )
            }
            soundLabDestination(navController) // M6: S22 Sound Lab lite
            wordsScreen(navController)
            composable<WordDetailRoute> {
                WordDetailScreen(
                    onBack = { navController.popBackStack() },
                    onPractise = { id -> navController.navigate(ReviewRoute(ReviewRoute.GYM, wordId = id)) },
                    onSeeLine = { id -> navController.navigate(ReportRoute(id)) },
                )
            }
            composable<DeckRoute> { entry ->
                val lang = entry.toRoute<DeckRoute>().lang
                DeckScreen(
                    onBack = { navController.popBackStack() },
                    onRevise = { navController.navigate(RevisionRoute(lang)) { popUpTo<DeckRoute> { inclusive = true } } },
                )
            }
            composable<RevisionRoute> { entry ->
                val lang = entry.toRoute<RevisionRoute>().lang
                RevisionScreen(
                    onBack = { navController.popBackStack() },
                    onLearn = { navController.navigate(DeckRoute(lang)) { popUpTo<RevisionRoute> { inclusive = true } } },
                )
            }
            composable<ReviewRoute> {
                ReviewScreen(
                    onClose = { if (!navController.popBackStack()) navController.navigate(TodayRoute) },
                    onTurnOnTalks = { navController.navigate(DisclosureRoute) },
                )
            }
            meScreen(navController)
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onPlan = { navController.navigate(PlanRoute(PlanRoute.SOURCE_SETTINGS)) },
                    onDisclosure = { navController.navigate(DisclosureRoute) },
                    onStartFresh = onDataDeleted,
                    onKeys = { navController.navigate(KeysRoute) },
                    onRecordings = { navController.navigate(RecordingsRoute) },
                )
            }
            composable<KeysRoute> { KeysScreen(onBack = { navController.popBackStack() }) }
            composable<RecordingsRoute> {
                RecordingsScreen(onBack = { navController.popBackStack() }, onOpen = { id -> navController.navigate(ReportRoute(id)) })
            }
            composable<PlanRoute> { PlanScreen(onClose = { if (!navController.popBackStack()) navController.navigate(TodayRoute) }) }
            composable<ShareRoute> { ShareScreen(onClose = { navController.popBackStack() }) }
            composable<QuotaRoute> {
                QuotaScreen(
                    onClose = home,
                    onPremium = { navController.navigate(PlanRoute(PlanRoute.SOURCE_QUOTA)) },
                    onRevise = { navController.navigate(ReviewRoute(ReviewRoute.REVISE)) { popUpTo<QuotaRoute> { inclusive = true } } },
                    onGym = { navController.navigate(RevisionRoute()) { popUpTo<QuotaRoute> { inclusive = true } } },
                )
            }

            composable<RecordRoute> {
                RecordScreen(
                    onLeave = { if (!navController.popBackStack()) home() },
                    onReport = { id -> navController.navigate(ReportRoute(id)) { popUpTo<RecordRoute> { inclusive = true } } },
                    onSayAgain = { id -> navController.navigate(SayAgainRoute(id)) { popUpTo<RecordRoute> { inclusive = true } } },
                    onQuota = { navController.navigate(QuotaRoute) { popUpTo<RecordRoute> { inclusive = true } } },
                )
            }
            composable<ReportRoute> {
                ReportScreen(
                    ReportActions(
                        // Opened from Settings → Recordings: back returns there; otherwise to Today as before.
                        onBack = {
                            if (navController.previousBackStackEntry?.destination?.hasRoute<RecordingsRoute>() == true) navController.popBackStack() else home()
                        },
                        onSayAgain = { s ->
                            val m = s.meta ?: return@ReportActions
                            navController.navigate(RecordRoute(s.promptId, m.lang.code, m.track.key, parentId = s.sessionId, capS = s.retakeCapS?.toInt()))
                        },
                        onRecordAgain = { s ->
                            val m = s.meta ?: return@ReportActions
                            navController.navigate(RecordRoute(s.promptId, m.lang.code, m.track.key)) { popUpTo<ReportRoute> { inclusive = true } }
                        },
                        onSettings = { navController.navigate(MeRoute) { popUpTo(navController.graph.findStartDestination().id) } },
                        onQuota = { navController.navigate(QuotaRoute) { popUpTo<ReportRoute> { inclusive = true } } },
                        onAnalysisSettings = { navController.navigate(SettingsRoute) },
                        onKeys = { navController.navigate(KeysRoute) },
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
                    // A recovered baseline resumes the baseline flow (analysis, then "Keep this recording?").
                    onResumeBaseline = { id -> navController.navigate(BaselineResumeRoute(id)) { popUpTo<RecoveredRoute> { inclusive = true } } },
                )
            }
        }
    }
}

/** The Words tab (S14). */
fun NavGraphBuilder.wordsDestination(navController: NavHostController) {
    composable<WordsRoute> {
        WordsScreen(
            onOpenRevision = { lang -> navController.navigate(RevisionRoute(lang?.code)) },
            onOpenWord = { id -> navController.navigate(WordDetailRoute(id)) },
            onTalk = navController::navigateToTalk,
            onRevise = { lang, from, to ->
                navController.navigate(RevisionRoute(lang?.code, RevisionRoute.SET_DONE, from.toString(), to.toString()))
            },
            onOpenDeck = { lang -> navController.navigate(DeckRoute(lang?.code)) },
            onPractiseStarred = { lang -> navController.navigate(RevisionRoute(lang?.code, RevisionRoute.SET_STARRED)) },
        )
    }
}

/** The Me tab: Progress (S16), with Settings in the header and the plan card at the bottom. */
fun NavGraphBuilder.meDestination(navController: NavHostController) {
    composable<MeRoute> {
        ProgressScreen(
            onSettings = { navController.navigate(SettingsRoute) },
            onPlan = { navController.navigate(PlanRoute(PlanRoute.SOURCE_ME)) },
            onTalk = navController::navigateToTalk,
            onBaseline = { navController.navigate(BaselineRoute) },
            onShare = { navController.navigate(ShareRoute) },
        )
    }
}

/** Full-screen talk routes (S09 to S12): no bottom bar, and they handle their own window insets. */
private val FULL_SCREEN_TALK = listOf(
    RecordRoute::class, ReportRoute::class, SayAgainRoute::class, RecoveredRoute::class,
    SettingsRoute::class, PlanRoute::class, QuotaRoute::class, ShareRoute::class, KeysRoute::class, RecordingsRoute::class,
)
