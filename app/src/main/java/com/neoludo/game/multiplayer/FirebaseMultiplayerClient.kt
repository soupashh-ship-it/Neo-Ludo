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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    val preferredColor: PlayerColor = PlayerColor.RED
) : MultiplayerClient {

    private val tag = "FirebaseClient"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val reconciler = StateReconciler()
    private val reconnectManager = ReconnectManager(scope) {
        handleDisconnectAiTakeover()
    }

    private var database: FirebaseDatabase? = null
    private var roomRef: DatabaseReference? = null
    private var isFirebaseAvailable = false

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _chatEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    override val chatEvents: SharedFlow<ChatEvent> = _chatEvents.asSharedFlow()

    private val _roomState = MutableStateFlow<RoomSnapshot?>(null)
    override val roomState: StateFlow<RoomSnapshot?> = _roomState.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    override val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private var currentRoomId: String? = null
    private var actionSequence = 0L

    init {
        try {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()) {
                database = FirebaseDatabase.getInstance()
                isFirebaseAvailable = true
                _connectionState.value = ConnectionState.CONNECTED
            } else {
                isFirebaseAvailable = false
                _connectionState.value = ConnectionState.CONNECTED
            }
        } catch (e: Exception) {
            Log.w(tag, "Firebase not initialized, operating in local-safe mode", e)
            isFirebaseAvailable = false
            _connectionState.value = ConnectionState.CONNECTED
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
            // Local test room simulation
            val host = PlayerPresence("host_id", "Host Player", 2, PlayerColor.RED, isHost = true, isReady = true)
            val self = PlayerPresence(localPlayerId, localPlayerName, localAvatarId, PlayerColor.GREEN, isHost = false, isReady = false)
            val meta = RoomMetadata(cleanCode, "host_id", RoomStatus.LOBBY, 4)
            _roomState.value = RoomSnapshot(meta, listOf(host, self))
            return Result.success(Unit)
        }
    }

    private fun attachRoomListeners(code: String) {
        val ref = roomRef ?: return

        // 1. Meta Listener
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

        // 2. Players Listener
        ref.child("players").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playersList = mutableListOf<PlayerPresence>()
                for (child in snapshot.children) {
                    val jsonStr = child.getValue(String::class.java) ?: continue
                    runCatching {
                        playersList.add(json.decodeFromString<PlayerPresence>(jsonStr))
                    }
                }
                _roomState.value = _roomState.value?.copy(players = playersList)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "Players listener error: ${error.message}")
            }
        })

        // 3. GameState Listener
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

        // 4. Chat Listener
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

        if (isFirebaseAvailable && roomRef != null) {
            broadcastAction(ActionType.ROLL_DICE, "${next.diceState.value}")
            roomRef?.child("gameState")?.setValue(json.encodeToString(next))
        }

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

        if (isFirebaseAvailable && roomRef != null) {
            broadcastAction(ActionType.MOVE_PIECE, "$pieceId")
            roomRef?.child("gameState")?.setValue(json.encodeToString(next))
        }

        return Result.success(Unit)
    }

    override suspend fun sendChat(message: String): Result<Unit> {
        val self = _roomState.value?.players?.find { it.id == localPlayerId } ?: return Result.failure(IllegalStateException("Not in room"))
        val event = ChatEvent(
            id = UUID.randomUUID().toString(),
            senderId = self.id,
            senderName = self.name,
            senderColor = self.color,
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
        val self = _roomState.value?.players?.find { it.id == localPlayerId } ?: return Result.failure(IllegalStateException("Not in room"))
        val event = ChatEvent(
            id = UUID.randomUUID().toString(),
            senderId = self.id,
            senderName = self.name,
            senderColor = self.color,
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
        if (isFirebaseAvailable && roomRef != null) {
            roomRef?.child("players")?.child(localPlayerId)?.removeValue()
        }
        _roomState.value = null
        _gameState.value = null
        return Result.success(Unit)
    }

    private fun broadcastAction(type: ActionType, payload: String) {
        val action = NetworkAction(
            actionId = UUID.randomUUID().toString(),
            sequence = ++actionSequence,
            type = type,
            playerId = localPlayerId,
            payload = payload,
            timestamp = System.currentTimeMillis()
        )
        reconciler.recordAction(action)
        roomRef?.child("actions")?.push()?.setValue(json.encodeToString(action))
    }

    private fun handleDisconnectAiTakeover() {
        val state = _gameState.value ?: return
        if (state.activePlayer.id == localPlayerId) {
            val nextState = DisconnectAiProxy.executeProxyStep(state)
            _gameState.value = nextState
            if (isFirebaseAvailable && roomRef != null) {
                roomRef?.child("gameState")?.setValue(json.encodeToString(nextState))
            }
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..3).map { chars.random() }.joinToString("")
        val part2 = (1..3).map { chars.random() }.joinToString("")
        return "NL-$part1$part2"
    }

    override fun release() {
        scope.cancel()
    }
}
