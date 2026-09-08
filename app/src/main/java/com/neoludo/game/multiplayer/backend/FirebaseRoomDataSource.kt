package com.neoludo.game.multiplayer.backend

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.NetworkEvent
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomError
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomSnapshot
import com.neoludo.game.multiplayer.model.RoomStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

class FirebaseRoomDataSource(
    private val customDatabase: FirebaseDatabase? = null
) {
    private val tag = "FirebaseRoomDataSource"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val db: FirebaseDatabase?
        get() = customDatabase ?: try {
            if (FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()) {
                FirebaseDatabase.getInstance()
            } else null
        } catch (e: Throwable) {
            Log.w(tag, "Firebase Database not available: ${e.message}")
            null
        }

    private fun roomsRef(): DatabaseReference? = db?.getReference("rooms")
    private fun roomRef(roomId: String): DatabaseReference? = roomsRef()?.child(roomId)

    suspend fun createRoom(
        meta: RoomMetadata,
        host: PlayerPresence
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(meta.roomId) ?: return@withContext Result.failure(RoomError.AuthenticationRequired)

        try {
            val metaMap = mapOf(
                "roomId" to meta.roomId,
                "hostId" to meta.hostId,
                "hostEpoch" to meta.hostEpoch,
                "status" to meta.status.name,
                "maxPlayers" to meta.maxPlayers,
                "fillBots" to meta.fillBots,
                "ruleSet" to json.encodeToString(meta.ruleSet),
                "createdAt" to ServerValue.TIMESTAMP,
                "updatedAt" to ServerValue.TIMESTAMP,
                "turnStartedAt" to meta.turnStartedAt,
                "turnDeadline" to meta.turnDeadline
            )

            val hostMap = mapOf(
                "id" to host.id,
                "name" to host.name,
                "avatarId" to host.avatarId,
                "color" to host.color.name,
                "isHost" to true,
                "isReady" to true,
                "isConnected" to true,
                "isAi" to false,
                "joinedAt" to ServerValue.TIMESTAMP,
                "lastSeen" to ServerValue.TIMESTAMP
            )

            val updates = mutableMapOf<String, Any>(
                "meta" to metaMap,
                "players/${host.id}" to hostMap
            )

            ref.updateChildren(updates).await()

            // Setup disconnect presence for host
            ref.child("players/${host.id}/isConnected").onDisconnect().setValue(false)
            ref.child("players/${host.id}/lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to create room: ${e.message}", e)
            Result.failure(RoomError.NetworkFailure(e.message ?: "Failed to create room"))
        }
    }

    suspend fun joinRoom(
        roomId: String,
        player: PlayerPresence
    ): Result<PlayerPresence> = withContext(Dispatchers.IO) {
        val cleanRoomId = roomId.trim().uppercase()
        val ref = roomRef(cleanRoomId) ?: return@withContext Result.failure(RoomError.AuthenticationRequired)

        suspendCancellableCoroutine { continuation ->
            ref.runTransaction(object : Transaction.Handler {
                var assignedPlayer: PlayerPresence? = null
                var transactionError: RoomError? = null

                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val metaData = currentData.child("meta")
                    if (metaData.value == null) {
                        transactionError = RoomError.RoomNotFound
                        return Transaction.abort()
                    }
                    val statusStr = metaData.child("status").getValue(String::class.java) ?: RoomStatus.LOBBY.name
                    val status = try { RoomStatus.valueOf(statusStr) } catch (e: Exception) { RoomStatus.LOBBY }
                    if (status != RoomStatus.LOBBY) {
                        transactionError = RoomError.GameAlreadyStarted
                        return Transaction.abort()
                    }

                    val maxPlayers = metaData.child("maxPlayers").getValue(Int::class.java) ?: 4
                    val playersData = currentData.child("players")

                    val existingPlayerIds = mutableListOf<String>()
                    val usedColors = mutableSetOf<PlayerColor>()

                    for (child in playersData.children) {
                        val pid = child.key ?: continue
                        existingPlayerIds.add(pid)
                        val colorStr = child.child("color").getValue(String::class.java)
                        if (colorStr != null) {
                            try { usedColors.add(PlayerColor.valueOf(colorStr)) } catch (_: Exception) {}
                        }
                    }

                    if (existingPlayerIds.contains(player.id)) {
                        // Re-joining existing seat
                        val existingColor = playersData.child(player.id).child("color").getValue(String::class.java)
                        val col = try { PlayerColor.valueOf(existingColor ?: player.color.name) } catch (_: Exception) { player.color }
                        assignedPlayer = player.copy(color = col, isConnected = true)
                        playersData.child(player.id).child("isConnected").value = true
                        playersData.child(player.id).child("lastSeen").value = System.currentTimeMillis()
                        return Transaction.success(currentData)
                    }

                    if (existingPlayerIds.size >= maxPlayers) {
                        transactionError = RoomError.RoomFull
                        return Transaction.abort()
                    }

                    // Assign first available non-conflicting color
                    val allColors = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
                    val availableColor = if (player.color !in usedColors) {
                        player.color
                    } else {
                        allColors.firstOrNull { it !in usedColors } ?: player.color
                    }

                    val newPlayer = player.copy(
                        color = availableColor,
                        isHost = false,
                        isReady = false,
                        isConnected = true,
                        joinedAt = System.currentTimeMillis(),
                        lastSeen = System.currentTimeMillis()
                    )
                    assignedPlayer = newPlayer

                    val playerMap = mapOf(
                        "id" to newPlayer.id,
                        "name" to newPlayer.name,
                        "avatarId" to newPlayer.avatarId,
                        "color" to newPlayer.color.name,
                        "isHost" to false,
                        "isReady" to false,
                        "isConnected" to true,
                        "isAi" to false,
                        "joinedAt" to System.currentTimeMillis(),
                        "lastSeen" to System.currentTimeMillis()
                    )

                    playersData.child(newPlayer.id).value = playerMap
                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (committed && assignedPlayer != null) {
                        // Setup presence disconnect
                        ref.child("players/${player.id}/isConnected").onDisconnect().setValue(false)
                        ref.child("players/${player.id}/lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
                        continuation.resume(Result.success(assignedPlayer!!))
                    } else {
                        val err = transactionError ?: error?.let { RoomError.NetworkFailure(it.message) } ?: RoomError.NetworkFailure("Transaction failed")
                        continuation.resume(Result.failure(err))
                    }
                }
            })
        }
    }

    suspend fun setPlayerReady(roomId: String, uid: String, isReady: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("players/$uid/isReady") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            ref.setValue(isReady).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPlayerConnected(roomId: String, uid: String, isConnected: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("players/$uid") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            val updates = mapOf<String, Any>(
                "isConnected" to isConnected,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            ref.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setFillBots(roomId: String, fillBots: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("meta/fillBots") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            ref.setValue(fillBots).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateRoomStatus(roomId: String, status: RoomStatus): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("meta") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            val updates = mapOf<String, Any>(
                "status" to status.name,
                "updatedAt" to ServerValue.TIMESTAMP
            )
            ref.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postAction(roomId: String, action: NetworkAction): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("actions/${action.actionId}") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            val map = mapOf(
                "actionId" to action.actionId,
                "sequence" to action.sequence,
                "type" to action.type.name,
                "playerId" to action.playerId,
                "payload" to action.payload,
                "timestamp" to ServerValue.TIMESTAMP
            )
            ref.setValue(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun publishGameState(
        roomId: String,
        state: GameState,
        metaUpdate: RoomMetadata? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId) ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            val stateJson = json.encodeToString(state)
            val updates = mutableMapOf<String, Any>(
                "state/raw" to stateJson,
                "state/version" to state.version,
                "state/activePlayerIndex" to state.activePlayerIndex,
                "state/turnPhase" to state.turnPhase.name,
                "state/updatedAt" to ServerValue.TIMESTAMP
            )

            if (metaUpdate != null) {
                updates["meta/status"] = metaUpdate.status.name
                updates["meta/turnStartedAt"] = metaUpdate.turnStartedAt
                updates["meta/turnDeadline"] = metaUpdate.turnDeadline
                updates["meta/updatedAt"] = ServerValue.TIMESTAMP
            }

            ref.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to publish game state: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun publishEvent(roomId: String, event: NetworkEvent): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("events/${event.eventId}") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            val map = mapOf(
                "eventId" to event.eventId,
                "type" to event.type.name,
                "playerId" to event.playerId,
                "payload" to event.payload,
                "version" to event.version,
                "timestamp" to ServerValue.TIMESTAMP
            )
            ref.setValue(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChat(roomId: String, chat: ChatEvent): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("chat/${chat.id}") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            val map = mapOf(
                "id" to chat.id,
                "senderId" to chat.senderId,
                "senderName" to chat.senderName,
                "senderColor" to chat.senderColor.name,
                "message" to (chat.message ?: ""),
                "emoteId" to (chat.emoteId ?: ""),
                "timestamp" to ServerValue.TIMESTAMP
            )
            ref.setValue(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ref = roomRef(roomId)?.child("players/$uid") ?: return@withContext Result.failure(RoomError.AuthenticationRequired)
        try {
            ref.removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRoom(roomId: String): Flow<RoomSnapshot?> = callbackFlow {
        val ref = roomRef(roomId)
        if (ref == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }

                try {
                    val metaSnap = snapshot.child("meta")
                    val meta = if (metaSnap.exists()) {
                        val statusStr = metaSnap.child("status").getValue(String::class.java) ?: RoomStatus.LOBBY.name
                        val status = try { RoomStatus.valueOf(statusStr) } catch (_: Exception) { RoomStatus.LOBBY }
                        val ruleSetJson = metaSnap.child("ruleSet").getValue(String::class.java)
                        val ruleSet = if (!ruleSetJson.isNullOrBlank()) {
                            try { json.decodeFromString(ruleSetJson) } catch (_: Exception) { com.neoludo.game.engine.model.LudoRuleSet() }
                        } else com.neoludo.game.engine.model.LudoRuleSet()

                        RoomMetadata(
                            roomId = metaSnap.child("roomId").getValue(String::class.java) ?: roomId,
                            hostId = metaSnap.child("hostId").getValue(String::class.java) ?: "",
                            hostEpoch = metaSnap.child("hostEpoch").getValue(Long::class.java) ?: 1L,
                            status = status,
                            maxPlayers = metaSnap.child("maxPlayers").getValue(Int::class.java) ?: 4,
                            fillBots = metaSnap.child("fillBots").getValue(Boolean::class.java) ?: false,
                            ruleSet = ruleSet,
                            createdAt = metaSnap.child("createdAt").getValue(Long::class.java) ?: 0L,
                            updatedAt = metaSnap.child("updatedAt").getValue(Long::class.java) ?: 0L,
                            turnStartedAt = metaSnap.child("turnStartedAt").getValue(Long::class.java) ?: 0L,
                            turnDeadline = metaSnap.child("turnDeadline").getValue(Long::class.java) ?: 0L
                        )
                    } else RoomMetadata(roomId = roomId)

                    val playersSnap = snapshot.child("players")
                    val playersList = mutableListOf<PlayerPresence>()
                    for (child in playersSnap.children) {
                        val pid = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                        val name = child.child("name").getValue(String::class.java) ?: "Player"
                        val avatarId = child.child("avatarId").getValue(Int::class.java) ?: 1
                        val colorStr = child.child("color").getValue(String::class.java) ?: PlayerColor.RED.name
                        val color = try { PlayerColor.valueOf(colorStr) } catch (_: Exception) { PlayerColor.RED }
                        val isHost = child.child("isHost").getValue(Boolean::class.java) ?: false
                        val isReady = child.child("isReady").getValue(Boolean::class.java) ?: false
                        val isConnected = child.child("isConnected").getValue(Boolean::class.java) ?: true
                        val isAi = child.child("isAi").getValue(Boolean::class.java) ?: false
                        val joinedAt = child.child("joinedAt").getValue(Long::class.java) ?: 0L
                        val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L

                        playersList.add(
                            PlayerPresence(
                                id = pid,
                                name = name,
                                avatarId = avatarId,
                                color = color,
                                isHost = isHost,
                                isReady = isReady,
                                isConnected = isConnected,
                                isAi = isAi,
                                joinedAt = joinedAt,
                                lastSeen = lastSeen
                            )
                        )
                    }

                    val stateRaw = snapshot.child("state/raw").getValue(String::class.java)
                    val gameState: GameState? = if (!stateRaw.isNullOrBlank()) {
                        try {
                            json.decodeFromString<GameState>(stateRaw)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to decode state: ${e.message}")
                            null
                        }
                    } else null

                    trySend(RoomSnapshot(meta = meta, players = playersList, gameState = gameState))
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing room snapshot: ${e.message}", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "Room listener cancelled: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeActions(roomId: String): Flow<NetworkAction> = callbackFlow {
        val ref = roomRef(roomId)?.child("actions")
        if (ref == null) {
            close()
            return@callbackFlow
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val actionId = snapshot.child("actionId").getValue(String::class.java) ?: snapshot.key ?: return
                    val seq = snapshot.child("sequence").getValue(Long::class.java) ?: 0L
                    val typeStr = snapshot.child("type").getValue(String::class.java) ?: ActionType.ROLL_DICE.name
                    val type = try { ActionType.valueOf(typeStr) } catch (_: Exception) { ActionType.ROLL_DICE }
                    val playerId = snapshot.child("playerId").getValue(String::class.java) ?: ""
                    val payload = snapshot.child("payload").getValue(String::class.java) ?: ""
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    trySend(
                        NetworkAction(
                            actionId = actionId,
                            sequence = seq,
                            type = type,
                            playerId = playerId,
                            payload = payload,
                            timestamp = timestamp
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing action: ${e.message}", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeEvents(roomId: String): Flow<NetworkEvent> = callbackFlow {
        val ref = roomRef(roomId)?.child("events")
        if (ref == null) {
            close()
            return@callbackFlow
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val eventId = snapshot.child("eventId").getValue(String::class.java) ?: snapshot.key ?: return
                    val typeStr = snapshot.child("type").getValue(String::class.java) ?: ""
                    val type = try { com.neoludo.game.multiplayer.model.NetworkEventType.valueOf(typeStr) } catch (_: Exception) { return }
                    val playerId = snapshot.child("playerId").getValue(String::class.java) ?: ""
                    val payload = snapshot.child("payload").getValue(String::class.java) ?: ""
                    val version = snapshot.child("version").getValue(Long::class.java) ?: 0L
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    trySend(
                        NetworkEvent(
                            eventId = eventId,
                            type = type,
                            playerId = playerId,
                            payload = payload,
                            version = version,
                            timestamp = timestamp
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing event: ${e.message}", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeChat(roomId: String): Flow<ChatEvent> = callbackFlow {
        val ref = roomRef(roomId)?.child("chat")
        if (ref == null) {
            close()
            return@callbackFlow
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val id = snapshot.child("id").getValue(String::class.java) ?: snapshot.key ?: return
                    val senderId = snapshot.child("senderId").getValue(String::class.java) ?: ""
                    val senderName = snapshot.child("senderName").getValue(String::class.java) ?: ""
                    val colorStr = snapshot.child("senderColor").getValue(String::class.java) ?: PlayerColor.RED.name
                    val color = try { PlayerColor.valueOf(colorStr) } catch (_: Exception) { PlayerColor.RED }
                    val message = snapshot.child("message").getValue(String::class.java)
                    val emoteId = snapshot.child("emoteId").getValue(String::class.java)
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    trySend(
                        ChatEvent(
                            id = id,
                            senderId = senderId,
                            senderName = senderName,
                            senderColor = color,
                            message = message.takeIf { !it.isNullOrBlank() },
                            emoteId = emoteId.takeIf { !it.isNullOrBlank() },
                            timestamp = timestamp
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing chat: ${e.message}", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeConnected(): Flow<Boolean> = callbackFlow {
        val connectedRef = db?.getReference(".info/connected")
        if (connectedRef == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                trySend(isConnected)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        connectedRef.addValueEventListener(listener)
        awaitClose { connectedRef.removeEventListener(listener) }
    }
}
