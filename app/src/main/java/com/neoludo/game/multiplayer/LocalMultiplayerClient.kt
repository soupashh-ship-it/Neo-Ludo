package com.neoludo.game.multiplayer

import com.neoludo.game.engine.InitialPlayerConfig
import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.ConnectionState
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomSnapshot
import com.neoludo.game.multiplayer.model.RoomStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
class LocalMultiplayerClient(
    playerCount: Int = 4,
    ruleSet: LudoRuleSet = LudoRuleSet(),
    playerNames: List<String> = listOf("Red", "Green", "Yellow", "Blue")
) : MultiplayerClient {
    private val mutex = Mutex()
    private val colors = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).take(playerCount)
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _chatEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    override val chatEvents: SharedFlow<ChatEvent> = _chatEvents.asSharedFlow()

    private val roomId = "local_" + UUID.randomUUID().toString().take(6)

    private val presences = colors.mapIndexed { idx, color ->
        PlayerPresence(
            id = "local_p$idx",
            name = playerNames.getOrElse(idx) { "Player ${idx + 1}" },
            avatarId = idx + 1,
            color = color,
            isHost = idx == 0,
            isReady = true,
            isConnected = true,
            isAi = false
        )
    }

    private val _roomState = MutableStateFlow<RoomSnapshot?>(
        RoomSnapshot(
            meta = RoomMetadata(
                roomId = roomId,
                hostId = presences.first().id,
                status = RoomStatus.IN_GAME,
                maxPlayers = playerCount,
                ruleSet = ruleSet,
                createdAt = System.currentTimeMillis()
            ),
            players = presences
        )
    )
    override val roomState: StateFlow<RoomSnapshot?> = _roomState.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(
        LudoGameEngine.createInitialState(
            gameId = roomId,
            playerConfigs = presences.map {
                InitialPlayerConfig(it.id, it.name, it.color, it.avatarId, isBot = false)
            },
            ruleSet = ruleSet
        )
    )
    override val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    override suspend fun rollDice(): Result<Unit> = mutex.withLock {
        val current = _gameState.value ?: return Result.failure(IllegalStateException("Game not started"))
        if (current.isGameOver) return Result.failure(IllegalStateException("Game is over"))
        if (current.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
            return Result.failure(IllegalStateException("Cannot roll dice in phase ${current.turnPhase}"))
        }
        if (!current.diceState.canRoll) {
            return Result.failure(IllegalStateException("Dice cannot be rolled right now"))
        }
        val next = LudoGameEngine.rollDice(current)
        _gameState.value = next
        return Result.success(Unit)
    }

    override suspend fun movePiece(pieceId: Int): Result<Unit> = mutex.withLock {
        val current = _gameState.value ?: return Result.failure(IllegalStateException("Game not started"))
        if (current.isGameOver) return Result.failure(IllegalStateException("Game is over"))
        if (current.turnPhase != TurnPhase.WAITING_FOR_MOVE) {
            return Result.failure(IllegalStateException("Cannot move piece in phase ${current.turnPhase}"))
        }
        val next = LudoGameEngine.movePiece(current, pieceId)
        _gameState.value = next
        return Result.success(Unit)
    }

    override suspend fun sendChat(message: String): Result<Unit> {
        val current = _gameState.value ?: return Result.failure(IllegalStateException("Game not started"))
        val active = current.activePlayer
        _chatEvents.emit(
            ChatEvent(
                id = UUID.randomUUID().toString(),
                senderId = active.id,
                senderName = active.name,
                senderColor = active.color,
                message = message,
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun sendEmote(emoteId: String): Result<Unit> {
        val current = _gameState.value ?: return Result.failure(IllegalStateException("Game not started"))
        val active = current.activePlayer
        _chatEvents.emit(
            ChatEvent(
                id = UUID.randomUUID().toString(),
                senderId = active.id,
                senderName = active.name,
                senderColor = active.color,
                emoteId = emoteId,
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun startMatch(): Result<Unit> = Result.success(Unit)
    override suspend fun leaveRoom(): Result<Unit> {
        _roomState.value = _roomState.value?.copy(meta = _roomState.value!!.meta.copy(status = RoomStatus.COMPLETED))
        return Result.success(Unit)
    }
}
