package com.neoludo.game.multiplayer

import com.neoludo.game.multiplayer.sync.ActionDeduplicator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Client counters are seeded with epoch seconds so a rejoined app never
 * replays from 0. The deduplicator must accept that scale — and still
 * reject absurd values. Pure logic, no network.
 */
class SequenceRestartTest {

    @Test
    fun `epoch-second scale sequences are accepted and ordered per sender`() {
        val deduplicator = ActionDeduplicator()
        val base = System.currentTimeMillis() / 1000L
        assertFalse(deduplicator.isDuplicateOrStale("r1", base, "phone"))
        deduplicator.markProcessed("r1", base, "phone")
        assertFalse(deduplicator.isDuplicateOrStale("r2", base + 1, "phone"))
        deduplicator.markProcessed("r2", base + 1, "phone")
        // Restart replays the same bucket: stale, correctly dropped.
        assertTrue(deduplicator.isDuplicateOrStale("r3", base, "phone"))
        // A fresh restart ahead of the mark is accepted.
        assertFalse(deduplicator.isDuplicateOrStale("r4", base + 2, "phone"))
    }

    @Test
    fun `absurd sequences are still rejected`() {
        val deduplicator = ActionDeduplicator()
        assertTrue(deduplicator.isDuplicateOrStale("evil", Long.MAX_VALUE, "evil"))
        assertTrue(deduplicator.isDuplicateOrStale("neg", -5L, "evil"))
    }

    @Test
    fun `small counters from a fresh install still work`() {
        val deduplicator = ActionDeduplicator()
        assertFalse(deduplicator.isDuplicateOrStale("a1", 1L, "fresh"))
        deduplicator.markProcessed("a1", 1L, "fresh")
        assertFalse(deduplicator.isDuplicateOrStale("a2", 2L, "fresh"))
    }
}
