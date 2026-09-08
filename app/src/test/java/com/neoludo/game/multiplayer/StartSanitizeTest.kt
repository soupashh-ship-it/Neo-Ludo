package com.neoludo.game.multiplayer

import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.model.PlayerPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Game start must never crash on a skewed roster (slow networks can deliver
 * duplicate colors). Pure logic, no network.
 */
class StartSanitizeTest {

    private fun seat(uid: String, color: PlayerColor) = PlayerPresence(
        id = uid,
        name = "P-$uid",
        avatarId = 1,
        color = color,
        isHost = false,
        isReady = true,
        isConnected = true,
        isAi = false,
        joinedAt = 1L,
        lastSeen = 2L
    )

    @Test
    fun `clean roster passes through untouched`() {
        val players = listOf(seat("a", PlayerColor.RED), seat("b", PlayerColor.GREEN))
        val out = sanitizePlayersForStart(players)!!
        assertEquals(listOf("a", "b"), out.map { it.id })
        assertEquals(listOf(PlayerColor.RED, PlayerColor.GREEN), out.map { it.color })
    }

    @Test
    fun `duplicate colors are remapped deterministically`() {
        val players = listOf(seat("a", PlayerColor.RED), seat("b", PlayerColor.RED))
        val out = sanitizePlayersForStart(players)!!
        assertEquals(2, out.size)
        assertEquals(PlayerColor.RED, out[0].color)
        assertEquals(PlayerColor.GREEN, out[1].color)
        assertEquals("b", out[1].id)
        assertEquals(2, out.map { it.color }.toSet().size)
    }

    @Test
    fun `first seat keeps its color, later dupes move`() {
        val players = listOf(
            seat("a", PlayerColor.BLUE),
            seat("b", PlayerColor.BLUE),
            seat("c", PlayerColor.BLUE)
        )
        val out = sanitizePlayersForStart(players)!!
        assertEquals(PlayerColor.BLUE, out[0].color)
        assertEquals(3, out.map { it.color }.toSet().size)
    }

    @Test
    fun `solo roster fails cleanly instead of crashing`() {
        assertNull(sanitizePlayersForStart(listOf(seat("a", PlayerColor.RED))))
        assertNull(sanitizePlayersForStart(emptyList()))
    }

    @Test
    fun `oversized roster fails cleanly instead of crashing`() {
        val players = listOf(
            seat("a", PlayerColor.RED),
            seat("b", PlayerColor.GREEN),
            seat("c", PlayerColor.YELLOW),
            seat("d", PlayerColor.BLUE),
            seat("e", PlayerColor.RED)
        )
        assertNull(sanitizePlayersForStart(players))
    }

    @Test
    fun `duplicate ids collapse to one seat`() {
        val players = listOf(
            seat("a", PlayerColor.RED),
            seat("a", PlayerColor.GREEN),
            seat("b", PlayerColor.YELLOW)
        )
        val out = sanitizePlayersForStart(players)!!
        assertEquals(listOf("a", "b"), out.map { it.id })
        assertTrue(out.map { it.color }.toSet().size == 2)
    }
}
