package com.neoludo.game.multiplayer

import com.neoludo.game.multiplayer.backend.MqttRelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Room codes pasted from chat apps carry formatting/quotes/junk — parsing
 * must be aggressive so a valid code never becomes "room does not exist".
 * Pure logic, no network.
 */
class MqttRelayRoomCodeTest {

    @Test
    fun `plain codes pass through`() {
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("NL-ABC123"))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("abc123"))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("NLABC123"))
    }

    @Test
    fun `whatsapp formatting is stripped`() {
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("*NL-ABC123*"))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("_NL-ABC123_"))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("~NL-ABC123~"))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("```NL-ABC123```"))
    }

    @Test
    fun `quotes spaces and punctuation are stripped`() {
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("\"NL-ABC123\""))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("  NL - ABC 123  "))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("Room code: NL-ABC123!"))
        assertEquals("NL-ABC123", MqttRelay.normalizeRoomCode("‎NL-ABC123‏"))
    }

    @Test
    fun `sanitized codes validate`() {
        assertEquals("NL-ABC123", MqttRelay.sanitizeCode("*NL-ABC123*"))
        assertNull(MqttRelay.sanitizeCode("***"))
        assertNull(MqttRelay.sanitizeCode("NL-ABC12"))
        assertNull(MqttRelay.sanitizeCode("NL-ABC1234"))
    }

    @Test
    fun `relay short names`() {
        assertEquals("HiveMQ", MqttRelay.shortName("ssl://broker.hivemq.com:8883"))
        assertEquals("HiveMQ", MqttRelay.shortName("tcp://broker.hivemq.com:1883"))
        assertEquals("EMQX", MqttRelay.shortName("ssl://broker.emqx.io:8883"))
        assertEquals("?", MqttRelay.shortName(null))
        assertEquals("?", MqttRelay.shortName("tcp://unknown.example:1883"))
    }
}
