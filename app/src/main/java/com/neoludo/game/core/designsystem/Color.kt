package com.neoludo.game.core.designsystem

import androidx.compose.ui.graphics.Color
import com.neoludo.game.engine.model.PlayerColor

object NeoLudoColors {
    // Obsidian Dark Theme
    val ObsidianBackground = Color(0xFF0B0E14)
    val ObsidianSurface = Color(0xFF151B26)
    val ObsidianSurfaceElevated = Color(0xFF1F2737)
    val ObsidianSurfaceCard = Color(0xFF1A2232)
    val ObsidianBorder = Color(0xFF28344A)
    val ObsidianBorderGlow = Color(0xFF3B4D6E)
    val ObsidianTextPrimary = Color(0xFFF1F5F9)
    val ObsidianTextSecondary = Color(0xFF94A3B8)
    val ObsidianTextMuted = Color(0xFF64748B)

    // Titanium Light Theme
    val TitaniumBackground = Color(0xFFF1F5F9)
    val TitaniumSurface = Color(0xFFFFFFFF)
    val TitaniumSurfaceElevated = Color(0xFFE2E8F0)
    val TitaniumSurfaceCard = Color(0xFFF8FAFC)
    val TitaniumBorder = Color(0xFFCBD5E1)
    val TitaniumBorderGlow = Color(0xFF94A3B8)
    val TitaniumTextPrimary = Color(0xFF0F172A)
    val TitaniumTextSecondary = Color(0xFF475569)
    val TitaniumTextMuted = Color(0xFF94A3B8)

    // Player Colors: Ruby Red
    val RubyRed = Color(0xFFFF3366)
    val RubyRedDark = Color(0xFFC2185B)
    val RubyRedGlow = Color(0xFFFF6B93)
    val RubyRedContainer = Color(0xFF3D1120)
    val RubyRedSurface = Color(0xFF240A13)

    // Player Colors: Emerald Green
    val EmeraldGreen = Color(0xFF00E676)
    val EmeraldGreenDark = Color(0xFF00897B)
    val EmeraldGreenGlow = Color(0xFF69F0AE)
    val EmeraldGreenContainer = Color(0xFF0D3823)
    val EmeraldGreenSurface = Color(0xFF072115)

    // Player Colors: Amber Yellow
    val AmberYellow = Color(0xFFFFD600)
    val AmberYellowDark = Color(0xFFFF8F00)
    val AmberYellowGlow = Color(0xFFFFEA75)
    val AmberYellowContainer = Color(0xFF3D3205)
    val AmberYellowSurface = Color(0xFF241D02)

    // Player Colors: Cobalt Blue
    val CobaltBlue = Color(0xFF2979FF)
    val CobaltBlueDark = Color(0xFF1565C0)
    val CobaltBlueGlow = Color(0xFF82B1FF)
    val CobaltBlueContainer = Color(0xFF0E254A)
    val CobaltBlueSurface = Color(0xFF08172E)

    fun getPlayerColor(color: PlayerColor): Color = when (color) {
        PlayerColor.RED -> RubyRed
        PlayerColor.GREEN -> EmeraldGreen
        PlayerColor.YELLOW -> AmberYellow
        PlayerColor.BLUE -> CobaltBlue
    }

    fun getPlayerGlow(color: PlayerColor): Color = when (color) {
        PlayerColor.RED -> RubyRedGlow
        PlayerColor.GREEN -> EmeraldGreenGlow
        PlayerColor.YELLOW -> AmberYellowGlow
        PlayerColor.BLUE -> CobaltBlueGlow
    }

    fun getPlayerContainer(color: PlayerColor): Color = when (color) {
        PlayerColor.RED -> RubyRedContainer
        PlayerColor.GREEN -> EmeraldGreenContainer
        PlayerColor.YELLOW -> AmberYellowContainer
        PlayerColor.BLUE -> CobaltBlueContainer
    }
}
