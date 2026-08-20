package com.neoludo.game.multiplayer.model

import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import kotlinx.serialization.Serializable

@Serializable
enum class RoomStatus {
    LOBBY,
    IN_GAME,
    COMPLETED,
    ABANDONED
}

@Serializable
data class RoomMetadata(
    val roomId: String,
    val hostId: String,
    val status: RoomStatus = RoomStatus.LOBBY,
    val maxPlayers: Int = 4,
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    val createdAt: Long = 0L,
    val lastHeartbeat: Long = 0L
)

@Serializable
data class PlayerPresence(
    val id: String,
    val name: String,
    val avatarId: Int = 1,
    val color: PlayerColor,
    val isHost: Boolean = false,
    val isReady: Boolean = false,
    val isConnected: Boolean = true,
    val isAi: Boolean = false
)

@Serializable
data class RoomSnapshot(
    val meta: RoomMetadata,
    val players: List<PlayerPresence>,
    val gameState: GameState? = null
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

@Serializable
data class ChatEvent(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderColor: PlayerColor,
    val message: String? = null,
    val emoteId: String? = null,
    val timestamp: Long = 0L
)

@Serializable
enum class ActionType {
    ROLL_DICE,
    MOVE_PIECE,
    PASS_TURN,
    SET_READY,
    START_GAME,
    LEAVE_ROOM
}

@Serializable
data class NetworkAction(
    val actionId: String,
    val sequence: Long,
    val type: ActionType,
    val playerId: String,
    val payload: String = "",
    val timestamp: Long = 0L
)
