package com.neoludo.game.multiplayer

import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.model.PlayerPresence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only live seats (connected or recently heartbeating) may fill the room or
 * reserve colors — stale offline presences are broker litter. Pure logic.
 */
class SeatLivenessTest {

    private fun seat(connected: Boolean, lastSeenAgoMs: Long, now: Long) = PlayerPresence(
        id = "s",
        name = "S",
        avatarId = 1,
        color = PlayerColor.RED,
        isHost = false,
        isReady = true,
        isConnected = connected,
        isAi = false,
        joinedAt = now - 120_000L,
        lastSeen = now - lastSeenAgoMs
    )

    @Test
    fun `connected seats are live`() {
        assertTrue(isSeatLive(seat(true, 120_000L, 1_000_000L), 1_000_000L))
    }

    @Test
    fun `recently heartbeating offline seats are live`() {
        assertTrue(isSeatLive(seat(false, 10_000L, 1_000_000L), 1_000_000L))
    }

    @Test
    fun `long-dead offline seats are ghosts`() {
        assertFalse(isSeatLive(seat(false, 300_000L, 1_000_000L), 1_000_000L))
    }
}
