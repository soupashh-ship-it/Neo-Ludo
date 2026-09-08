package com.neoludo.game.multiplayer

import com.neoludo.game.multiplayer.model.PlayerPresence
import java.util.UUID

/**
 * Decides which player id a joiner should use.
 *
 * Two phones can share one id when an install was cloned (same Google
 * account auto-backup, manual data transfer). Without a guard, the guest's
 * join overwrites the host's seat — the host then looks "disconnected" and
 * loses host controls.
 *
 * Rule: our own stale seat (offline, or heartbeat older than
 * [ALIVE_WINDOW_MS]) is a true rejoin and keeps the id. A seat that is still
 * heartbeating can only belong to another LIVE device, so the joiner mints a
 * fresh id and takes a new seat instead of stealing it.
 */
internal object JoinUidResolver {
    const val ALIVE_WINDOW_MS = 20_000L

    fun resolve(
        currentUid: String,
        known: Map<String, PlayerPresence>,
        now: Long = System.currentTimeMillis(),
        mint: () -> String = { "user_" + UUID.randomUUID().toString().take(8) }
    ): String {
        val existing = known[currentUid] ?: return currentUid
        val alive = existing.isConnected && (now - existing.lastSeen) < ALIVE_WINDOW_MS
        if (!alive) return currentUid
        var fresh = mint()
        var guard = 0
        while (known.containsKey(fresh) && guard++ < 8) fresh = mint()
        return fresh
    }
}
