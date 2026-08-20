package com.neoludo.game.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
        content = content
    )
}
