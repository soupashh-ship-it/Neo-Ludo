package com.neoludo.game.data

import com.google.common.truth.Truth.assertThat
import com.neoludo.game.core.model.BoardTheme
import com.neoludo.game.core.model.DiceSkin
import com.neoludo.game.core.model.MatchRecord
import com.neoludo.game.core.model.PawnSkin
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import org.junit.Test

class EconomyAndStatsTest {

    @Test
    fun testUserStatsWinRateCalculation() {
        val emptyStats = UserStats()
        assertThat(emptyStats.winRate).isEqualTo(0f)

        val stats = UserStats(
            totalMatches = 10,
            totalWins = 7,
            totalCaptures = 24,
            totalSixes = 18,
            totalPiecesHome = 28
        )
        assertThat(stats.winRate).isEqualTo(70f)
    }

    @Test
    fun testMatchRecordHistoryCappedAt50() {
        var stats = UserStats()
        for (i in 1..60) {
            val record = MatchRecord(
                id = "match_$i",
                timestamp = System.currentTimeMillis(),
                mode = "AI",
                isWin = i % 2 == 0,
                captures = 2,
                sixes = 1
            )
            val updatedHistory = (listOf(record) + stats.matchHistory).take(50)
            stats = stats.copy(
                totalMatches = stats.totalMatches + 1,
                totalWins = if (record.isWin) stats.totalWins + 1 else stats.totalWins,
                matchHistory = updatedHistory
            )
        }

        assertThat(stats.totalMatches).isEqualTo(60)
        assertThat(stats.totalWins).isEqualTo(30)
        assertThat(stats.matchHistory.size).isEqualTo(50)
        assertThat(stats.matchHistory.first().id).isEqualTo("match_60")
    }

    @Test
    fun testUserProfileCurrencyAndUnlock() {
        var profile = UserProfile(
            coins = 10000,
            gems = 50,
            unlockedThemes = setOf(BoardTheme.CLASSIC_ARCADE, BoardTheme.CYBER_OBSIDIAN)
        )

        // Attempting to unlock ROYAL_PARCHMENT (cost: 5000 coins)
        val costCoins = 5000
        val costGems = 0
        assertThat(profile.coins >= costCoins).isTrue()

        profile = profile.copy(
            coins = profile.coins - costCoins,
            gems = profile.gems - costGems,
            unlockedThemes = profile.unlockedThemes + BoardTheme.ROYAL_PARCHMENT
        )

        assertThat(profile.coins).isEqualTo(5000)
        assertThat(profile.unlockedThemes).contains(BoardTheme.ROYAL_PARCHMENT)
        assertThat(BoardTheme.ROYAL_PARCHMENT in profile.unlockedThemes).isTrue()

        // Attempting to unlock without enough coins
        val expensiveThemeCost = 10000
        val canAfford = profile.coins >= expensiveThemeCost
        assertThat(canAfford).isFalse()
    }
}
