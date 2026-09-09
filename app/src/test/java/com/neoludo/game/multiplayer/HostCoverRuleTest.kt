package com.neoludo.game.multiplayer

import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.model.PlayerPresence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The host may act for bots / disconnected / missing seats, but never for a
 * connected human's turn (otherwise the host plays the whole game alone).
 * Pure logic, no network.
 */
class HostCoverRuleTest {

    private fun presence(connected: Boolean, isAi: Boolean) = PlayerPresence(
        id = "other",
        name = "Other",
        avatarId = 1,
        color = PlayerColor.GREEN,
        isHost = false,
        isReady = true,
        isConnected = connected,
        isAi = isAi,
        joinedAt = 1L,
        lastSeen = 2L
    )

    @Test
    fun `non-host never covers`() {
        assertFalse(canHostCoverTurn(false, null))
        assertFalse(canHostCoverTurn(false, presence(true, false)))
    }

    @Test
    fun `host covers bot turns`() {
        assertTrue(canHostCoverTurn(true, presence(true, true)))
    }

    @Test
    fun `host covers disconnected humans`() {
        assertTrue(canHostCoverTurn(true, presence(false, false)))
    }

    @Test
    fun `host covers seats missing from presence`() {
        assertTrue(canHostCoverTurn(true, null))
    }

    @Test
    fun `host never covers a connected human`() {
        assertFalse(canHostCoverTurn(true, presence(true, false)))
    }
}
