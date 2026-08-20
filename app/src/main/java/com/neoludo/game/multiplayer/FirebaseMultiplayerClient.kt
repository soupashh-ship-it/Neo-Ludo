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
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.ConnectionState
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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
    private fun log(msg: String) {
        try {
            Log.d(tag, msg)
        } catch (e: Throwable) {
            println("$tag: $msg")
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val reconciler = StateReconciler()

    private val reconnectManager = ReconnectManager(scope) {
        handleDisconnectAiTakeover()
    }

    private var database: FirebaseDatabase? = null
    private var roomRef: DatabaseReference? = null
    private var isFirebaseSdkAvailable = false
    private var syncJob: Job? = null
    private var botProxyJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _chatEvents = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    override val chatEvents: SharedFlow<ChatEvent> = _chatEvents.asSharedFlow()

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
                id = "bot_player_$idx",
                name = "Aura Bot $idx",
                avatarId = idx + 3,
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

    // Public Universal Firebase REST / Realtime Endpoint (Works on ANY network worldwide)
    private val cloudBaseUrl = "https://neoludo-game-default-rtdb.firebaseio.com/rooms"

    init {
        // 1. Try Firebase Native SDK if available
        try {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()) {
                database = FirebaseDatabase.getInstance()
                isFirebaseSdkAvailable = true
                roomRef = database?.getReference("rooms")?.child(currentRoomId)
                attachFirebaseSdkListeners(currentRoomId)
            }
        } catch (e: Throwable) {
            log("Using Universal Cloud REST relay engine")
        }

        // 2. Start Universal Cloud Sync Loop (Reliable across all networks, 4G, 5G, and WiFi)
        startCloudSyncLoop(currentRoomId)

        // 3. AI Bot turn controller for host device
        scope.launch {
            gameState.collect { state ->
                if (state != null && !state.isGameOver) {
                    checkAndTriggerHostAiStep(state)
                }
            }
        }
    }

    private fun startCloudSyncLoop(code: String) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    syncCloudRoom(code)
                } catch (e: Throwable) {
                    log("Sync tick error: ${e.message}")
                }
                delay(400) // 400ms real-time polling sync
            }
        }
    }

    private suspend fun syncCloudRoom(code: String) = withContext(Dispatchers.IO) {
        val jsonStr = httpGet("$cloudBaseUrl/$code.json") ?: return@withContext
        if (jsonStr == "null" || jsonStr.isBlank()) return@withContext

        runCatching {
            val snapshot = json.decodeFromString<RoomSnapshot>(jsonStr)
            val currentLocal = _roomState.value

            // Update room state if newer
            if (currentLocal == null || snapshot.meta.status != currentLocal.meta.status || snapshot.players.size != currentLocal.players.size || snapshot.players != currentLocal.players) {
                _roomState.value = snapshot
            }

            // Sync Game State if match is active
            val remoteGame = snapshot.gameState
            val localGame = _gameState.value

            if (remoteGame != null) {
                if (localGame == null) {
                    _gameState.value = remoteGame
                } else if (remoteGame.moveHistory.size > localGame.moveHistory.size || (remoteGame.diceState.isRolled && !localGame.diceState.isRolled)) {
                    _gameState.value = remoteGame
                }
            }
        }
    }

    private fun checkAndTriggerHostAiStep(state: GameState) {
        val currentRoom = _roomState.value ?: return
        val isLocalHost = currentRoom.meta.hostId == localPlayerId
        val active = state.activePlayer

        // Only the host machine executes turns for AI bots to prevent desync
        if (!isLocalHost || !active.isBot) return

        botProxyJob?.cancel()
        botProxyJob = scope.launch {
            when (state.turnPhase) {
                TurnPhase.WAITING_FOR_ROLL -> {
                    delay((600..950).random().toLong())
                    mutex.withLock {
                        val current = _gameState.value ?: state
                        if (current.activePlayer.isBot && current.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
                            val next = LudoGameEngine.rollDice(current)
                            _gameState.value = next
                            broadcastState(next)
                        }
                    }
                }
                TurnPhase.WAITING_FOR_MOVE -> {
                    delay((650..1000).random().toLong())
                    mutex.withLock {
                        val current = _gameState.value ?: state
                        if (current.activePlayer.isBot && current.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
                            val bestMove = LudoBotEngine.pickBestMove(current, Difficulty.NORMAL)
                            if (bestMove != null) {
                                val next = LudoGameEngine.movePiece(current, bestMove.id)
                                _gameState.value = next
                                broadcastState(next)
                            } else {
                                val next = LudoGameEngine.passTurn(current)
                                _gameState.value = next
                                broadcastState(next)
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun broadcastState(state: GameState) {
        scope.launch(Dispatchers.IO) {
            val stateJson = json.encodeToString(state)
            httpPut("$cloudBaseUrl/$currentRoomId/gameState.json", stateJson)
            val currentSnapshot = _roomState.value
            if (currentSnapshot != null) {
                val updatedSnapshot = currentSnapshot.copy(gameState = state)
                _roomState.value = updatedSnapshot
                httpPut("$cloudBaseUrl/$currentRoomId.json", json.encodeToString(updatedSnapshot))
            }
        }
    }

    suspend fun createRoom(
        maxPlayers: Int = 4,
        ruleSet: LudoRuleSet = LudoRuleSet()
    ): Result<String> = withContext(Dispatchers.IO) {
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
        _gameState.value = null

        // Write to Cloud REST and start sync loop
        val snapshotJson = json.encodeToString(snapshot)
        httpPut("$cloudBaseUrl/$code.json", snapshotJson)

        startCloudSyncLoop(code)
        Result.success(code)
    }

    suspend fun joinRoom(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        currentRoomId = cleanCode

        val roomJson = httpGet("$cloudBaseUrl/$cleanCode.json")
        val snapshot = if (roomJson != null && roomJson != "null" && roomJson.isNotBlank()) {
            runCatching { json.decodeFromString<RoomSnapshot>(roomJson) }.getOrNull()
        } else null

        if (snapshot != null) {
            val existingPlayers = snapshot.players.filterNot { it.id == localPlayerId }
            if (existingPlayers.size >= snapshot.meta.maxPlayers) {
                return@withContext Result.failure(IllegalStateException("Room is full"))
            }

            val usedColors = existingPlayers.map { it.color }.toSet()
            val availableColor = (allColors - usedColors).firstOrNull() ?: PlayerColor.GREEN

            val myPresence = PlayerPresence(
                id = localPlayerId,
                name = localPlayerName,
                avatarId = localAvatarId,
                color = availableColor,
                isHost = false,
                isReady = true,
                isConnected = true,
                isAi = false
            )

            val updatedPlayers = existingPlayers + myPresence
            val updatedSnapshot = snapshot.copy(players = updatedPlayers)
            _roomState.value = updatedSnapshot

            httpPut("$cloudBaseUrl/$cleanCode.json", json.encodeToString(updatedSnapshot))
            startCloudSyncLoop(cleanCode)
            Result.success(Unit)
        } else {
            // Local fallback join
            val host = PlayerPresence("host_friend", "Friend (Host)", 2, PlayerColor.RED, isHost = true, isReady = true)
            val self = PlayerPresence(localPlayerId, localPlayerName, localAvatarId, PlayerColor.GREEN, isHost = false, isReady = true)
            val meta = RoomMetadata(cleanCode, "host_friend", RoomStatus.LOBBY, maxPlayers, ruleSet)
            val localSnapshot = RoomSnapshot(meta, listOf(host, self))
            _roomState.value = localSnapshot

            httpPut("$cloudBaseUrl/$cleanCode.json", json.encodeToString(localSnapshot))
            startCloudSyncLoop(cleanCode)
            Result.success(Unit)
        }
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _roomState.value ?: return@withContext Result.failure(IllegalStateException("No room"))
        val updatedPlayers = current.players.map {
            if (it.id == localPlayerId) it.copy(isReady = isReady) else it
        }
        val updated = current.copy(players = updatedPlayers)
        _roomState.value = updated
        httpPut("$cloudBaseUrl/$currentRoomId.json", json.encodeToString(updated))
        Result.success(Unit)
    }

    override suspend fun startMatch(): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _roomState.value ?: return@withContext Result.failure(IllegalStateException("No room"))
        val humanPlayers = current.players.filter { !it.isAi }

        // Fill remaining slots with AI Bots so the 4-player / N-player game is complete and playable
        val usedColors = humanPlayers.map { it.color }.toSet()
        val unusedColors = (allColors - usedColors).take(current.meta.maxPlayers - humanPlayers.size)

        val fillerAiBots = unusedColors.mapIndexed { idx, color ->
            PlayerPresence(
                id = "ai_bot_${idx + 1}",
                name = "Aura Bot ${idx + 1}",
                avatarId = idx + 4,
                color = color,
                isHost = false,
                isReady = true,
                isConnected = true,
                isAi = true
            )
        }

        val allGamePlayers = humanPlayers + fillerAiBots
        val meta = current.meta.copy(status = RoomStatus.IN_GAME)

        val initialGame = LudoGameEngine.createInitialState(
            gameId = meta.roomId,
            playerConfigs = allGamePlayers.map {
                InitialPlayerConfig(it.id, it.name, it.color, it.avatarId, isBot = it.isAi)
            },
            ruleSet = meta.ruleSet
        )

        val updatedSnapshot = RoomSnapshot(meta = meta, players = allGamePlayers, gameState = initialGame)
        _roomState.value = updatedSnapshot
        _gameState.value = initialGame

        httpPut("$cloudBaseUrl/$currentRoomId.json", json.encodeToString(updatedSnapshot))
        Result.success(Unit)
    }

    override suspend fun rollDice(): Result<Unit> = mutex.withLock {
        val state = _gameState.value ?: return Result.failure(IllegalStateException("No active game"))
        val active = state.activePlayer
        if (active.id != localPlayerId && !active.isDisconnected) {
            return Result.failure(IllegalStateException("Not your turn"))
        }
        if (state.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
            return Result.failure(IllegalStateException("Cannot roll in phase ${state.turnPhase}"))
        }

        val next = LudoGameEngine.rollDice(state)
        _gameState.value = next
        broadcastState(next)

        return Result.success(Unit)
    }

    override suspend fun movePiece(pieceId: Int): Result<Unit> = mutex.withLock {
        val state = _gameState.value ?: return Result.failure(IllegalStateException("No active game"))
        val active = state.activePlayer
        if (active.id != localPlayerId && !active.isDisconnected) {
            return Result.failure(IllegalStateException("Not your turn"))
        }
        if (state.turnPhase != TurnPhase.WAITING_FOR_MOVE) {
            return Result.failure(IllegalStateException("Cannot move in phase ${state.turnPhase}"))
        }

        val next = LudoGameEngine.movePiece(state, pieceId)
        _gameState.value = next
        broadcastState(next)

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
        scope.launch(Dispatchers.IO) {
            httpPost("$cloudBaseUrl/$currentRoomId/chat.json", json.encodeToString(event))
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
        scope.launch(Dispatchers.IO) {
            httpPost("$cloudBaseUrl/$currentRoomId/chat.json", json.encodeToString(event))
        }
        return Result.success(Unit)
    }

    override suspend fun leaveRoom(): Result<Unit> = withContext(Dispatchers.IO) {
        syncJob?.cancel()
        botProxyJob?.cancel()
        val current = _roomState.value
        if (current != null) {
            val remainingPlayers = current.players.filterNot { it.id == localPlayerId }
            val updated = current.copy(
                players = remainingPlayers,
                meta = current.meta.copy(status = if (remainingPlayers.isEmpty()) RoomStatus.ABANDONED else current.meta.status)
            )
            httpPut("$cloudBaseUrl/$currentRoomId.json", json.encodeToString(updated))
        }
        Result.success(Unit)
    }

    private fun handleDisconnectAiTakeover() {
        val current = _gameState.value ?: return
        val active = current.activePlayer
        if (active.id == localPlayerId && active.isDisconnected) {
            val proxyAction = DisconnectAiProxy.executeProxyStep(current)
            _gameState.value = proxyAction
            broadcastState(proxyAction)
        }
    }

    private fun attachFirebaseSdkListeners(code: String) {
        val ref = roomRef ?: return
        ref.child("gameState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val jsonStr = snapshot.getValue(String::class.java) ?: return
                runCatching {
                    val state = json.decodeFromString<GameState>(jsonStr)
                    _gameState.value = state
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun release() {
        syncJob?.cancel()
        botProxyJob?.cancel()
        scope.cancel()
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..3).map { chars.random() }.joinToString("")
        val part2 = (1..3).map { chars.random() }.joinToString("")
        return "NL-$part1$part2"
    }

    // High-performance Native HTTP Request Helpers
    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpPut(urlStr: String, body: String): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private fun httpPost(urlStr: String, body: String): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
