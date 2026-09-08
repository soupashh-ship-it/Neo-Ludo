package com.neoludo.game.multiplayer

import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.engine.model.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Joining with an id that another LIVE device is heartbeating must mint a
 * fresh id (cloned installs) instead of stealing the seat; genuinely stale
 * seats keep the id so true rejoins work. Pure logic, no network.
 */
class JoinUidResolverTest {

    private fun seat(
        uid: String,
        connected: Boolean,
        lastSeenAgoMs: Long,
        now: Long
    ) = PlayerPresence(
        id = uid,
        name = "Host",
        avatarId = 1,
        color = PlayerColor.RED,
        isHost = true,
        isReady = true,
        isConnected = connected,
        isAi = false,
        joinedAt = now - 60_000L,
        lastSeen = now - lastSeenAgoMs
    )

    @Test
    fun `unknown uid keeps its id (new seat)`() {
        val now = 1_000_000L
        assertEquals("user_abc", JoinUidResolver.resolve("user_abc", emptyMap(), now))
    }

    @Test
    fun `offline seat keeps its id (true rejoin)`() {
        val now = 1_000_000L
        val known = mapOf("user_abc" to seat("user_abc", connected = false, lastSeenAgoMs = 5_000L, now))
        assertEquals("user_abc", JoinUidResolver.resolve("user_abc", known, now))
    }

    @Test
    fun `stale live-flagged seat keeps its id (true rejoin after will delay)`() {
        val now = 1_000_000L
        val known = mapOf("user_abc" to seat("user_abc", connected = true, lastSeenAgoMs = 120_000L, now))
        assertEquals("user_abc", JoinUidResolver.resolve("user_abc", known, now))
    }

    @Test
    fun `heartbeating seat mints a fresh unused id (cloned install)`() {
        val now = 1_000_000L
        val known = mapOf("user_abc" to seat("user_abc", connected = true, lastSeenAgoMs = 3_000L, now))
        val resolved = JoinUidResolver.resolve("user_abc", known, now) { "user_fresh" }
        assertEquals("user_fresh", resolved)
        assertNotEquals("user_abc", resolved)
    }

    @Test
    fun `minted id never collides with a known seat`() {
        val now = 1_000_000L
        val known = mapOf(
            "user_abc" to seat("user_abc", connected = true, lastSeenAgoMs = 1_000L, now),
            "user_taken" to seat("user_taken", connected = true, lastSeenAgoMs = 1_000L, now)
        )
        var calls = 0
        val resolved = JoinUidResolver.resolve("user_abc", known, now) {
            calls++
            if (calls == 1) "user_taken" else "user_free"
        }
        assertEquals("user_free", resolved)
        assertTrue(!known.containsKey(resolved))
    }
}
