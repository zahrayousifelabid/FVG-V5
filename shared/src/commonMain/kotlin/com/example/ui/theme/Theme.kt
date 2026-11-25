package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val FvgLightColorScheme = lightColorScheme(
    primary = FvgDeepGreen,
    onPrimary = FvgWarmIvory,
    primaryContainer = FvgSoftCream,
    onPrimaryContainer = FvgDeepGreen,
    secondary = FvgWarmGold,
    onSecondary = FvgDeepGreen,
    secondaryContainer = Color(0xFFF7E7C8),
    onSecondaryContainer = FvgDeepGreen,
    tertiary = FvgSoftTeal,
    onTertiary = Color.White,
    background = FvgWarmIvory,
    onBackground = FvgInkDark,
    surface = FvgCardWhite,
    onSurface = FvgInkDark,
    surfaceVariant = FvgSoftCream,
    onSurfaceVariant = FvgMutedGrey,
    surfaceContainerLow = FvgWarmIvory,
    surfaceContainer = FvgSoftCream,
    surfaceContainerHigh = FvgWarmStone,
    outline = FvgWarmStone,
    outlineVariant = Color(0xFFE7E1D3),
    error = FvgNegative,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val FvgDarkColorScheme = darkColorScheme(
    primary = FvgWarmGold,
    onPrimary = FvgDeepGreen,
    primaryContainer = FvgForestGreen,
    onPrimaryContainer = FvgWarmIvory,
    secondary = FvgSoftTeal,
    onSecondary = Color.White,
    secondaryContainer = FvgDarkSurfaceHigh,
    onSecondaryContainer = FvgWarmGold,
    tertiary = FvgAntiqueGold,
    onTertiary = Color.White,
    background = FvgDarkBg,
    onBackground = FvgDarkText,
    surface = FvgDarkSurface,
    onSurface = FvgDarkText,
    surfaceVariant = FvgDarkSurfaceHigh,
    onSurfaceVariant = FvgDarkSubText,
    surfaceContainerLow = FvgDarkBg,
    surfaceContainer = FvgDarkSurface,
    surfaceContainerHigh = FvgDarkSurfaceHigh,
    outline = FvgDarkOutline,
    outlineVariant = FvgForestGreen,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val FvgShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FvgTheme(
    themeMode: String = "light",
    darkTheme: Boolean? = null,
    isArabic: Boolean = true,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val resolvedDark = darkTheme ?: when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (resolvedDark) FvgDarkColorScheme else FvgLightColorScheme
    val direction = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val montserrat = rememberMontserratFontFamily()
    val cairo = rememberCairoFontFamily()
    CompositionLocalProvider(
        LocalLayoutDirection provides direction,
        LocalMontserratFontFamily provides montserrat,
        LocalCairoFontFamily provides cairo
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = fvgTypography(isArabic, fontScale),
            shapes = FvgShapes,
            content = content
        )
    }
}
