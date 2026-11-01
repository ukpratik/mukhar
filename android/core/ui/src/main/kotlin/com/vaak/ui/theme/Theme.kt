package com.vaak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Vaak's M3 theme: light by default, dark follows the system (US-074). Dynamic colour is off. */
@Composable
fun VaakTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalVaakColors provides if (darkTheme) DarkVaakColors else LightVaakColors,
        LocalVaakTypeExtras provides VaakTypeExtras(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = VaakTypography,
            shapes = VaakShapes,
            content = content,
        )
    }
}

/** Access to Vaak's extra roles: `VaakTheme.colors.accent`, `VaakTheme.type.quote`. */
object VaakTheme {
    val colors: VaakColors
        @Composable @ReadOnlyComposable get() = LocalVaakColors.current
    val type: VaakTypeExtras
        @Composable @ReadOnlyComposable get() = LocalVaakTypeExtras.current
}
