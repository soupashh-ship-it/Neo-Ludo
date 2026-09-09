package com.neoludo.game.multiplayer.sync

/**
 * UUID-primary idempotency with per-sender sequence tracking.
 * Replaces the old global lastSequence (which dropped valid cross-client actions
 * because every client started its counter at 0/1).
 */
class ActionDeduplicator(
    private val maxCacheSize: Int = 1000
) {
    private val processedActionIds = java.util.Collections.synchronizedSet(
        object : java.util.LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                if (size >= maxCacheSize) {
                    val iterator = iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                return super.add(element)
            }
        }
    )

    // Per-sender high-water mark — cross-client sequences never collide.
    private val lastSequenceBySender = mutableMapOf<String, Long>()
    // Fallback global mark for legacy sender-less callers (tests / single-writer paths).
    private var lastSequenceGlobal: Long = 0L

    companion object {
        // Firebase numbers are IEEE-754 doubles. Keep sequences within the exact
        // JavaScript integer range while leaving enough space for a restart-safe
        // millisecond seed (epochMillis * 1024).
        const val MAX_SEQUENCE: Long = 9_007_199_254_740_991L

        fun restartSafeSeed(nowMillis: Long = System.currentTimeMillis()): Long =
            (nowMillis.coerceIn(1L, (MAX_SEQUENCE - 1_000_000L) / 1024L) * 1024L)
    }

    @Synchronized
    fun isDuplicateOrStale(actionId: String, sequence: Long = 0L, senderId: String = ""): Boolean {
        if (actionId.isBlank()) return true
        if (processedActionIds.contains(actionId)) return true
        // Sequence 0 = unordered (backward compat) — UUID decides.
        if (sequence == 0L) return false
        // Range-guard: attacker posting Long.MAX_VALUE no longer DoSes the room.
        if (sequence < 0L || sequence > MAX_SEQUENCE) return true
        if (senderId.isNotBlank()) {
            val last = lastSequenceBySender[senderId] ?: 0L
            if (sequence <= last) return true
        } else {
            if (sequence <= lastSequenceGlobal) return true
        }
        return false
    }

    // Backward-compat overload (single-writer tests / legacy callers).
    @Synchronized
    fun isDuplicateOrStale(actionId: String, sequence: Long): Boolean =
        isDuplicateOrStale(actionId, sequence, "")

    @Synchronized
    fun markProcessed(actionId: String, sequence: Long = 0L, senderId: String = "") {
        if (actionId.isNotBlank()) processedActionIds.add(actionId)
        if (sequence in 1L..MAX_SEQUENCE && senderId.isNotBlank()) {
            val last = lastSequenceBySender[senderId] ?: 0L
            if (sequence > last) lastSequenceBySender[senderId] = sequence
        }
        if (sequence in 1L..MAX_SEQUENCE && senderId.isBlank()) {
            if (sequence > lastSequenceGlobal) lastSequenceGlobal = sequence
        }
    }

    @Synchronized
    fun markProcessed(actionId: String, sequence: Long) =
        markProcessed(actionId, sequence, "")

    @Synchronized
    fun reset() {
        processedActionIds.clear()
        lastSequenceBySender.clear()
        lastSequenceGlobal = 0L
    }
}
