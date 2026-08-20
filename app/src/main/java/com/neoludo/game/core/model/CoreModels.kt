package com.neoludo.game.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    DARK_OLED,
    LIGHT_TITANIUM
}

@Serializable
data class GameSettings(
    val themeMode: ThemeMode = ThemeMode.DARK_OLED,
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
    val localWins: Int = 0
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
