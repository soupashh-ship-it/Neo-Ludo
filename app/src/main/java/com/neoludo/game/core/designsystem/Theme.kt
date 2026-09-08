package com.neoludo.game.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoludo.game.core.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = NeoLudoColors.CobaltBlue,
    onPrimary = Color.White,
    primaryContainer = NeoLudoColors.CobaltBlueContainer,
    secondary = NeoLudoColors.EmeraldGreen,
    onSecondary = Color.Black,
    secondaryContainer = NeoLudoColors.EmeraldGreenContainer,
    tertiary = NeoLudoColors.RubyRed,
    background = NeoLudoColors.ObsidianBackground,
    surface = NeoLudoColors.ObsidianSurface,
    surfaceVariant = NeoLudoColors.ObsidianSurfaceCard,
    onBackground = NeoLudoColors.ObsidianTextPrimary,
    onSurface = NeoLudoColors.ObsidianTextPrimary,
    outline = NeoLudoColors.ObsidianBorder
)

private val LightColorScheme = lightColorScheme(
    primary = NeoLudoColors.CobaltBlue,
    onPrimary = Color.White,
    primaryContainer = NeoLudoColors.CobaltBlueGlow,
    secondary = NeoLudoColors.EmeraldGreen,
    onSecondary = Color.Black,
    secondaryContainer = NeoLudoColors.EmeraldGreenGlow,
    tertiary = NeoLudoColors.RubyRed,
    background = NeoLudoColors.TitaniumBackground,
    surface = NeoLudoColors.TitaniumSurface,
    surfaceVariant = NeoLudoColors.TitaniumSurfaceCard,
    onBackground = NeoLudoColors.TitaniumTextPrimary,
    onSurface = NeoLudoColors.TitaniumTextPrimary,
    outline = NeoLudoColors.TitaniumBorder
)

/**
 * Club Brutalist type scale — single rule-documented system.
 * Display: heavy grotesk, tight tracking. Body: humanist sans.
 * Codes/stats/timers: monospace tabular (no proportional-number jitter).
 * Section labels: sentence case, never ALL-CAPS muted.
 */
val NeoLudoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

/** Single shape scale — 14dp everywhere unless documented otherwise. */
val NeoLudoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

/** Spacing tokens — replaces Spacer(12/16/20/24…) magic numbers. */
object NeoLudoSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

@Composable
fun NeoLudoTheme(
    themeMode: ThemeMode = ThemeMode.DARK_OLED,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK_OLED -> true
        ThemeMode.LIGHT_TITANIUM -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeoLudoTypography,
        shapes = NeoLudoShapes,
        content = content
    )
}
