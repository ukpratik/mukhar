package com.vaak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.vaak.ui.R

// design-system.md §2 and §9.2. One bundled Fraunces cut (opsz 28, 500/600) stands in for both serif cuts (D-030).
// M6 (NFR-012): Manrope and Noto Sans Devanagari are bundled variable fonts (core/ui/fonts/README.md).
private val Fraunces = FontFamily(
    Font(R.font.fraunces_500, FontWeight.Normal),
    Font(R.font.fraunces_500, FontWeight.Medium),
    Font(R.font.fraunces_600, FontWeight.SemiBold),
    Font(R.font.fraunces_500, FontWeight.Normal, FontStyle.Italic),
)
val FrauncesDisplay: FontFamily = Fraunces
val FrauncesText: FontFamily = Fraunces

// Variable fonts (minSdk 26): Font(resId, weight)'s default variationSettings set `wght` from the weight, so one file
// serves every weight the type scale uses.
/** Body, labels, buttons (wght 400..800). */
val Manrope: FontFamily = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal),
    Font(R.font.manrope_variable, FontWeight.Medium),
    Font(R.font.manrope_variable, FontWeight.SemiBold),
    Font(R.font.manrope_variable, FontWeight.Bold),
    Font(R.font.manrope_variable, FontWeight.ExtraBold),
)

/** All Hindi (wght 400..700). It also has Latin glyphs, so Hinglish shares one font and baseline. */
val NotoDeva: FontFamily = FontFamily(
    Font(R.font.noto_sans_devanagari_variable, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari_variable, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari_variable, FontWeight.SemiBold),
    Font(R.font.noto_sans_devanagari_variable, FontWeight.Bold),
)

val VaakTypography = Typography(
    displayMedium = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.Normal, fontSize = 72.sp, lineHeight = 72.sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = FrauncesDisplay, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.96.sp),
)

// Devanagari: keep font padding so matras aren't clipped, and tag the locale so TalkBack uses a Hindi voice.
private val DevaPlatform = PlatformTextStyle(includeFontPadding = true)
private val DevaLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private fun deva(weight: FontWeight, size: Float, lineHeight: Float) = TextStyle(
    fontFamily = NotoDeva, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp,
    platformStyle = DevaPlatform, localeList = LocaleList("hi"), lineHeightStyle = DevaLineHeight,
)

/** Custom roles outside M3 (design-system §9.2 "VaakTypeExtras"). */
@Immutable
data class VaakTypeExtras(
    val prompt: TextStyle = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 27.sp),
    val quote: TextStyle = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 25.sp),
    val metric: TextStyle = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 29.sp, fontFeatureSettings = "tnum"),
    val hiHeadline: TextStyle = deva(FontWeight.SemiBold, 22f, 34f),
    val hiPrompt: TextStyle = deva(FontWeight.Medium, 20f, 34f),
    val hiBodyLarge: TextStyle = deva(FontWeight.Normal, 16.5f, 30.5f),
    val hiBodyMedium: TextStyle = deva(FontWeight.Normal, 15f, 27f),
)

val LocalVaakTypeExtras = staticCompositionLocalOf { VaakTypeExtras() }

/** Recording timer, capped at 1.3× font scale so 200% doesn't reach 144 sp on Android 13 and below. */
@Composable
fun timerStyle(): TextStyle {
    val scale = LocalDensity.current.fontScale
    val capped = 72f * minOf(scale, 1.3f) / scale
    return MaterialTheme.typography.displayMedium.copy(fontSize = capped.sp, lineHeight = capped.sp)
}
