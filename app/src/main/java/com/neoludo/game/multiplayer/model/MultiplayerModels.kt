package com.neoludo.game.multiplayer.model

import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import kotlinx.serialization.Serializable

@Serializable
enum class RoomStatus {
    CREATING,
    LOBBY,
    STARTING,
    IN_GAME,
    PAUSED,
    RECONNECTING,
    COMPLETED,
    ABANDONED
}

@Serializable
data class RoomMetadata(
    val roomId: String = "",
    val hostId: String = "",
    val hostEpoch: Long = 1L,
    val status: RoomStatus = RoomStatus.LOBBY,
    val maxPlayers: Int = 4,
    val fillBots: Boolean = false,
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val turnStartedAt: Long = 0L,
    val turnDeadline: Long = 0L
)

@Serializable
data class PlayerPresence(
    val id: String = "",
    val name: String = "",
    val avatarId: Int = 1,
    val color: PlayerColor = PlayerColor.RED,
    val isHost: Boolean = false,
    val isReady: Boolean = false,
    val isConnected: Boolean = true,
    val isAi: Boolean = false,
    val joinedAt: Long = 0L,
    val lastSeen: Long = 0L
)

@Serializable
data class RoomSnapshot(
    val meta: RoomMetadata = RoomMetadata(),
    val players: List<PlayerPresence> = emptyList(),
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
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderColor: PlayerColor = PlayerColor.RED,
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
    LEAVE_ROOM,
    TOGGLE_BOT_FILL,
    REQUEST_RECONNECT
}

@Serializable
data class NetworkAction(
    val actionId: String = "",
    val sequence: Long = 0L,
    val type: ActionType = ActionType.ROLL_DICE,
    val playerId: String = "",
    val payload: String = "",
    /** Canonical game revision the sender acted on. -1 keeps old snapshots decodable. */
    val expectedVersion: Long = -1L,
    /** Host generation the sender observed. -1 keeps old protocol messages decodable. */
    val expectedHostEpoch: Long = -1L,
    val timestamp: Long = 0L
)

@Serializable
enum class NetworkEventType {
    PLAYER_JOINED,
    PLAYER_LEFT,
    PLAYER_READY,
    GAME_STARTED,
    DICE_ROLLED,
    PIECE_MOVED,
    PIECE_CAPTURED,
    BONUS_TURN,
    TURN_FORFEITED,
    PLAYER_DISCONNECTED,
    PLAYER_RECONNECTED,
    AI_TAKEOVER,
    PLAYER_WON,
    GAME_OVER
}

@Serializable
data class NetworkEvent(
    val eventId: String = "",
    val type: NetworkEventType = NetworkEventType.GAME_STARTED,
    val playerId: String = "",
    val payload: String = "",
    val version: Long = 0L,
    val timestamp: Long = 0L
)

sealed class RoomError(val userMessage: String) : Exception(userMessage) {
    data object RoomNotFound : RoomError("Room does not exist. Check the room code.")
    /** Relays reachable, but no such room on any of them — says what to check. */
    data class RoomNotFoundDetailed(val details: String) : RoomError("Room not found. $details")
    data object RoomFull : RoomError("This room is already full.")
    data object GameAlreadyStarted : RoomError("Game has already started in this room.")
    data object RoomExpired : RoomError("This room has expired or been abandoned.")
    data object AlreadyJoined : RoomError("You are already in this room.")
    data object NotHost : RoomError("Only the room host can perform this action.")
    data object NotYourTurn : RoomError("It is not your turn.")
    data object IllegalMove : RoomError("Selected move is not valid.")
    data object PlayersNotReady : RoomError("All connected players must be ready before the match starts.")
    data object StaleAction : RoomError("The game changed before that action arrived. Synced to the latest state.")
    data object AuthenticationRequired : RoomError("Network connection / authentication required.")
    data class NetworkFailure(val details: String) : RoomError("Network error: $details")
}
