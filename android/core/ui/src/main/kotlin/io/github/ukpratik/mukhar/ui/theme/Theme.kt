package io.github.ukpratik.mukhar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Mukhar's M3 theme: light by default, dark follows the system (US-074). Dynamic colour is off. */
@Composable
fun MukharTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMukharColors provides if (darkTheme) DarkMukharColors else LightMukharColors,
        LocalMukharTypeExtras provides MukharTypeExtras(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MukharTypography,
            shapes = MukharShapes,
            content = content,
        )
    }
}

/** Access to Mukhar's extra roles: `MukharTheme.colors.accent`, `MukharTheme.type.quote`. */
object MukharTheme {
    val colors: MukharColors
        @Composable @ReadOnlyComposable get() = LocalMukharColors.current
    val type: MukharTypeExtras
        @Composable @ReadOnlyComposable get() = LocalMukharTypeExtras.current
}
