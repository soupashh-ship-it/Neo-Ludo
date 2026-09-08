package com.neoludo.game.multiplayer

import com.neoludo.game.multiplayer.backend.MqttRelay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The relay spans two independent broker networks (HiveMQ + EMQX): a room
 * published on one is invisible on the other. Peers must converge on one
 * server per session — these tests pin down that pinning logic.
 * Pure logic, no network.
 */
class MqttRelayServerPinningTest {

    @After
    fun clearPin() {
        MqttRelay.clearPin()
    }

    @Test
    fun `unpinned order is the default server list`() {
        assertNull(MqttRelay.pinnedServer)
        assertEquals(MqttRelay.SERVERS, MqttRelay.orderedServers())
    }

    @Test
    fun `pinned server comes first with no duplicates`() {
        MqttRelay.pinServer("ssl://broker.emqx.io:8883")
        val ordered = MqttRelay.orderedServers()
        assertEquals("ssl://broker.emqx.io:8883", ordered.first())
        assertEquals(MqttRelay.SERVERS.size, ordered.size)
        assertEquals(MqttRelay.SERVERS.toSet(), ordered.toSet())
    }

    @Test
    fun `explicit preference beats the pin`() {
        MqttRelay.pinServer("ssl://broker.emqx.io:8883")
        val ordered = MqttRelay.orderedServers(prefer = "tcp://broker.hivemq.com:1883")
        assertEquals("tcp://broker.hivemq.com:1883", ordered.first())
        assertEquals(MqttRelay.SERVERS.size, ordered.size)
    }

    @Test
    fun `unknown servers are never pinned or preferred`() {
        MqttRelay.pinServer("ssl://evil.example.com:8883")
        assertNull(MqttRelay.pinnedServer)
        assertEquals(MqttRelay.SERVERS, MqttRelay.orderedServers(prefer = "ssl://evil.example.com:8883"))
    }

    @Test
    fun `clearPin restores default order`() {
        MqttRelay.pinServer("tcp://broker.emqx.io:1883")
        MqttRelay.clearPin()
        assertNull(MqttRelay.pinnedServer)
        assertEquals(MqttRelay.SERVERS, MqttRelay.orderedServers())
    }
}
