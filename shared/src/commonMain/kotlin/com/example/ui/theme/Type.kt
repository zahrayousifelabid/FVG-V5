package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.shared.resources.Res
import com.example.shared.resources.cairo
import com.example.shared.resources.montserrat
import org.jetbrains.compose.resources.Font

val LocalMontserratFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }
val LocalCairoFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

val MontserratFontFamily: FontFamily
    @Composable
    @ReadOnlyComposable
    get() = LocalMontserratFontFamily.current

val CairoFontFamily: FontFamily
    @Composable
    @ReadOnlyComposable
    get() = LocalCairoFontFamily.current

@Composable
fun rememberMontserratFontFamily(): FontFamily = FontFamily(
    Font(Res.font.montserrat, FontWeight.Normal),
    Font(Res.font.montserrat, FontWeight.Medium),
    Font(Res.font.montserrat, FontWeight.SemiBold),
    Font(Res.font.montserrat, FontWeight.Bold)
)

@Composable
fun rememberCairoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.cairo, FontWeight.Normal),
    Font(Res.font.cairo, FontWeight.Medium),
    Font(Res.font.cairo, FontWeight.SemiBold),
    Font(Res.font.cairo, FontWeight.Bold)
)

@Composable
fun fvgTypography(isArabic: Boolean, fontScale: Float = 1.0f): Typography {
    val montserrat = MontserratFontFamily
    val cairo = CairoFontFamily
    val family = if (isArabic) cairo else montserrat
    val s = fontScale.coerceIn(0.85f, 1.30f)
    return Typography(
        displayLarge = TextStyle(
            fontFamily = montserrat,
            fontWeight = FontWeight.Bold,
            fontSize = (42f * s).sp,
            lineHeight = (46f * s).sp,
            letterSpacing = 1.sp
        ),
        displayMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (34f * s).sp,
            lineHeight = (40f * s).sp
        ),
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (28f * s).sp,
            lineHeight = (34f * s).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (22f * s).sp,
            lineHeight = (28f * s).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (20f * s).sp,
            lineHeight = (26f * s).sp
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18f * s).sp,
            lineHeight = (24f * s).sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16f * s).sp,
            lineHeight = (22f * s).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (15f * s).sp,
            lineHeight = (22f * s).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (13.5f * s).sp,
            lineHeight = (20f * s).sp
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (12f * s).sp,
            lineHeight = (17f * s).sp
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13f * s).sp,
            lineHeight = (18f * s).sp
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (11f * s).sp,
            lineHeight = (14f * s).sp
        )
    )
}
