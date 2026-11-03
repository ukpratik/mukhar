package io.github.ukpratik.mukhar.app.debug

import androidx.compose.runtime.Composable

/** Release builds have no debug menu (build-plan "Debug hooks" are stripped from release). */
@Composable
fun DebugMenuEntry() = Unit
