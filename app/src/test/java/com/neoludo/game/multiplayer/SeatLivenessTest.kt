package com.neoludo.game.multiplayer

import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.model.PlayerPresence
import org.junit.Assert.assertEquals
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
    @Test
    fun `simultaneous fifth join deterministically loses final slot race`() {
        val now = 1_000_000L
        fun p(id: String, joinedAt: Long) = PlayerPresence(
            id = id,
            name = id,
            avatarId = 1,
            color = PlayerColor.RED,
            isHost = id == "a",
            isReady = true,
            isConnected = true,
            isAi = false,
            joinedAt = joinedAt,
            lastSeen = now
        )
        val admitted = selectAdmittedPlayerIds(
            listOf(p("a", 1), p("b", 2), p("c", 3), p("d", 4), p("e", 5)),
            maxPlayers = 4,
            now = now
        )
        assertEquals(setOf("a", "b", "c", "d"), admitted)
        assertFalse("e" in admitted)
    }

    @Test
    fun `admission tiebreaks equal timestamps by stable id`() {
        val now = 1_000_000L
        fun p(id: String) = PlayerPresence(
            id = id, name = id, avatarId = 1, color = PlayerColor.RED,
            isConnected = true, joinedAt = 100, lastSeen = now
        )
        val admitted = selectAdmittedPlayerIds(listOf(p("e"), p("c"), p("a"), p("d"), p("b")), 4, now)
        assertEquals(setOf("a", "b", "c", "d"), admitted)
    }

}
