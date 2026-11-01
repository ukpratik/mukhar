package com.vaak.app.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.vaak.app.R
import kotlinx.serialization.Serializable

// Type-safe routes (LLD §5). The onboarding graph and the full-screen talk flow arrive in M2/M3.
@Serializable data object TodayRoute
@Serializable data object PracticeRoute
@Serializable data object WordsRoute
@Serializable data object MeRoute

/** The 4 bottom tabs (US-030): selected icon filled, unselected outlined (design-system §7). */
enum class TopLevel(
    val route: Any,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    TODAY(TodayRoute, R.string.tab_today, Icons.Rounded.WbSunny, Icons.Outlined.WbSunny),
    PRACTICE(PracticeRoute, R.string.tab_practice, Icons.Rounded.RecordVoiceOver, Icons.Outlined.RecordVoiceOver),
    WORDS(WordsRoute, R.string.tab_words, Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    ME(MeRoute, R.string.tab_me, Icons.Rounded.Person, Icons.Outlined.Person),
}
