package com.neoludo.game.multiplayer

import com.google.common.truth.Truth.assertThat
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.backend.MqttRelay
import com.neoludo.game.multiplayer.backend.MqttRelayDataSource
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Offline-safe protocol tests for the free public relay.
 * No broker connection needed — topics, codes and JSON codecs only.
 */
class MqttRelayCodecTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testTopicLayout() {
        val code = "NL-ABC123"
        assertThat(MqttRelay.metaTopic(code)).isEqualTo("neoludo/v1/rooms/NL-ABC123/meta")
        assertThat(MqttRelay.presenceTopic(code, "user_1")).isEqualTo("neoludo/v1/rooms/NL-ABC123/players/user_1")
        assertThat(MqttRelay.stateTopic(code)).isEqualTo("neoludo/v1/rooms/NL-ABC123/state")
        assertThat(MqttRelay.actionsTopic(code)).isEqualTo("neoludo/v1/rooms/NL-ABC123/actions")
        assertThat(MqttRelay.chatTopic(code)).isEqualTo("neoludo/v1/rooms/NL-ABC123/chat")
        assertThat(MqttRelay.eventsTopic(code)).isEqualTo("neoludo/v1/rooms/NL-ABC123/events")
    }

    @Test
    fun testCodeNormalizationMatchesFirebaseFormat() {
        assertThat(MqttRelay.sanitizeCode("nl-x7k9qp")).isEqualTo("NL-X7K9QP")
        assertThat(MqttRelay.sanitizeCode("X7K9QP")).isEqualTo("NL-X7K9QP")
        assertThat(MqttRelay.sanitizeCode("  nl - x7k 9qp  ")).isEqualTo("NL-X7K9QP")
        assertThat(MqttRelay.sanitizeCode("")).isNull()
        assertThat(MqttRelay.sanitizeCode("NL-12")).isNull()
        assertThat(MqttRelay.sanitizeCode("NL-ABC+12")).isNull()
        assertThat(MqttRelay.sanitizeCode("NL-ABC#12")).isNull()
        // Generated codes always validate.
        repeat(25) {
            assertThat(MqttRelay.sanitizeCode(MqttRelay.generateRoomCode())).isNotNull()
        }
    }

    @Test
    fun testUidExtractionRejectsTraversal() {
        val code = "NL-ABC123"
        assertThat(MqttRelay.uidFromPresenceTopic("neoludo/v1/rooms/NL-ABC123/players/user_9", code))
            .isEqualTo("user_9")
        assertThat(MqttRelay.uidFromPresenceTopic("neoludo/v1/rooms/NL-ABC123/players/a/b", code)).isNull()
        assertThat(MqttRelay.uidFromPresenceTopic("neoludo/v1/rooms/NL-ABC123/meta", code)).isNull()
        assertThat(MqttRelay.uidFromPresenceTopic("neoludo/v1/rooms/OTHER/players/user_9", code)).isNull()
    }

    @Test
    fun testActionRoundTrip() {
        val action = NetworkAction(
            actionId = "act_1",
            sequence = 7L,
            type = ActionType.MOVE_PIECE,
            playerId = "user_9",
            payload = "2",
            timestamp = 123456789L
        )
        val decoded = json.decodeFromString<NetworkAction>(json.encodeToString(action))
        assertThat(decoded).isEqualTo(action)
    }

    @Test
    fun testMetaAndPresenceRoundTrip() {
        val meta = RoomMetadata(roomId = "NL-ABC123", hostId = "user_1", status = RoomStatus.LOBBY, maxPlayers = 4)
        assertThat(json.decodeFromString<RoomMetadata>(json.encodeToString(meta))).isEqualTo(meta)

        val presence = PlayerPresence(id = "user_1", name = "Ann", color = PlayerColor.BLUE, isReady = true)
        assertThat(json.decodeFromString<PlayerPresence>(json.encodeToString(presence))).isEqualTo(presence)
    }

    @Test
    fun testClientIdUniqueAndBrokerSafe() {
        val a = MqttRelayDataSource.newClientId("user_1")
        val b = MqttRelayDataSource.newClientId("user_1")
        assertThat(a).isNotEqualTo(b)
        assertThat(a).startsWith("neoludo-user1-")
        assertThat(a).doesNotContain(" ")
        assertThat(a).doesNotContain("+")
        assertThat(a).doesNotContain("#")
    }
}
