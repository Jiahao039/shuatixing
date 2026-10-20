package com.shutixing.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val InkBlue = Color(0xFF1B4B8F)
val DeepTeal = Color(0xFF0E7C86)
val Amber = Color(0xFFF59E0B)
val CorrectGreen = Color(0xFF2E9E6B)
val WrongRed = Color(0xFFE5484D)

val BgLight = Color(0xFFF6F7FB)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEDF0F7)
val TextPrimaryLight = Color(0xFF101828)
val TextSecondaryLight = Color(0xFF667085)
val OutlineLight = Color(0xFFD0D5DD)

val BgDark = Color(0xFF0B1220)
val SurfaceDark = Color(0xFF131C2E)
val SurfaceVariantDark = Color(0xFF1C2740)
val TextPrimaryDark = Color(0xFFE6EAF2)
val TextSecondaryDark = Color(0xFFA3B0C6)
val OutlineDark = Color(0xFF3A4763)

val HeroGradientStart = Color(0xFF1B4B8F)
val HeroGradientEnd = Color(0xFF0E7C86)
val AmberGradientStart = Color(0xFFF59E0B)
val AmberGradientEnd = Color(0xFFF97316)

val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF0A2547),
    secondary = DeepTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F2F0),
    onSecondaryContainer = Color(0xFF05332F),
    tertiary = Amber,
    onTertiary = Color(0xFF3A2500),
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE4E8F0),
    error = WrongRed,
    onError = Color.White,
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC3FF),
    onPrimary = Color(0xFF0B2A52),
    primaryContainer = Color(0xFF1C3A66),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = Color(0xFF5FD3C4),
    onSecondary = Color(0xFF04352F),
    secondaryContainer = Color(0xFF124F4A),
    onSecondaryContainer = Color(0xFFC7F4ED),
    tertiary = Color(0xFFFFC44D),
    onTertiary = Color(0xFF3A2500),
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF26324B),
    error = Color(0xFFFF8A8F),
    onError = Color(0xFF3A0004),
)
