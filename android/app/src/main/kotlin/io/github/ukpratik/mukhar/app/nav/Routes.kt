package io.github.ukpratik.mukhar.app.nav

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
import io.github.ukpratik.mukhar.app.R
import kotlinx.serialization.Serializable

// Type-safe routes (LLD §5). The onboarding graph arrives in M3.
@Serializable data object TodayRoute
@Serializable data object PracticeRoute
@Serializable data object WordsRoute
@Serializable data object MeRoute

// Full-screen talk flow (no bottom bar): S09 → S10/S11 → S12.

/**
 * S09 Recording. [promptId] null = Today's Talk (a prompt is picked for the last-used language and track).
 * [parentId] makes it a re-take (US-017) with [capS]; [replaceId] is a recovered talk being recorded again.
 */
@Serializable
data class RecordRoute(
    val promptId: String? = null,
    val lang: String? = null,
    val track: String? = null,
    val parentId: String? = null,
    val capS: Int? = null,
    val replaceId: String? = null,
)

/** S10 Analysing → S11 Results (one screen). */
@Serializable data class ReportRoute(val sessionId: String)

/** S12 Say it again: the re-take's deltas against its parent. */
@Serializable data class SayAgainRoute(val retakeId: String)

/** S09 state 8: a talk saved when the app closed mid-recording. */
@Serializable data class RecoveredRoute(val sessionId: String)

// M4: Word Gym (S13), quick revision (S15) and word detail (S14).

/**
 * S13 ([mode] = gym) or S15 ([mode] = revise). [wordId] reviews one word now (S14 "Practise in a sentence").
 * [lang] limits the Word Gym to one Words tab (US-075). [mode] = extra is History's extra practice (US-077): the
 * words first shown from [from] to [to] (ISO dates), due or not.
 */
@Serializable
data class ReviewRoute(
    val mode: String = GYM,
    val wordId: String? = null,
    val lang: String? = null,
    val from: String? = null,
    val to: String? = null,
) {
    companion object {
        const val GYM = "gym"
        const val REVISE = "revise"
        const val EXTRA = "extra"
    }
}

@Serializable data class WordDetailRoute(val wordId: String)

// M5: Me (Progress S16) → Settings (S19), Plan/paywall (S18), Share card (S21); Quota (S17) from the talk flow.

@Serializable data object SettingsRoute

/** S18. [source] is the `paywall_viewed{source}` value: quota, progress, settings or me. */
@Serializable
data class PlanRoute(val source: String = SOURCE_ME) {
    companion object {
        const val SOURCE_ME = "me"
        const val SOURCE_QUOTA = "quota"
        const val SOURCE_SETTINGS = "settings"
    }
}

@Serializable data object QuotaRoute

/** 2A (ADR-008): Settings → Analysis → Your keys. */
@Serializable data object KeysRoute

/** US-080: Settings → Recordings. */
@Serializable data object RecordingsRoute

@Serializable data object ShareRoute

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
