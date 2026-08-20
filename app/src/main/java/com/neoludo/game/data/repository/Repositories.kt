package com.neoludo.game.data.repository

import com.neoludo.game.core.model.Friend
import com.neoludo.game.core.model.GameSettings
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import com.neoludo.game.data.datastore.PreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class SettingsRepository(private val dataStore: PreferencesDataStore) {
    val settings: Flow<GameSettings> = dataStore.settingsFlow

    suspend fun getSettings(): GameSettings = dataStore.settingsFlow.first()

    suspend fun updateSettings(settings: GameSettings) {
        dataStore.updateSettings(settings)
    }
}

class ProfileRepository(private val dataStore: PreferencesDataStore) {
    val profile: Flow<UserProfile> = dataStore.profileFlow

    suspend fun getProfile(): UserProfile = dataStore.profileFlow.first()

    suspend fun updateProfile(profile: UserProfile) {
        dataStore.saveProfile(profile)
    }
}

class StatsRepository(private val dataStore: PreferencesDataStore) {
    val stats: Flow<UserStats> = dataStore.statsFlow

    suspend fun getStats(): UserStats = dataStore.statsFlow.first()

    suspend fun recordMatchResult(
        isWin: Boolean,
        mode: String,
        capturesMade: Int,
        sixesRolled: Int,
        piecesHome: Int
    ) {
        val current = getStats()
        val updated = current.copy(
            totalMatches = current.totalMatches + 1,
            totalWins = if (isWin) current.totalWins + 1 else current.totalWins,
            totalCaptures = current.totalCaptures + capturesMade,
            totalSixes = current.totalSixes + sixesRolled,
            totalPiecesHome = current.totalPiecesHome + piecesHome,
            aiWins = if (isWin && mode == "AI") current.aiWins + 1 else current.aiWins,
            onlineWins = if (isWin && mode == "ONLINE") current.onlineWins + 1 else current.onlineWins,
            localWins = if (isWin && mode == "LOCAL") current.localWins + 1 else current.localWins
        )
        dataStore.saveStats(updated)
    }
}

class FriendRepository {
    private val _friends = MutableStateFlow(
        listOf(
            Friend("f_1", "NovaKnight", 2, isOnline = true, statusMessage = "In Lobby"),
            Friend("f_2", "CyberDice", 5, isOnline = true, statusMessage = "Looking for match"),
            Friend("f_3", "AuraQueen", 8, isOnline = false, statusMessage = "Offline 2h ago"),
            Friend("f_4", "ShadowPawn", 11, isOnline = true, statusMessage = "In Game (Turn 14)"),
            Friend("f_5", "PixelRuler", 14, isOnline = false, statusMessage = "Offline 1d ago")
        )
    )
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    fun addFriend(id: String, name: String, avatarId: Int) {
        val current = _friends.value.toMutableList()
        if (current.none { it.id == id }) {
            current.add(Friend(id, name, avatarId, isOnline = true, statusMessage = "Just added"))
            _friends.value = current
        }
    }

    fun removeFriend(id: String) {
        _friends.value = _friends.value.filterNot { it.id == id }
    }
}
