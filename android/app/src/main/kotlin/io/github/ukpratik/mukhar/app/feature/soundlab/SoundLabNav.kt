package io.github.ukpratik.mukhar.app.feature.soundlab

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** M6: S22 Sound Lab lite, opened from the Practice tab (S08). Kept here so the nav files only gain one line. */
@Serializable data object SoundLabRoute

fun NavGraphBuilder.soundLabDestination(navController: NavHostController) {
    composable<SoundLabRoute> { SoundLabScreen(onBack = { navController.popBackStack() }) }
}
