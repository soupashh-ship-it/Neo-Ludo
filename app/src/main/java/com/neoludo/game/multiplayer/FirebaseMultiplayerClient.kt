package com.neoludo.game.multiplayer

import android.util.Log
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.backend.FirebaseAuthDataSource
import com.neoludo.game.multiplayer.backend.FirebaseRoomDataSource
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.ConnectionState
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.NetworkEvent
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomError
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomSnapshot
import com.neoludo.game.multiplayer.model.RoomStatus
import com.neoludo.game.multiplayer.repository.ActionRepository
import com.neoludo.game.multiplayer.repository.ChatRepository
import com.neoludo.game.multiplayer.repository.GameRepository
import com.neoludo.game.multiplayer.repository.PresenceRepository
import com.neoludo.game.multiplayer.repository.RoomRepository
import com.neoludo.game.multiplayer.sync.AuthoritativeGameProcessor
import com.neoludo.game.multiplayer.sync.HostElectionManager
import com.neoludo.game.multiplayer.sync.ReconnectManager
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class FirebaseMultiplayerClient(
    val localPlayerId: String,
    val localPlayerName: String,
    val localAvatarId: Int,
    override val preferredColor: PlayerColor = PlayerColor.RED,
    val initialRoomId: String = generateRoomCode(),
    override val maxPlayers: Int = 4,
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    private val authDataSource: FirebaseAuthDataSource = FirebaseAuthDataSource(),
    private val roomDataSource: FirebaseRoomDataSource = FirebaseRoomDataSource()
) : OnlineRoomClient {

    private val tag = "FirebaseMultiplayerClient"
    private fun log(msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("$tag: $msg")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Seeded with epoch seconds (not 0): a rejoined app must not replay
    // from 0 and get its actions dropped as stale by the host deduplicator.
    private val sequenceCounter = AtomicLong(System.currentTimeMillis() / 1000L)

    private val roomRepo = RoomRepository(roomDataSource)
    private val actionRepo = ActionRepository(roomDataSource)
    private val presenceRepo = PresenceRepository(roomDataSource, authDataSource)
    private val chatRepo = ChatRepository(roomDataSource)
    private val gameRepo = GameRepository(roomDataSource)

    private val hostElectionManager = HostElectionManager()
    private val authoritativeProcessor = AuthoritativeGameProcessor()
    private val reconnectManager = ReconnectManager(scope)

    override var currentUid: String = localPlayerId
        private set
    override var currentAssignedColor: PlayerColor = preferredColor
        private set
    override var currentRoomId: String = initialRoomId
        private set

    private val _roomState = MutableStateFlow<RoomSnapshot?>(null)
    override val roomState: StateFlow<RoomSnapshot?> = _roomState.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    override val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _chatEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    override val chatEvents: SharedFlow<ChatEvent> = _chatEvents.asSharedFlow()

    private var activeRoomObserverJob: Job? = null
    private var activeActionsObserverJob: Job? = null
    private var activeChatObserverJob: Job? = null
    private var activeEventsObserverJob: Job? = null
    private var botTurnJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        presenceRepo.observeConnected()
            .distinctUntilChanged()
            .onEach { isConnected ->
                if (isConnected) {
                    reconnectManager.onConnected()
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    reconnectManager.onDisconnected()
                    _connectionState.value = ConnectionState.RECONNECTING
                }
            }
            .launchIn(scope)
    }

    override suspend fun createRoom(
        playerCount: Int,
        fillBots: Boolean,
        rules: LudoRuleSet
    ): Result<String> = withContext(Dispatchers.IO) {
        val authResult = authDataSource.ensureAuthenticated(localPlayerId)
        val uid = authResult.getOrDefault(localPlayerId)
        currentUid = uid

        val code = generateRoomCode()
        currentRoomId = code

        val meta = RoomMetadata(
            roomId = code,
            hostId = uid,
            hostEpoch = 1L,
            status = RoomStatus.LOBBY,
            maxPlayers = playerCount.coerceIn(2, 4),
            fillBots = fillBots,
            ruleSet = rules,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val hostPresence = PlayerPresence(
            id = uid,
            name = localPlayerName,
            avatarId = localAvatarId,
            color = preferredColor,
            isHost = true,
            isReady = true,
            isConnected = true,
            isAi = false,
            joinedAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )

        val result = roomRepo.createRoom(meta, hostPresence)
        if (result.isSuccess) {
            currentAssignedColor = preferredColor
            attachRealtimeListeners(code)
            Result.success(code)
        } else {
            Result.failure(result.exceptionOrNull() ?: RoomError.NetworkFailure("Failed to create room"))
        }
    }

    override suspend fun joinRoom(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanCode = normalizeRoomCode(roomId)
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(RoomError.RoomNotFound)
        }

        val authResult = authDataSource.ensureAuthenticated(localPlayerId)
        val uid = authResult.getOrDefault(localPlayerId)
        currentUid = uid
        currentRoomId = cleanCode

        val playerPresence = PlayerPresence(
            id = uid,
            name = localPlayerName,
            avatarId = localAvatarId,
            color = preferredColor,
            isHost = false,
            isReady = false,
            isConnected = true,
            isAi = false,
            joinedAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )

        val joinResult = roomRepo.joinRoom(cleanCode, playerPresence)
        joinResult.fold(
            onSuccess = { assigned ->
                currentAssignedColor = assigned.color
                attachRealtimeListeners(cleanCode)
                Result.success(Unit)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    private fun attachRealtimeListeners(code: String) {
        activeRoomObserverJob?.cancel()
        activeActionsObserverJob?.cancel()
        activeChatObserverJob?.cancel()
        activeEventsObserverJob?.cancel()

        activeRoomObserverJob = roomRepo.observeRoom(code)
            .onEach { snapshot ->
                _roomState.value = snapshot
                if (snapshot != null) {
                    val remoteGame = snapshot.gameState
                    val localGame = _gameState.value
                    if (remoteGame != null) {
                        // Strictly-greater accept: equal versions are already applied —
                        // accepting >= caused same-version forks to flap indefinitely.
                        if (localGame == null || remoteGame.version > localGame.version) {
                            _gameState.value = remoteGame
                        }
                    } else if (snapshot.meta.status == RoomStatus.LOBBY) {
                        _gameState.value = null
                    }

                    // Check if local player is authoritative host
                    val isHost = hostElectionManager.isLocalPlayerHost(currentUid, snapshot.meta, snapshot.players)
                    if (isHost && snapshot.meta.status == RoomStatus.IN_GAME && remoteGame != null) {
                        triggerHostEvaluation(snapshot.meta, remoteGame)
                    }
                }
            }
            .launchIn(scope)

        activeActionsObserverJob = actionRepo.observeActions(code)
            .onEach { action ->
                val snapshot = _roomState.value ?: return@onEach
                val currentGameState = _gameState.value ?: snapshot.gameState ?: return@onEach
                val isHost = hostElectionManager.isLocalPlayerHost(currentUid, snapshot.meta, snapshot.players)

                if (isHost) {
                    val processResult = authoritativeProcessor.processAction(action, currentGameState, snapshot.meta)
                    if (processResult != null) {
                        _gameState.value = processResult.updatedState
                        gameRepo.publishGameState(code, processResult.updatedState, processResult.updatedMeta)
                        processResult.events.forEach { event ->
                            gameRepo.publishEvent(code, event)
                        }
                    }
                }
            }
            .launchIn(scope)

        activeChatObserverJob = chatRepo.observeChat(code)
            .onEach { chat ->
                // tryEmit: SharedFlow buffer (64, no replay) would suspend sendChat on
                // Dispatchers.IO indefinitely when no collector is attached.
                _chatEvents.tryEmit(chat)
            }
            .launchIn(scope)
    }

    private fun triggerHostEvaluation(meta: RoomMetadata, state: GameState) {
        if (state.isGameOver) return
        val active = state.activePlayer

        botTurnJob?.cancel()
        timeoutJob?.cancel()

        // 1. If active player is Bot, schedule bot turn
        if (active.isBot) {
            botTurnJob = scope.launch {
                delay(600)
                if (!isActive) return@launch
                when (state.turnPhase) {
                    TurnPhase.WAITING_FOR_ROLL -> {
                        rollDice()
                    }
                    TurnPhase.WAITING_FOR_MOVE -> {
                        val best = LudoBotEngine.pickBestMove(state, Difficulty.NORMAL)
                        if (best != null) {
                            movePiece(best.id)
                        } else {
                            // Pass turn if no moves — carry expected version for
                            // host-migration replay fencing (v=<version>).
                            val action = NetworkAction(
                                actionId = UUID.randomUUID().toString(),
                                sequence = sequenceCounter.incrementAndGet(),
                                type = ActionType.PASS_TURN,
                                playerId = active.id,
                                payload = "v=${state.version}",
                                timestamp = System.currentTimeMillis()
                            )
                            actionRepo.postAction(meta.roomId, action)
                        }
                    }
                    else -> Unit
                }
            }
            return
        }

        // 2. Monitor turn timeout
        val now = System.currentTimeMillis()
        val deadline = meta.turnDeadline.takeIf { it > 0 } ?: (now + meta.ruleSet.turnTimerSeconds * 1000L)
        val remainingMs = (deadline - now).coerceAtLeast(0L)

        timeoutJob = scope.launch {
            delay(remainingMs + 500L) // Grace buffer
            if (!isActive) return@launch
            val latestState = _gameState.value ?: return@launch
            val latestSnapshot = _roomState.value ?: return@launch
            if (latestState.activePlayerIndex == state.activePlayerIndex && latestState.version == state.version && !latestState.isGameOver) {
                log("Turn deadline elapsed for player ${active.name}, executing timeout step")
                val timeoutResult = authoritativeProcessor.processTimeout(latestState, latestSnapshot.meta)
                if (timeoutResult != null) {
                    _gameState.value = timeoutResult.updatedState
                    gameRepo.publishGameState(meta.roomId, timeoutResult.updatedState, timeoutResult.updatedMeta)
                    timeoutResult.events.forEach { event ->
                        gameRepo.publishEvent(meta.roomId, event)
                    }
                }
            }
        }
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val snapshot = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        presenceRepo.setReady(snapshot.meta.roomId, currentUid, isReady)
    }

    override suspend fun setFillBots(fillBots: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val snapshot = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        roomRepo.setFillBots(snapshot.meta.roomId, fillBots)
    }

    override suspend fun startMatch(): Result<Unit> = withContext(Dispatchers.IO) {
        val snapshot = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val isHost = hostElectionManager.isLocalPlayerHost(currentUid, snapshot.meta, snapshot.players)
        if (!isHost) {
            return@withContext Result.failure(RoomError.NotHost)
        }

        val players = sanitizePlayersForStart(snapshot.players)
            ?: return@withContext Result.failure(
                RoomError.NetworkFailure("Need 2 to 4 players with different colors to start. Wait for everyone to join and retry.")
            )
        val initResult = runCatching { authoritativeProcessor.initializeGame(snapshot.meta, players) }.getOrElse {
            return@withContext Result.failure(
                RoomError.NetworkFailure("Need 2 to 4 players with different colors to start. Wait for everyone to join and retry.")
            )
        }
        _gameState.value = initResult.updatedState
        val pubResult = gameRepo.publishGameState(
            snapshot.meta.roomId,
            initResult.updatedState,
            initResult.updatedMeta
        )

        if (pubResult.isSuccess) {
            initResult.events.forEach { ev ->
                gameRepo.publishEvent(snapshot.meta.roomId, ev)
            }
            Result.success(Unit)
        } else {
            Result.failure(pubResult.exceptionOrNull() ?: RoomError.NetworkFailure("Failed to publish initial game state"))
        }
    }

    override suspend fun rollDice(): Result<Unit> = withContext(Dispatchers.IO) {
        val snapshot = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val state = _gameState.value ?: return@withContext Result.failure(
            RoomError.NetworkFailure("Still loading the game — wait a moment and retry.")
        )
        val active = state.activePlayer

        val isHost = hostElectionManager.isLocalPlayerHost(currentUid, snapshot.meta, snapshot.players)
        if (active.id != currentUid && !canHostCoverTurn(isHost, snapshot.players.find { it.id == active.id })) {
            return@withContext Result.failure(RoomError.NotYourTurn)
        }

        val action = NetworkAction(
            actionId = UUID.randomUUID().toString(),
            sequence = sequenceCounter.incrementAndGet(),
            type = ActionType.ROLL_DICE,
            playerId = active.id,
            payload = "",
            timestamp = System.currentTimeMillis()
        )

        actionRepo.postAction(snapshot.meta.roomId, action)
    }

    override suspend fun movePiece(pieceId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val snapshot = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val state = _gameState.value ?: return@withContext Result.failure(
            RoomError.NetworkFailure("Still loading the game — wait a moment and retry.")
        )
        val active = state.activePlayer

        val isHost = hostElectionManager.isLocalPlayerHost(currentUid, snapshot.meta, snapshot.players)
        if (active.id != currentUid && !canHostCoverTurn(isHost, snapshot.players.find { it.id == active.id })) {
            return@withContext Result.failure(RoomError.NotYourTurn)
        }

        val action = NetworkAction(
            actionId = UUID.randomUUID().toString(),
            sequence = sequenceCounter.incrementAndGet(),
            type = ActionType.MOVE_PIECE,
            playerId = active.id,
            payload = pieceId.toString(),
            timestamp = System.currentTimeMillis()
        )

        actionRepo.postAction(snapshot.meta.roomId, action)
    }

    override suspend fun sendChat(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = message.trim()
        if (clean.isBlank()) return@withContext Result.failure(RoomError.NetworkFailure("Message is empty"))
        if (clean.length > 280) return@withContext Result.failure(RoomError.NetworkFailure("Message too long (max 280)"))
        val snapshot = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val chat = ChatEvent(
            id = UUID.randomUUID().toString(),
            senderId = currentUid,
            senderName = localPlayerName,
            senderColor = currentAssignedColor,
            message = clean,
            timestamp = System.currentTimeMillis()
        )
        chatRepo.sendChat(snapshot.meta.roomId, chat)
    }

    override suspend fun sendEmote(emoteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = emoteId.trim().take(64)
        if (clean.isBlank()) return@withContext Result.failure(RoomError.NetworkFailure("Emote is empty"))
        val snapshot = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val chat = ChatEvent(
            id = UUID.randomUUID().toString(),
            senderId = currentUid,
            senderName = localPlayerName,
            senderColor = currentAssignedColor,
            emoteId = clean,
            timestamp = System.currentTimeMillis()
        )
        chatRepo.sendChat(snapshot.meta.roomId, chat)
    }

    override suspend fun leaveRoom(): Result<Unit> = withContext(Dispatchers.IO) {
        val snapshot = _roomState.value
        if (snapshot != null) {
            roomRepo.leaveRoom(snapshot.meta.roomId, currentUid)
        }
        release()
        Result.success(Unit)
    }

    override val transportDebug: String = "Firebase"

    override suspend fun refreshConnection(): Result<Unit> {
        // Firebase RTDB owns its socket and auto-reconnects; re-assert state.
        reconnectManager.onConnected()
        return Result.success(Unit)
    }

    override fun release() {
        botTurnJob?.cancel()
        timeoutJob?.cancel()
        activeRoomObserverJob?.cancel()
        activeActionsObserverJob?.cancel()
        activeChatObserverJob?.cancel()
        activeEventsObserverJob?.cancel()
        scope.cancel()
    }

    companion object {
        private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateRoomCode(): String {
            val randomPart = (1..6)
                .map { CODE_CHARS.random() }
                .joinToString("")
            return "NL-$randomPart"
        }

        // Single hardened implementation (chat-app paste junk, full shared
        // messages) lives on the relay; Firebase rooms share the code format.
        fun normalizeRoomCode(raw: String): String =
            com.neoludo.game.multiplayer.backend.MqttRelay.normalizeRoomCode(raw)
    }
}
