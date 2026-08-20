package com.neoludo.game.core.designsystem

import androidx.compose.ui.graphics.Color
import com.neoludo.game.core.model.BoardTheme
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

    fun getPlayerColor(color: PlayerColor, theme: BoardTheme = BoardTheme.CYBER_OBSIDIAN): Color {
        val palette = getBoardColors(theme)
        return when (color) {
            PlayerColor.RED -> palette.red
            PlayerColor.GREEN -> palette.green
            PlayerColor.YELLOW -> palette.yellow
            PlayerColor.BLUE -> palette.blue
        }
    }

    fun getPlayerGlow(color: PlayerColor, theme: BoardTheme = BoardTheme.CYBER_OBSIDIAN): Color {
        val palette = getBoardColors(theme)
        return when (color) {
            PlayerColor.RED -> palette.redGlow
            PlayerColor.GREEN -> palette.greenGlow
            PlayerColor.YELLOW -> palette.yellowGlow
            PlayerColor.BLUE -> palette.blueGlow
        }
    }

    fun getPlayerContainer(color: PlayerColor, theme: BoardTheme = BoardTheme.CYBER_OBSIDIAN): Color {
        val palette = getBoardColors(theme)
        return when (color) {
            PlayerColor.RED -> palette.redContainer
            PlayerColor.GREEN -> palette.greenContainer
            PlayerColor.YELLOW -> palette.yellowContainer
            PlayerColor.BLUE -> palette.blueContainer
        }
    }

    fun getBoardColors(theme: BoardTheme): LudoBoardPalette = when (theme) {
        BoardTheme.CYBER_OBSIDIAN -> CyberObsidianPalette
        BoardTheme.ROYAL_PARCHMENT -> RoyalParchmentPalette
        BoardTheme.SYNTHWAVE_NEON -> SynthwaveNeonPalette
        BoardTheme.FROST_TITANIUM -> FrostTitaniumPalette
    }
}

data class LudoBoardPalette(
    val theme: BoardTheme,
    val background: Color,
    val cardSurface: Color,
    val boardBorder: Color,
    val boardBorderGlow: Color,
    val cellPathDefault: Color,
    val cellBorder: Color,
    val starSafeColor: Color,
    val centerHomeColor: Color,
    val red: Color,
    val redGlow: Color,
    val redContainer: Color,
    val green: Color,
    val greenGlow: Color,
    val greenContainer: Color,
    val yellow: Color,
    val yellowGlow: Color,
    val yellowContainer: Color,
    val blue: Color,
    val blueGlow: Color,
    val blueContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean = true
)

val CyberObsidianPalette = LudoBoardPalette(
    theme = BoardTheme.CYBER_OBSIDIAN,
    background = Color(0xFF080B10),
    cardSurface = Color(0xFF111622),
    boardBorder = Color(0xFF243048),
    boardBorderGlow = Color(0xFF3B4D6E),
    cellPathDefault = Color(0xFF151C2C),
    cellBorder = Color(0xFF2A3955),
    starSafeColor = Color(0xFFFFD200),
    centerHomeColor = Color(0xFF111622),
    red = Color(0xFFFF2B66),
    redGlow = Color(0xFFFF6B93),
    redContainer = Color(0xFF3D1120),
    green = Color(0xFF00F076),
    greenGlow = Color(0xFF69F0AE),
    greenContainer = Color(0xFF0D3823),
    yellow = Color(0xFFFFD200),
    yellowGlow = Color(0xFFFFEA75),
    yellowContainer = Color(0xFF3D3205),
    blue = Color(0xFF2B7FFF),
    blueGlow = Color(0xFF82B1FF),
    blueContainer = Color(0xFF0E254A),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    isDark = true
)

val RoyalParchmentPalette = LudoBoardPalette(
    theme = BoardTheme.ROYAL_PARCHMENT,
    background = Color(0xFF1F140E),
    cardSurface = Color(0xFFF5E6C8),
    boardBorder = Color(0xFF5D4037),
    boardBorderGlow = Color(0xFFD4AF37),
    cellPathDefault = Color(0xFFEEDBBA),
    cellBorder = Color(0xFFBCAAA4),
    starSafeColor = Color(0xFFD4AF37),
    centerHomeColor = Color(0xFFE5D2AE),
    red = Color(0xFFC62828),
    redGlow = Color(0xFFE53935),
    redContainer = Color(0xFF491717),
    green = Color(0xFF2E7D32),
    greenGlow = Color(0xFF43A047),
    greenContainer = Color(0xFF143B17),
    yellow = Color(0xFFD97706),
    yellowGlow = Color(0xFFF59E0B),
    yellowContainer = Color(0xFF4D2B05),
    blue = Color(0xFF1565C0),
    blueGlow = Color(0xFF1E88E5),
    blueContainer = Color(0xFF0B2C54),
    textPrimary = Color(0xFF2E1C0C),
    textSecondary = Color(0xFF6D4C41),
    isDark = false
)

val SynthwaveNeonPalette = LudoBoardPalette(
    theme = BoardTheme.SYNTHWAVE_NEON,
    background = Color(0xFF12072B),
    cardSurface = Color(0xFF261047),
    boardBorder = Color(0xFF52188A),
    boardBorderGlow = Color(0xFFFF007F),
    cellPathDefault = Color(0xFF2D1452),
    cellBorder = Color(0xFF6B21A8),
    starSafeColor = Color(0xFFFFE600),
    centerHomeColor = Color(0xFF200B3B),
    red = Color(0xFFFF007F),
    redGlow = Color(0xFFFF52AF),
    redContainer = Color(0xFF4A052A),
    green = Color(0xFF00F0FF),
    greenGlow = Color(0xFF66FAFF),
    greenContainer = Color(0xFF04383B),
    yellow = Color(0xFFFFE600),
    yellowGlow = Color(0xFFFFF152),
    yellowContainer = Color(0xFF473F02),
    blue = Color(0xFF8B00FF),
    blueGlow = Color(0xFFB552FF),
    blueContainer = Color(0xFF2B0052),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFC084FC),
    isDark = true
)

val FrostTitaniumPalette = LudoBoardPalette(
    theme = BoardTheme.FROST_TITANIUM,
    background = Color(0xFF0F172A),
    cardSurface = Color(0xFFF8FAFC),
    boardBorder = Color(0xFF334155),
    boardBorderGlow = Color(0xFF64748B),
    cellPathDefault = Color(0xFFEDF2F7),
    cellBorder = Color(0xFFCBD5E1),
    starSafeColor = Color(0xFFD97706),
    centerHomeColor = Color(0xFFFFFFFF),
    red = Color(0xFFE11D48),
    redGlow = Color(0xFFFB7185),
    redContainer = Color(0xFF3B101C),
    green = Color(0xFF059669),
    greenGlow = Color(0xFF34D399),
    greenContainer = Color(0xFF072B1E),
    yellow = Color(0xFFD97706),
    yellowGlow = Color(0xFFFBBF24),
    yellowContainer = Color(0xFF381F04),
    blue = Color(0xFF2563EB),
    blueGlow = Color(0xFF60A5FA),
    blueContainer = Color(0xFF0D255A),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    isDark = false
)
