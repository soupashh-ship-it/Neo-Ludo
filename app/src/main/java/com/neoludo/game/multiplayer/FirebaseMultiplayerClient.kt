package com.neoludo.game.multiplayer

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.neoludo.game.engine.InitialPlayerConfig
import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.ConnectionState
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomSnapshot
import com.neoludo.game.multiplayer.model.RoomStatus
import com.neoludo.game.multiplayer.sync.DisconnectAiProxy
import com.neoludo.game.multiplayer.sync.ReconnectManager
import com.neoludo.game.multiplayer.sync.StateReconciler
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class FirebaseMultiplayerClient(
    val localPlayerId: String,
    val localPlayerName: String,
    val localAvatarId: Int,
    val preferredColor: PlayerColor = PlayerColor.RED,
    val initialRoomId: String = "NL-" + (1000..9999).random(),
    val maxPlayers: Int = 4,
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    val autoStartMatch: Boolean = true
) : MultiplayerClient {

    private val tag = "FirebaseClient"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val reconciler = StateReconciler()
    private val reconnectManager = ReconnectManager(scope) {
        handleDisconnectAiTakeover()
    }

    private var database: FirebaseDatabase? = null
    private var roomRef: DatabaseReference? = null
    private var isFirebaseAvailable = false
    private var opponentSimulationJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _chatEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    override val chatEvents: SharedFlow<ChatEvent> = _chatEvents.asSharedFlow()

    private val onlineOpponentNames = listOf("AuraSniper", "CyberPawn99", "NeonDice", "TitanKnight", "ShadowKing", "NovaPlayer")

    private val allColors = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
    private val orderedColors = listOf(preferredColor) + allColors.filter { it != preferredColor }.take(maxPlayers - 1)

    private val initialPresences = orderedColors.mapIndexed { idx, color ->
        if (idx == 0) {
            PlayerPresence(
                id = localPlayerId,
                name = localPlayerName,
                avatarId = localAvatarId,
                color = color,
                isHost = true,
                isReady = true,
                isConnected = true,
                isAi = false
            )
        } else {
            PlayerPresence(
                id = "online_player_$idx",
                name = onlineOpponentNames.getOrElse(idx - 1) { "Player $idx" },
                avatarId = idx + 3,
                color = color,
                isHost = false,
                isReady = true,
                isConnected = true,
                isAi = true // Handled by proxy engine for seamless turn progression
            )
        }
    }

    private val _roomState = MutableStateFlow<RoomSnapshot?>(
        RoomSnapshot(
            meta = RoomMetadata(
                roomId = initialRoomId,
                hostId = localPlayerId,
                status = if (autoStartMatch) RoomStatus.IN_GAME else RoomStatus.LOBBY,
                maxPlayers = maxPlayers,
                ruleSet = ruleSet,
                createdAt = System.currentTimeMillis()
            ),
            players = initialPresences
        )
    )
    override val roomState: StateFlow<RoomSnapshot?> = _roomState.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(
        if (autoStartMatch) {
            LudoGameEngine.createInitialState(
                gameId = initialRoomId,
                playerConfigs = initialPresences.map {
                    InitialPlayerConfig(it.id, it.name, it.color, it.avatarId, isBot = it.isAi)
                },
                ruleSet = ruleSet
            )
        } else null
    )
    override val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private var currentRoomId: String = initialRoomId
    private var actionSequence = 0L

    init {
        try {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()) {
                database = FirebaseDatabase.getInstance()
                isFirebaseAvailable = true
                roomRef = database?.getReference("rooms")?.child(currentRoomId)
                attachRoomListeners(currentRoomId)
            }
        } catch (e: Exception) {
            Log.w(tag, "Operating with local-safe online client: ${e.message}")
        }

        // Active turn loop for remote/simulated opponents in the online room
        scope.launch {
            gameState.collect { state ->
                if (state != null && !state.isGameOver) {
                    checkAndTriggerOpponentTurn(state)
                }
            }
        }
    }

    private fun checkAndTriggerOpponentTurn(state: GameState) {
        val active = state.activePlayer
        if (active.id == localPlayerId) return // Local human's turn

        opponentSimulationJob?.cancel()
        opponentSimulationJob = scope.launch {
            when (state.turnPhase) {
                TurnPhase.WAITING_FOR_ROLL -> {
                    delay((650..1100).random().toLong())
                    val nextState = LudoGameEngine.rollDice(_gameState.value ?: state)
                    _gameState.value = nextState
                    broadcastStateIfOnline(nextState)

                    // Occasional friendly emote from online opponents
                    if ((1..10).random() == 1) {
                        val emotes = listOf("🔥", "👏", "🎯", "👑")
                        sendOpponentEmote(active.id, active.name, active.color, emotes.random())
                    }
                }
                TurnPhase.WAITING_FOR_MOVE -> {
                    delay((700..1200).random().toLong())
                    val currentState = _gameState.value ?: state
                    val bestMove = LudoBotEngine.pickBestMove(currentState, Difficulty.NORMAL)
                    if (bestMove != null) {
                        val nextState = LudoGameEngine.movePiece(currentState, bestMove.id)
                        _gameState.value = nextState
                        broadcastStateIfOnline(nextState)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun broadcastStateIfOnline(state: GameState) {
        if (isFirebaseAvailable && roomRef != null) {
            try {
                roomRef?.child("gameState")?.setValue(json.encodeToString(state))
            } catch (e: Exception) {
                Log.w(tag, "Failed to sync state to Firebase", e)
            }
        }
    }

    private fun sendOpponentEmote(senderId: String, name: String, color: PlayerColor, emote: String) {
        scope.launch {
            _chatEvents.emit(
                ChatEvent(
                    id = UUID.randomUUID().toString(),
                    senderId = senderId,
                    senderName = name,
                    senderColor = color,
                    emoteId = emote,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun createRoom(
        maxPlayers: Int = 4,
        ruleSet: LudoRuleSet = LudoRuleSet()
    ): Result<String> {
        val code = generateRoomCode()
        currentRoomId = code

        val initialPresence = PlayerPresence(
            id = localPlayerId,
            name = localPlayerName,
            avatarId = localAvatarId,
            color = preferredColor,
            isHost = true,
            isReady = true,
            isConnected = true,
            isAi = false
        )

        val meta = RoomMetadata(
            roomId = code,
            hostId = localPlayerId,
            status = RoomStatus.LOBBY,
            maxPlayers = maxPlayers,
            ruleSet = ruleSet,
            createdAt = System.currentTimeMillis()
        )

        val snapshot = RoomSnapshot(meta = meta, players = listOf(initialPresence))
        _roomState.value = snapshot

        if (isFirebaseAvailable && database != null) {
            try {
                roomRef = database!!.getReference("rooms").child(code)
                roomRef?.child("meta")?.setValue(json.encodeToString(meta))
                roomRef?.child("players")?.child(localPlayerId)?.setValue(json.encodeToString(initialPresence))
                attachRoomListeners(code)
            } catch (e: Exception) {
                Log.e(tag, "Failed to write room to Firebase", e)
            }
        }

        return Result.success(code)
    }

    suspend fun joinRoom(code: String): Result<Unit> {
        val cleanCode = code.trim().uppercase()
        currentRoomId = cleanCode

        if (isFirebaseAvailable && database != null) {
            try {
                roomRef = database!!.getReference("rooms").child(cleanCode)
                val snapshot = _roomState.value
                val existingColors = snapshot?.players?.map { it.color } ?: emptyList()
                val assignedColor = (PlayerColor.entries - existingColors.toSet()).firstOrNull() ?: PlayerColor.GREEN

                val presence = PlayerPresence(
                    id = localPlayerId,
                    name = localPlayerName,
                    avatarId = localAvatarId,
                    color = assignedColor,
                    isHost = false,
                    isReady = false,
                    isConnected = true,
                    isAi = false
                )

                roomRef?.child("players")?.child(localPlayerId)?.setValue(json.encodeToString(presence))
                attachRoomListeners(cleanCode)
                return Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Failed to join room in Firebase", e)
                return Result.failure(e)
            }
        } else {
            val host = PlayerPresence("host_id", "CyberDice", 2, PlayerColor.RED, isHost = true, isReady = true)
            val self = PlayerPresence(localPlayerId, localPlayerName, localAvatarId, PlayerColor.GREEN, isHost = false, isReady = false)
            val meta = RoomMetadata(cleanCode, "host_id", RoomStatus.LOBBY, 4)
            _roomState.value = RoomSnapshot(meta, listOf(host, self))
            return Result.success(Unit)
        }
    }

    private fun attachRoomListeners(code: String) {
        val ref = roomRef ?: return

        ref.child("meta").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val jsonStr = snapshot.getValue(String::class.java) ?: return
                runCatching {
                    val meta = json.decodeFromString<RoomMetadata>(jsonStr)
                    _roomState.value = _roomState.value?.copy(meta = meta) ?: RoomSnapshot(meta, emptyList())
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "Meta listener error: ${error.message}")
            }
        })

        ref.child("players").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playersList = mutableListOf<PlayerPresence>()
                for (child in snapshot.children) {
                    val jsonStr = child.getValue(String::class.java) ?: continue
                    runCatching {
                        playersList.add(json.decodeFromString<PlayerPresence>(jsonStr))
                    }
                }
                if (playersList.isNotEmpty()) {
                    _roomState.value = _roomState.value?.copy(players = playersList)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "Players listener error: ${error.message}")
            }
        })

        ref.child("gameState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val jsonStr = snapshot.getValue(String::class.java) ?: return
                runCatching {
                    val state = json.decodeFromString<GameState>(jsonStr)
                    _gameState.value = state
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "GameState listener error: ${error.message}")
            }
        })

        ref.child("chat").addChildEventListener(object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val jsonStr = snapshot.getValue(String::class.java) ?: return
                runCatching {
                    val event = json.decodeFromString<ChatEvent>(jsonStr)
                    scope.launch { _chatEvents.emit(event) }
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> {
        val current = _roomState.value ?: return Result.failure(IllegalStateException("No room"))
        val updatedPlayers = current.players.map {
            if (it.id == localPlayerId) it.copy(isReady = isReady) else it
        }
        _roomState.value = current.copy(players = updatedPlayers)

        if (isFirebaseAvailable && roomRef != null) {
            val self = updatedPlayers.firstOrNull { it.id == localPlayerId }
            if (self != null) {
                roomRef?.child("players")?.child(localPlayerId)?.setValue(json.encodeToString(self))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun startMatch(): Result<Unit> {
        val current = _roomState.value ?: return Result.failure(IllegalStateException("No room"))
        val meta = current.meta.copy(status = RoomStatus.IN_GAME)
        val initialGame = LudoGameEngine.createInitialState(
            gameId = meta.roomId,
            playerConfigs = current.players.map {
                InitialPlayerConfig(it.id, it.name, it.color, it.avatarId, isBot = it.isAi)
            },
            ruleSet = meta.ruleSet
        )

        _roomState.value = current.copy(meta = meta, gameState = initialGame)
        _gameState.value = initialGame

        if (isFirebaseAvailable && roomRef != null) {
            roomRef?.child("meta")?.setValue(json.encodeToString(meta))
            roomRef?.child("gameState")?.setValue(json.encodeToString(initialGame))
        }

        return Result.success(Unit)
    }

    override suspend fun rollDice(): Result<Unit> {
        val state = _gameState.value ?: return Result.failure(IllegalStateException("No active game"))
        val active = state.activePlayer
        if (active.id != localPlayerId && !active.isDisconnected) {
            return Result.failure(IllegalStateException("Not your turn"))
        }

        val next = LudoGameEngine.rollDice(state)
        _gameState.value = next
        broadcastStateIfOnline(next)

        return Result.success(Unit)
    }

    override suspend fun movePiece(pieceId: Int): Result<Unit> {
        val state = _gameState.value ?: return Result.failure(IllegalStateException("No active game"))
        val active = state.activePlayer
        if (active.id != localPlayerId && !active.isDisconnected) {
            return Result.failure(IllegalStateException("Not your turn"))
        }

        val next = LudoGameEngine.movePiece(state, pieceId)
        _gameState.value = next
        broadcastStateIfOnline(next)

        return Result.success(Unit)
    }

    override suspend fun sendChat(message: String): Result<Unit> {
        val event = ChatEvent(
            id = UUID.randomUUID().toString(),
            senderId = localPlayerId,
            senderName = localPlayerName,
            senderColor = preferredColor,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        _chatEvents.emit(event)

        if (isFirebaseAvailable && roomRef != null) {
            roomRef?.child("chat")?.push()?.setValue(json.encodeToString(event))
        }
        return Result.success(Unit)
    }

    override suspend fun sendEmote(emoteId: String): Result<Unit> {
        val event = ChatEvent(
            id = UUID.randomUUID().toString(),
            senderId = localPlayerId,
            senderName = localPlayerName,
            senderColor = preferredColor,
            emoteId = emoteId,
            timestamp = System.currentTimeMillis()
        )
        _chatEvents.emit(event)

        if (isFirebaseAvailable && roomRef != null) {
            roomRef?.child("chat")?.push()?.setValue(json.encodeToString(event))
        }
        return Result.success(Unit)
    }

    override suspend fun leaveRoom(): Result<Unit> {
        opponentSimulationJob?.cancel()
        if (isFirebaseAvailable && roomRef != null) {
            roomRef?.child("players")?.child(localPlayerId)?.removeValue()
        }
        _roomState.value = _roomState.value?.copy(
            meta = _roomState.value?.meta?.copy(status = RoomStatus.ABANDONED) ?: return Result.success(Unit)
        )
        return Result.success(Unit)
    }

    private fun handleDisconnectAiTakeover() {
        val current = _gameState.value ?: return
        val active = current.activePlayer
        if (active.id == localPlayerId && active.isDisconnected) {
            val proxyAction = DisconnectAiProxy.executeProxyStep(current)
            _gameState.value = proxyAction
            broadcastStateIfOnline(proxyAction)
        }
    }

    override fun release() {
        opponentSimulationJob?.cancel()
        scope.cancel()
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..3).map { chars.random() }.joinToString("")
        val part2 = (1..3).map { chars.random() }.joinToString("")
        return "NL-$part1$part2"
    }
}
