package com.neoludo.game.multiplayer

import com.neoludo.game.engine.InitialPlayerConfig
import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class BotMultiplayerClient(
    humanName: String = "Player 1",
    humanAvatarId: Int = 1,
    humanColor: PlayerColor = PlayerColor.RED,
    botCount: Int = 3,
    val difficulty: Difficulty = Difficulty.NORMAL,
    ruleSet: LudoRuleSet = LudoRuleSet()
) : MultiplayerClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var botExecutionJob: Job? = null
    private val allColors = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
    private val orderedColors = listOf(humanColor) + allColors.filter { it != humanColor }.take(botCount)

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _chatEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    override val chatEvents: SharedFlow<ChatEvent> = _chatEvents.asSharedFlow()

    private val roomId = "ai_" + UUID.randomUUID().toString().take(6)

    private val botNames = listOf("Nexus AI", "Aura Bot", "Cyber Pawn", "Titan Bot")

    private val presences = orderedColors.mapIndexed { idx, color ->
        if (idx == 0) {
            PlayerPresence(
                id = "human_p0",
                name = humanName,
                avatarId = humanAvatarId,
                color = color,
                isHost = true,
                isReady = true,
                isConnected = true,
                isAi = false
            )
        } else {
            PlayerPresence(
                id = "bot_p$idx",
                name = botNames.getOrElse(idx - 1) { "Bot $idx" },
                avatarId = idx + 2,
                color = color,
                isHost = false,
                isReady = true,
                isConnected = true,
                isAi = true
            )
        }
    }

    private val _roomState = MutableStateFlow<RoomSnapshot?>(
        RoomSnapshot(
            meta = RoomMetadata(
                roomId = roomId,
                hostId = presences.first().id,
                status = RoomStatus.IN_GAME,
                maxPlayers = orderedColors.size,
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
                InitialPlayerConfig(it.id, it.name, it.color, it.avatarId, isBot = it.isAi)
            },
            ruleSet = ruleSet
        )
    )
    override val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    init {
        scope.launch {
            gameState.collect { state ->
                if (state != null && !state.isGameOver) {
                    triggerBotTurnIfNeeded(state)
                }
            }
        }
    }

    private fun triggerBotTurnIfNeeded(state: GameState? = _gameState.value) {
        val currentState = state ?: _gameState.value ?: return
        if (currentState.isGameOver || !currentState.activePlayer.isBot) {
            botExecutionJob?.cancel()
            botExecutionJob = null
            return
        }

        if (botExecutionJob?.isActive == true) {
            return // Already executing bot turn loop sequentially
        }

        botExecutionJob = scope.launch {
            executeBotTurnLoop()
        }
    }

    private suspend fun executeBotTurnLoop() {
        while (true) {
            val state = _gameState.value ?: break
            if (state.isGameOver) break
            val active = state.activePlayer
            if (!active.isBot) break // Human player turn: pause bot loop

            when (state.turnPhase) {
                TurnPhase.WAITING_FOR_ROLL -> {
                    delay((550..850).random().toLong())
                    mutex.withLock {
                        val current = _gameState.value ?: return@withLock
                        if (current.activePlayer.isBot && current.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
                            val next = LudoGameEngine.rollDice(current)
                            _gameState.value = next
                        }
                    }
                }
                TurnPhase.WAITING_FOR_MOVE -> {
                    delay((600..900).random().toLong())
                    mutex.withLock {
                        val current = _gameState.value ?: return@withLock
                        if (current.activePlayer.isBot && current.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
                            val bestMove = LudoBotEngine.pickBestMove(current, difficulty)
                            if (bestMove != null) {
                                val next = LudoGameEngine.movePiece(current, bestMove.id)
                                _gameState.value = next
                            } else {
                                val next = LudoGameEngine.passTurn(current)
                                _gameState.value = next
                            }
                        }
                    }
                }
                else -> {
                    delay(100)
                }
            }
        }
    }

    override suspend fun rollDice(): Result<Unit> = mutex.withLock {
        val current = _gameState.value ?: return Result.failure(IllegalStateException("Game not started"))
        if (current.isGameOver) return Result.failure(IllegalStateException("Game is over"))
        if (current.activePlayer.isBot) {
            return Result.failure(IllegalStateException("Cannot roll during bot turn"))
        }
        if (current.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
            return Result.failure(IllegalStateException("Cannot roll in phase ${current.turnPhase}"))
        }
        if (!current.diceState.canRoll) {
            return Result.failure(IllegalStateException("Dice cannot be rolled right now"))
        }
        val next = LudoGameEngine.rollDice(current)
        _gameState.value = next
        triggerBotTurnIfNeeded(next)
        return Result.success(Unit)
    }

    override suspend fun movePiece(pieceId: Int): Result<Unit> = mutex.withLock {
        val current = _gameState.value ?: return Result.failure(IllegalStateException("Game not started"))
        if (current.isGameOver) return Result.failure(IllegalStateException("Game is over"))
        if (current.activePlayer.isBot) {
            return Result.failure(IllegalStateException("Cannot move for bot"))
        }
        if (current.turnPhase != TurnPhase.WAITING_FOR_MOVE) {
            return Result.failure(IllegalStateException("Cannot move in phase ${current.turnPhase}"))
        }
        val next = LudoGameEngine.movePiece(current, pieceId)
        _gameState.value = next
        triggerBotTurnIfNeeded(next)
        return Result.success(Unit)
    }
    override suspend fun sendChat(message: String): Result<Unit> {
        val human = presences.first()
        _chatEvents.emit(
            ChatEvent(
                id = UUID.randomUUID().toString(),
                senderId = human.id,
                senderName = human.name,
                senderColor = human.color,
                message = message,
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun sendEmote(emoteId: String): Result<Unit> {
        val human = presences.first()
        _chatEvents.emit(
            ChatEvent(
                id = UUID.randomUUID().toString(),
                senderId = human.id,
                senderName = human.name,
                senderColor = human.color,
                emoteId = emoteId,
                timestamp = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun startMatch(): Result<Unit> = Result.success(Unit)
    override suspend fun leaveRoom(): Result<Unit> {
        botExecutionJob?.cancel()
        botExecutionJob = null
        _roomState.value = _roomState.value?.copy(meta = _roomState.value!!.meta.copy(status = RoomStatus.COMPLETED))
        return Result.success(Unit)
    }

    override fun release() {
        botExecutionJob?.cancel()
        botExecutionJob = null
        scope.cancel()
    }
}
