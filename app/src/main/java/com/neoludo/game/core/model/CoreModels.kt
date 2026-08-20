package com.neoludo.game.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    DARK_OLED,
    LIGHT_TITANIUM
}

@Serializable
enum class BoardTheme(val displayName: String, val description: String) {
    CYBER_OBSIDIAN("Cyber Obsidian", "Deep space dark canvas with neon laser borders & glowing circuits"),
    ROYAL_PARCHMENT("Royal Parchment", "Vintage antique parchment board with warm gold filigree & wood trims"),
    SYNTHWAVE_NEON("Synthwave Neon", "80s retro cyber grid with electric magenta, cyan & hyperglow rails"),
    FROST_TITANIUM("Frost Titanium", "Sleek frosted ice-glass with crystalline borders & minimal sheen")
}

@Serializable
enum class DiceSkin(val displayName: String, val description: String) {
    PRISM_CRYSTAL("Prism Crystal", "Translucent refractive crystal with glowing neon pips"),
    CARBON_CYBER("Carbon Cyber", "High-tech woven carbon fiber with electric cyan pips"),
    ROYAL_GOLD("Royal Gold", "Polished 24k gold with inlaid ruby gem pips"),
    CLASSIC_IVORY("Classic Ivory", "Traditional resin ivory with smooth beveled dark pips")
}

@Serializable
enum class PawnSkin(val displayName: String, val description: String) {
    CYBER_PIPS("Cyber Pips", "Glass neon orb tokens with orbiting pulse ring"),
    ROYAL_CROWNS("Royal Crowns", "Sculpted 3D golden imperial crown with inlaid gem"),
    CRYSTAL_GEMS("Crystal Gems", "Faceted hexagonal gem tokens with crystal shine")
}

@Serializable
data class MatchRecord(
    val id: String = "match_" + System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String = "AI",
    val isWin: Boolean = false,
    val captures: Int = 0,
    val sixes: Int = 0,
    val winnerColor: String = "RED"
)

@Serializable
data class GameSettings(
    val themeMode: ThemeMode = ThemeMode.DARK_OLED,
    val boardTheme: BoardTheme = BoardTheme.CYBER_OBSIDIAN,
    val diceSkin: DiceSkin = DiceSkin.PRISM_CRYSTAL,
    val pawnSkin: PawnSkin = PawnSkin.CYBER_PIPS,
    val soundVolume: Float = 1.0f,
    val soundEnabled: Boolean = true,
    val musicVolume: Float = 0.7f,
    val musicEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val autoMoveSinglePiece: Boolean = true,
    val turnTimerSeconds: Int = 30,
    val penalty3xSix: Boolean = true,
    val reducedMotion: Boolean = false
)

@Serializable
data class UserProfile(
    val id: String = "user_" + (1000..9999).random(),
    val displayName: String = "Ludo Master",
    val avatarId: Int = 1,
    val playerTitle: String = "Grandmaster",
    val selectedDiceSkin: DiceSkin = DiceSkin.PRISM_CRYSTAL,
    val selectedPawnSkin: PawnSkin = PawnSkin.CYBER_PIPS,
    val selectedBoardTheme: BoardTheme = BoardTheme.CYBER_OBSIDIAN,
    val isAnonymous: Boolean = true
)

@Serializable
data class UserStats(
    val totalMatches: Int = 0,
    val totalWins: Int = 0,
    val totalCaptures: Int = 0,
    val totalSixes: Int = 0,
    val totalPiecesHome: Int = 0,
    val aiWins: Int = 0,
    val onlineWins: Int = 0,
    val localWins: Int = 0,
    val matchHistory: List<MatchRecord> = emptyList()
) {
    val winRate: Float
        get() = if (totalMatches > 0) (totalWins.toFloat() / totalMatches.toFloat()) * 100f else 0f
}

@Serializable
data class Friend(
    val id: String,
    val displayName: String,
    val avatarId: Int,
    val isOnline: Boolean = false,
    val statusMessage: String = "Ready to play"
)
