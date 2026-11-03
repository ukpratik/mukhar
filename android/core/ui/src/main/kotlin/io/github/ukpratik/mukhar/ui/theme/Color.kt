package io.github.ukpratik.mukhar.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// design-system.md §9.1, tokens.css v1.1. Dynamic colour is off. Saffron is not an M3 role: it lives in MukharColors.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2B2766), onPrimary = Color.White,
    primaryContainer = Color(0xFFECEBF7), onPrimaryContainer = Color(0xFF2B2766),
    inversePrimary = Color(0xFFC9C5F5),
    secondary = Color(0xFF423D93), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEBF7), onSecondaryContainer = Color(0xFF2B2766),
    tertiary = Color(0xFF1D6447), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F1E9), onTertiaryContainer = Color(0xFF1D6447),
    background = Color(0xFFFAF6EF), onBackground = Color(0xFF1F1B2E),
    surface = Color(0xFFFAF6EF), onSurface = Color(0xFF1F1B2E),
    surfaceVariant = Color(0xFFF3EFE7), onSurfaceVariant = Color(0xFF5B566B),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFDF9), surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFFFFDF9), surfaceContainerHigh = Color(0xFFFFFDF9),
    surfaceContainerHighest = Color(0xFFFFFDF9),
    outline = Color(0xFF8C8799), outlineVariant = Color(0xFFE8E1D4),
    inverseSurface = Color(0xFF2A2638), inverseOnSurface = Color(0xFFF3EFE7),
    error = Color(0xFF8A5100), onError = Color.White,
    errorContainer = Color(0xFFFCEBCB), onErrorContainer = Color(0xFF8A5100),
    scrim = Color(0x7A1F1B2E),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFC9C5F5), onPrimary = Color(0xFF1A1740),
    primaryContainer = Color(0xFF2D2952), onPrimaryContainer = Color(0xFFC9C5F5),
    inversePrimary = Color(0xFF2B2766),
    secondary = Color(0xFFABA5EE), onSecondary = Color(0xFF15131F),
    secondaryContainer = Color(0xFF2D2952), onSecondaryContainer = Color(0xFFC9C5F5),
    tertiary = Color(0xFF86D3AC), onTertiary = Color(0xFF0F2A1F),
    tertiaryContainer = Color(0xFF173A2B), onTertiaryContainer = Color(0xFF86D3AC),
    background = Color(0xFF15131F), onBackground = Color(0xFFF3EFE7),
    surface = Color(0xFF15131F), onSurface = Color(0xFFF3EFE7),
    surfaceVariant = Color(0xFF191726), onSurfaceVariant = Color(0xFFB5AFC4),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF1E1B2B), surfaceContainerLow = Color(0xFF1E1B2B),
    surfaceContainer = Color(0xFF1E1B2B), surfaceContainerHigh = Color(0xFF1E1B2B),
    surfaceContainerHighest = Color(0xFF1E1B2B),
    outline = Color(0xFF7E788F), outlineVariant = Color(0xFF332F45),
    inverseSurface = Color(0xFFF3EFE7), inverseOnSurface = Color(0xFF1F1B2E),
    error = Color(0xFFF5C276), onError = Color(0xFF231A05),
    errorContainer = Color(0xFF3B2D12), onErrorContainer = Color(0xFFF5C276),
    scrim = Color(0x99000000),
)

/** Roles M3 doesn't have (design-system §9.1 "MukharColors"). */
@Immutable
data class MukharColors(
    val accent: Color = Color(0xFFF2A531),
    val accentHover: Color = Color(0xFFF5B24C),
    val onAccent: Color = Color(0xFF231A05),
    val accentInk: Color,
    val accentTint: Color,
    val hero: Color = Color(0xFF2B2766),
    val onHero: Color = Color.White,
    val onHero2: Color = Color(0xFFD9D6F5),
    val heroOutlineDark: Color = Color(0xFF423D93),
    val faint: Color,
    val line: Color,
    val lineStrong: Color,
    val glowMark: Color,
    val track: Color,
    val bandOk: Color,
    val primaryIcon: Color,
    val focus: Color = Color(0xFFF2A531),
)

internal val LightMukharColors = MukharColors(
    accentInk = Color(0xFF8A5100), accentTint = Color(0xFFFCEBCB), faint = Color(0xFF767185),
    line = Color(0xFFE8E1D4), lineStrong = Color(0xFFBDB6CC), glowMark = Color(0xFFCBE7D7),
    track = Color(0xFFE3DEEA), bandOk = Color(0xFFBFE0CD), primaryIcon = Color(0xFFF2A531),
)

internal val DarkMukharColors = MukharColors(
    accentInk = Color(0xFFF5C276), accentTint = Color(0xFF3B2D12), faint = Color(0xFF8E889F),
    line = Color(0xFF332F45), lineStrong = Color(0xFF5C5772), glowMark = Color(0xFF245640),
    track = Color(0xFF2E2A40), bandOk = Color(0xFF245640), primaryIcon = Color(0xFF1A1740),
)

val LocalMukharColors = staticCompositionLocalOf { LightMukharColors }
