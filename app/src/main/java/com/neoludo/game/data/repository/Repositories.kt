package com.neoludo.game.data.repository

import com.neoludo.game.core.model.Friend
import com.neoludo.game.core.model.GameSettings
import com.neoludo.game.core.model.MatchRecord
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import com.neoludo.game.data.datastore.PreferencesDataStore
import com.neoludo.game.core.model.BoardTheme
import com.neoludo.game.core.model.DiceSkin
import com.neoludo.game.core.model.PawnSkin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsRepository(private val dataStore: PreferencesDataStore) {
    val settings: Flow<GameSettings> = dataStore.settingsFlow

    suspend fun getSettings(): GameSettings = dataStore.settingsFlow.first()

    suspend fun updateSettings(settings: GameSettings) {
        dataStore.updateSettings(settings)
    }
}

class ProfileRepository(private val dataStore: PreferencesDataStore) {
    val profile: Flow<UserProfile> = dataStore.profileFlow
    private val mutex = Mutex()

    suspend fun getProfile(): UserProfile = dataStore.profileFlow.first()

    /**
     * Fresh installs start with an UNSAVED default profile whose id is
     * re-minted (`user_XXXX`, only 9000 values) on every DataStore emission —
     * any settings write would silently change your online identity
     * mid-session and break seat matching (or collide with a friend's id).
     * Mint one UUID-based id and persist it exactly once at startup.
     */
    suspend fun ensureStableProfile(): UserProfile = mutex.withLock {
        if (!dataStore.hasSavedProfile()) {
            val fresh = UserProfile(id = "user_" + java.util.UUID.randomUUID().toString().take(8))
            dataStore.saveProfile(fresh)
            fresh
        } else {
            val current = dataStore.profileFlow.first()
            if (current.id.isBlank()) {
                val fixed = current.copy(id = "user_" + java.util.UUID.randomUUID().toString().take(8))
                dataStore.saveProfile(fixed)
                fixed
            } else current
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        mutex.withLock { dataStore.saveProfile(profile) }
    }

    suspend fun addCurrency(coins: Int, gems: Int = 0) {
        mutex.withLock {
            val current = dataStore.profileFlow.first()
            dataStore.saveProfile(current.copy(coins = current.coins + coins, gems = current.gems + gems))
        }
    }

    suspend fun spendCurrency(coins: Int, gems: Int = 0): Boolean {
        return mutex.withLock {
            val current = dataStore.profileFlow.first()
            if (current.coins >= coins && current.gems >= gems) {
                dataStore.saveProfile(current.copy(coins = current.coins - coins, gems = current.gems - gems))
                true
            } else false
        }
    }

    suspend fun unlockBoardTheme(theme: BoardTheme, costCoins: Int, costGems: Int = 0): Boolean {
        return mutex.withLock {
            val current = dataStore.profileFlow.first()
            if (current.coins >= costCoins && current.gems >= costGems) {
                dataStore.saveProfile(
                    current.copy(
                        coins = current.coins - costCoins,
                        gems = current.gems - costGems,
                        unlockedThemes = current.unlockedThemes + theme
                    )
                )
                true
            } else false
        }
    }

    suspend fun unlockDiceSkin(skin: DiceSkin, costCoins: Int, costGems: Int = 0): Boolean {
        return mutex.withLock {
            val current = dataStore.profileFlow.first()
            if (current.coins >= costCoins && current.gems >= costGems) {
                dataStore.saveProfile(
                    current.copy(
                        coins = current.coins - costCoins,
                        gems = current.gems - costGems,
                        unlockedDice = current.unlockedDice + skin
                    )
                )
                true
            } else false
        }
    }

    suspend fun unlockPawnSkin(skin: PawnSkin, costCoins: Int, costGems: Int = 0): Boolean {
        return mutex.withLock {
            val current = dataStore.profileFlow.first()
            if (current.coins >= costCoins && current.gems >= costGems) {
                dataStore.saveProfile(
                    current.copy(
                        coins = current.coins - costCoins,
                        gems = current.gems - costGems,
                        unlockedPawns = current.unlockedPawns + skin
                    )
                )
                true
            } else false
        }
    }

    /** Daily reward: atomic claim-once-per-day. Returns false if already claimed today. */
    suspend fun claimDailyReward(coins: Int = 200, gems: Int = 10): Boolean {
        return mutex.withLock {
            val today = java.time.LocalDate.now().toEpochDay()
            val lastClaim = dataStore.getLastDailyClaimDay()
            if (lastClaim == today) return false
            val current = dataStore.profileFlow.first()
            dataStore.saveProfile(current.copy(coins = current.coins + coins, gems = current.gems + gems))
            dataStore.setLastDailyClaimDay(today)
            true
        }
    }

    suspend fun isDailyClaimedToday(): Boolean {
        val today = java.time.LocalDate.now().toEpochDay()
        return dataStore.getLastDailyClaimDay() == today
    }
}

class StatsRepository(private val dataStore: PreferencesDataStore) {
    val stats: Flow<UserStats> = dataStore.statsFlow
    private val mutex = Mutex()

    suspend fun getStats(): UserStats = dataStore.statsFlow.first()

    suspend fun recordMatchResult(
        isWin: Boolean,
        mode: String,
        capturesMade: Int,
        sixesRolled: Int,
        piecesHome: Int,
        winnerColor: String = "RED"
    ) {
        mutex.withLock {
            val current = dataStore.statsFlow.first()
        val newRecord = MatchRecord(
            id = "match_${System.currentTimeMillis()}_${(100..999).random()}",
            timestamp = System.currentTimeMillis(),
            mode = mode,
            isWin = isWin,
            captures = capturesMade,
            sixes = sixesRolled,
            winnerColor = winnerColor
        )
        val updatedHistory = (listOf(newRecord) + current.matchHistory).take(50)
        val updated = current.copy(
            totalMatches = current.totalMatches + 1,
            totalWins = if (isWin) current.totalWins + 1 else current.totalWins,
            totalCaptures = current.totalCaptures + capturesMade,
            totalSixes = current.totalSixes + sixesRolled,
            totalPiecesHome = current.totalPiecesHome + piecesHome,
            aiWins = if (isWin && mode == "AI") current.aiWins + 1 else current.aiWins,
            onlineWins = if (isWin && mode == "ONLINE") current.onlineWins + 1 else current.onlineWins,
            localWins = if (isWin && mode == "LOCAL") current.localWins + 1 else current.localWins,
            matchHistory = updatedHistory
            )
            dataStore.saveStats(updated)
        }
    }
}

class FriendRepository(
    private val dataStore: PreferencesDataStore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()
    private val mutex = Mutex()

    init {
        scope.launch {
            dataStore.friendsFlow.collect {
                _friends.value = it
            }
        }
    }

    fun addFriend(id: String, name: String, avatarId: Int) {
        val trimmed = name.trim().take(30)
        if (trimmed.isBlank()) return
        scope.launch {
            mutex.withLock {
                val current = _friends.value.toMutableList()
                if (current.none { it.id == id }) {
                    current.add(Friend(id, trimmed, avatarId, isOnline = true, statusMessage = "Just added"))
                    _friends.value = current.toList()
                    dataStore.saveFriends(current.toList())
                }
            }
        }
    }

    fun removeFriend(id: String) {
        scope.launch {
            mutex.withLock {
                val updated = _friends.value.filterNot { it.id == id }
                _friends.value = updated
                dataStore.saveFriends(updated)
            }
        }
    }
}
