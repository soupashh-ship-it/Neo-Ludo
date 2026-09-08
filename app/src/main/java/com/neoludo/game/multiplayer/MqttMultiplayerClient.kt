package com.neoludo.game.multiplayer

import android.util.Log
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.engine.rules.MoveValidator
import com.neoludo.game.multiplayer.backend.MqttRelay
import com.neoludo.game.multiplayer.backend.MqttRelayDataSource
import com.neoludo.game.multiplayer.backend.RelayLookup
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.ChatEvent
import com.neoludo.game.multiplayer.model.ConnectionState
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomError
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomSnapshot
import com.neoludo.game.multiplayer.model.RoomStatus
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Online rooms over the free public MQTT relay — no Firebase project, no
 * `google-services.json`, no account. Chosen automatically by
 * [OnlineClientFactory] when Firebase isn't configured in this build.
 *
 * Same protocol guarantees as the Firebase client: host-authoritative
 * processing via [AuthoritativeGameProcessor], UUID idempotency, strict
 * version fencing, AFK timeouts with AI takeover. Presence rides on retained
 * messages + Last-Will, so crashed peers show as disconnected.
 */
class MqttMultiplayerClient(
    val localPlayerId: String,
    val localPlayerName: String,
    val localAvatarId: Int,
    override val preferredColor: PlayerColor = PlayerColor.RED,
    val initialRoomId: String = MqttRelay.generateRoomCode(),
    override val maxPlayers: Int = 4,
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    private val relay: MqttRelayDataSource = MqttRelayDataSource()
) : OnlineRoomClient {

    private val tag = "MqttMultiplayerClient"
    private fun log(msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("$tag: $msg")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sequenceCounter = AtomicLong(0L)

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

    override val transportDebug: String
        get() = "Relay " + MqttRelay.shortName(roomServer ?: relay.connectedServer())

    private val snapshotLock = Any()
    private var latestMeta: RoomMetadata? = null
    private val latestPlayers = mutableMapOf<String, PlayerPresence>()

    private var listenerJobs: List<Job> = emptyList()
    private var botTurnJob: Job? = null
    private var timeoutJob: Job? = null
    private var heartbeatJob: Job? = null
    private var retryJob: Job? = null
    private var joinedCode: String? = null
    /**
     * Broker network this room lives on. Reconnects always return here —
     * hopping to another network mid-room would split-brain the peers.
     */
    private var roomServer: String? = null

    init {
        relay.onConnectionLost = {
            scope.launch {
                if (joinedCode != null) {
                    _connectionState.value = ConnectionState.RECONNECTING
                    reconnectManager.onDisconnected()
                    startRetryLoop()
                }
            }
        }
    }

    // ---------- room lifecycle ----------

    override suspend fun createRoom(
        playerCount: Int,
        fillBots: Boolean,
        rules: LudoRuleSet
    ): Result<String> = withContext(Dispatchers.IO) {
        currentUid = localPlayerId
        val conn = ensureConnected(null)
        if (conn.isFailure) return@withContext Result.failure(conn.exceptionOrNull()!!)
        _connectionState.value = ConnectionState.CONNECTED

        var code = MqttRelay.generateRoomCode()
        var attempts = 0
        while (attempts < 4) {
            val existing = relay.awaitRetained(MqttRelay.metaTopic(code), 2000L)
            if (existing == null || isReusableMeta(existing)) break
            code = MqttRelay.generateRoomCode()
            attempts++
        }
        // Final check on the chosen code.
        val existing = relay.awaitRetained(MqttRelay.metaTopic(code), 2000L)
        if (existing != null && !isReusableMeta(existing)) {
            return@withContext Result.failure(RoomError.NetworkFailure("Room code collision, try again"))
        }
        currentRoomId = code
        currentAssignedColor = preferredColor
        // Reconnect with the crash-detection will registered for this room.
        val willConn = ensureConnectedWithWill(code)
        if (willConn.isFailure) return@withContext Result.failure(willConn.exceptionOrNull()!!)
        _connectionState.value = ConnectionState.CONNECTED

        val now = System.currentTimeMillis()
        val meta = RoomMetadata(
            roomId = code,
            hostId = currentUid,
            hostEpoch = 1L,
            status = RoomStatus.LOBBY,
            maxPlayers = playerCount.coerceIn(2, 4),
            fillBots = fillBots,
            ruleSet = rules,
            createdAt = now,
            updatedAt = now
        )
        val hostPresence = PlayerPresence(
            id = currentUid,
            name = localPlayerName,
            avatarId = localAvatarId,
            color = preferredColor,
            isHost = true,
            isReady = true,
            isConnected = true,
            isAi = false,
            joinedAt = now,
            lastSeen = now
        )
        val metaPub = relay.publish(MqttRelay.metaTopic(code), relay.json.encodeToString(meta), retained = true)
        val presencePub = relay.publish(
            MqttRelay.presenceTopic(code, currentUid),
            relay.json.encodeToString(hostPresence),
            retained = true
        )
        if (metaPub.isFailure || presencePub.isFailure) {
            return@withContext Result.failure(
                RoomError.NetworkFailure("Could not reach the relay. Check your connection and try again.")
            )
        }
        roomServer = relay.connectedServer()
        // Read-back verify: never hand out a code the relay can't serve.
        // (A publish can report success to a broker that then drops it.)
        val confirmed = relay.awaitRetained(MqttRelay.metaTopic(code), 2500L)?.let { raw ->
            runCatching { relay.json.decodeFromString<RoomMetadata>(raw) }.getOrNull()?.roomId == code
        } ?: false
        if (!confirmed) {
            return@withContext Result.failure(
                RoomError.NetworkFailure("Room was not confirmed on the relay. Check your connection and try again.")
            )
        }
        attachListeners(code)
        synchronized(snapshotLock) {
            latestMeta = meta
            latestPlayers[currentUid] = hostPresence
            publishSnapshotLocked()
        }
        Result.success(code)
    }

    override suspend fun joinRoom(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val code = MqttRelay.sanitizeCode(roomId)
            ?: return@withContext Result.failure(RoomError.RoomNotFound)
        currentUid = localPlayerId
        val conn = ensureConnected(null)
        if (conn.isFailure) return@withContext Result.failure(conn.exceptionOrNull()!!)
        _connectionState.value = ConnectionState.CONNECTED

        // The room may live on a different broker network than our default —
        // scan all of them (the hit pins that server for the rest of the room).
        val rawMeta = when (val lookup = relay.fetchRetainedFromAny(MqttRelay.metaTopic(code))) {
            is RelayLookup.Found -> lookup.payload
            is RelayLookup.NotFound -> return@withContext Result.failure(
                if (!lookup.relayReached) {
                    RoomError.NetworkFailure(
                        "Could not reach the game relay. Check your internet connection and retry."
                    )
                } else {
                    RoomError.RoomNotFoundDetailed(
                        "Check the code, ask the host for a fresh code from the latest version, " +
                            "and make sure both phones run the same version (see Settings)."
                    )
                }
            )
        }
        // The probe pinned the room's server; all subsequent connects stay there.
        roomServer = MqttRelay.pinnedServer
        val meta = runCatching { relay.json.decodeFromString<RoomMetadata>(rawMeta) }.getOrNull()
            ?: return@withContext Result.failure(RoomError.RoomNotFound)
        if (isReusableMeta(rawMeta)) {
            return@withContext Result.failure(RoomError.RoomNotFound)
        }
        if (meta.status != RoomStatus.LOBBY) {
            return@withContext Result.failure(RoomError.GameAlreadyStarted)
        }
        currentRoomId = code
        attachListeners(code)

        // Seed players from retained presence docs.
        val now = System.currentTimeMillis()
        val known = fetchPlayers(code)
        // Cloned installs share one id: if OUR seat is heartbeating, another
        // live device owns it — mint a fresh id instead of stealing the seat.
        currentUid = JoinUidResolver.resolve(currentUid, known, now)
        val existing = known[currentUid]
        val assigned: PlayerPresence
        if (existing != null) {
            // Rejoining our own seat.
            assigned = existing.copy(isConnected = true, lastSeen = now)
            currentAssignedColor = assigned.color
        } else {
            if (known.size >= meta.maxPlayers) {
                return@withContext Result.failure(RoomError.RoomFull)
            }
            val used = known.values.map { it.color }.toSet()
            val color = if (preferredColor !in used) preferredColor
            else listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
                .firstOrNull { it !in used } ?: preferredColor
            assigned = PlayerPresence(
                id = currentUid,
                name = localPlayerName,
                avatarId = localAvatarId,
                color = color,
                isHost = false,
                isReady = false,
                isConnected = true,
                isAi = false,
                joinedAt = now,
                lastSeen = now
            )
            currentAssignedColor = color
        }
        // Reconnect with the crash-detection will registered for this room.
        val willConn = ensureConnectedWithWill(code)
        if (willConn.isFailure) return@withContext Result.failure(willConn.exceptionOrNull()!!)
        _connectionState.value = ConnectionState.CONNECTED
        attachListeners(code)
        val presencePub = relay.publish(
            MqttRelay.presenceTopic(code, currentUid),
            relay.json.encodeToString(assigned),
            retained = true
        )
        if (presencePub.isFailure) {
            return@withContext Result.failure(
                RoomError.NetworkFailure("Joined but could not announce presence. Check your connection and retry.")
            )
        }
        roomServer = relay.connectedServer()
        synchronized(snapshotLock) {
            latestMeta = meta
            latestPlayers.putAll(known)
            latestPlayers[currentUid] = assigned
            publishSnapshotLocked()
        }
        // Pull the latest retained state snapshot, if the match already has one.
        relay.awaitRetained(MqttRelay.stateTopic(code), 2500L)?.let { raw ->
            runCatching { relay.json.decodeFromString<GameState>(raw) }.getOrNull()?.let { game ->
                _gameState.value = game
                synchronized(snapshotLock) { publishSnapshotLocked() }
            }
        }
        Result.success(Unit)
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val code = joinedCode ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val self = synchronized(snapshotLock) { latestPlayers[currentUid] }
            ?: return@withContext Result.failure(RoomError.RoomNotFound)
        relay.publish(
            MqttRelay.presenceTopic(code, currentUid),
            relay.json.encodeToString(self.copy(isReady = isReady, lastSeen = System.currentTimeMillis())),
            retained = true
        )
        Result.success(Unit)
    }

    override suspend fun setFillBots(fillBots: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val snap = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        if (!hostElectionManager.isLocalPlayerHost(currentUid, snap.meta, snap.players)) {
            return@withContext Result.failure(RoomError.NotHost)
        }
        publishMeta(snap.meta.copy(fillBots = fillBots, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun startMatch(): Result<Unit> = withContext(Dispatchers.IO) {
        val snap = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        if (!hostElectionManager.isLocalPlayerHost(currentUid, snap.meta, snap.players)) {
            return@withContext Result.failure(RoomError.NotHost)
        }
        val initResult = authoritativeProcessor.initializeGame(snap.meta, snap.players)
        _gameState.value = initResult.updatedState
        publishMeta(initResult.updatedMeta ?: snap.meta)
        publishState(initResult.updatedState)
        initResult.events.forEach { publishEvent(it) }
        Result.success(Unit)
    }

    // ---------- game actions ----------

    override suspend fun rollDice(): Result<Unit> = withContext(Dispatchers.IO) {
        val snap = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val state = _gameState.value ?: return@withContext Result.failure(RoomError.GameAlreadyStarted)
        if (!isLocalTurn(snap, state)) return@withContext Result.failure(RoomError.NotYourTurn)
        postAction(
            snap.meta.roomId,
            NetworkAction(
                actionId = UUID.randomUUID().toString(),
                sequence = sequenceCounter.incrementAndGet(),
                type = ActionType.ROLL_DICE,
                playerId = state.activePlayer.id,
                payload = "",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun movePiece(pieceId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val snap = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val state = _gameState.value ?: return@withContext Result.failure(RoomError.GameAlreadyStarted)
        if (!isLocalTurn(snap, state)) return@withContext Result.failure(RoomError.NotYourTurn)
        postAction(
            snap.meta.roomId,
            NetworkAction(
                actionId = UUID.randomUUID().toString(),
                sequence = sequenceCounter.incrementAndGet(),
                type = ActionType.MOVE_PIECE,
                playerId = state.activePlayer.id,
                payload = pieceId.toString(),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun sendChat(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = message.trim()
        if (clean.isBlank()) return@withContext Result.failure(RoomError.NetworkFailure("Message is empty"))
        if (clean.length > 280) return@withContext Result.failure(RoomError.NetworkFailure("Message too long (max 280)"))
        val code = joinedCode ?: return@withContext Result.failure(RoomError.RoomNotFound)
        publishChat(
            code,
            ChatEvent(
                id = UUID.randomUUID().toString(),
                senderId = currentUid,
                senderName = localPlayerName,
                senderColor = currentAssignedColor,
                message = clean,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun sendEmote(emoteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = emoteId.trim().take(64)
        if (clean.isBlank()) return@withContext Result.failure(RoomError.NetworkFailure("Emote is empty"))
        val code = joinedCode ?: return@withContext Result.failure(RoomError.RoomNotFound)
        publishChat(
            code,
            ChatEvent(
                id = UUID.randomUUID().toString(),
                senderId = currentUid,
                senderName = localPlayerName,
                senderColor = currentAssignedColor,
                emoteId = clean,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun leaveRoom(): Result<Unit> = withContext(Dispatchers.IO) {
        val code = joinedCode
        if (code != null) {
            val self = synchronized(snapshotLock) { latestPlayers[currentUid] }
            if (self != null) {
                relay.publish(
                    MqttRelay.presenceTopic(code, currentUid),
                    relay.json.encodeToString(self.copy(isConnected = false, lastSeen = System.currentTimeMillis())),
                    retained = true
                )
            }
        }
        release()
        Result.success(Unit)
    }

    /** Manual retry from the lobby's "Disconnected" banner. */
    override suspend fun refreshConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        val code = joinedCode ?: return@withContext Result.failure(RoomError.RoomNotFound)
        retryJob?.cancel()
        relay.disconnect()
        val will = willFor(code)
        val res = relay.connect(
            MqttRelayDataSource.newClientId(currentUid.ifBlank { localPlayerId }),
            will.first,
            will.second,
            preferServer = roomServer
        )
        if (res.isFailure) {
            _connectionState.value = ConnectionState.RECONNECTING
            startRetryLoop()
            return@withContext Result.failure(
                res.exceptionOrNull() ?: RoomError.NetworkFailure("Relay unreachable")
            )
        }
        roomServer = relay.connectedServer()
        republishSelf(code)
        _connectionState.value = ConnectionState.CONNECTED
        reconnectManager.onConnected()
        maybeTriggerHost()
        Result.success(Unit)
    }

    override fun release() {
        botTurnJob?.cancel()
        timeoutJob?.cancel()
        heartbeatJob?.cancel()
        retryJob?.cancel()
        listenerJobs.forEach { it.cancel() }
        listenerJobs = emptyList()
        joinedCode = null
        roomServer = null
        scope.launch { relay.disconnect() }
        scope.cancel()
    }

    // ---------- internals ----------

    private fun isLocalTurn(snap: RoomSnapshot, state: GameState): Boolean {
        val isHost = hostElectionManager.isLocalPlayerHost(currentUid, snap.meta, snap.players)
        return state.activePlayer.id == currentUid || isHost
    }

    private suspend fun ensureConnected(
        will: Pair<String, String>?,
        preferServer: String? = roomServer
    ): Result<Unit> {
        if (relay.isConnected()) return Result.success(Unit)
        val id = MqttRelayDataSource.newClientId(currentUid.ifBlank { localPlayerId })
        return relay.connect(id, will?.first, will?.second, preferServer = preferServer)
    }

    /**
     * The Last-Will must be registered at CONNECT time, but the room code is
     * only known after the existence checks — so reconnect once with the will
     * bound to this room. Cheap (pre-join, no subscriptions live yet).
     */
    private suspend fun ensureConnectedWithWill(
        code: String,
        preferServer: String? = roomServer
    ): Result<Unit> {
        relay.disconnect()
        val id = MqttRelayDataSource.newClientId(currentUid.ifBlank { localPlayerId })
        val will = willFor(code)
        val res = relay.connect(id, will.first, will.second, preferServer = preferServer)
        if (res.isSuccess) roomServer = relay.connectedServer()
        return res
    }

    private fun willFor(code: String): Pair<String, String> {
        val offline = PlayerPresence(
            id = currentUid,
            name = localPlayerName,
            avatarId = localAvatarId,
            color = currentAssignedColor,
            isConnected = false,
            lastSeen = System.currentTimeMillis()
        )
        return MqttRelay.presenceTopic(code, currentUid) to relay.json.encodeToString(offline)
    }

    private fun isReusableMeta(rawMeta: String): Boolean {
        val meta = runCatching { relay.json.decodeFromString<RoomMetadata>(rawMeta) }.getOrNull()
            ?: return false
        if (meta.status == RoomStatus.COMPLETED || meta.status == RoomStatus.ABANDONED) return true
        // Stale tombstone: untouched for over a day.
        if (meta.updatedAt > 0 && System.currentTimeMillis() - meta.updatedAt > 24 * 60 * 60 * 1000L) return true
        return false
    }

    private suspend fun fetchPlayers(code: String): Map<String, PlayerPresence> {
        // Retained presence docs arrive on wildcard subscribe; collect briefly.
        val found = mutableMapOf<String, PlayerPresence>()
        val handler: (String, String) -> Unit = { topic, payload ->
            val uid = MqttRelay.uidFromPresenceTopic(topic, code)
            if (uid != null) {
                runCatching { relay.json.decodeFromString<PlayerPresence>(payload) }.getOrNull()?.let {
                    synchronized(found) { found[uid] = it }
                }
            }
        }
        relay.subscribePrefix(MqttRelay.playersPrefix(code), handler = handler)
        try {
            delay(2000L)
        } finally {
            relay.unsubscribePrefix(MqttRelay.playersPrefix(code), handler)
        }
        return found
    }

    private fun attachListeners(code: String) {
        listenerJobs.forEach { it.cancel() }
        joinedCode = code
        val jobs = mutableListOf<Job>()

        jobs += relay.observeExact(MqttRelay.metaTopic(code)).onEach { raw ->
            runCatching { relay.json.decodeFromString<RoomMetadata>(raw) }.getOrNull()?.let { meta ->
                synchronized(snapshotLock) {
                    latestMeta = meta
                    publishSnapshotLocked()
                }
                maybeTriggerHost()
            }
        }.launchIn(scope)

        jobs += relay.observePrefix(MqttRelay.playersPrefix(code)).onEach { (topic, payload) ->
            val uid = MqttRelay.uidFromPresenceTopic(topic, code) ?: return@onEach
            runCatching { relay.json.decodeFromString<PlayerPresence>(payload) }.getOrNull()?.let { presence ->
                synchronized(snapshotLock) {
                    latestPlayers[uid] = presence
                    publishSnapshotLocked()
                }
            }
        }.launchIn(scope)

        jobs += relay.observeExact(MqttRelay.stateTopic(code)).onEach { raw ->
            runCatching { relay.json.decodeFromString<GameState>(raw) }.getOrNull()?.let { remote ->
                val local = _gameState.value
                if (local == null || remote.version > local.version) {
                    _gameState.value = remote
                    synchronized(snapshotLock) { publishSnapshotLocked() }
                    maybeTriggerHost()
                }
            }
        }.launchIn(scope)

        jobs += relay.observeExact(MqttRelay.actionsTopic(code)).onEach { raw ->
            val action = runCatching { relay.json.decodeFromString<NetworkAction>(raw) }.getOrNull()
                ?: return@onEach
            val snap = _roomState.value ?: return@onEach
            val game = _gameState.value ?: snap.gameState ?: return@onEach
            if (!hostElectionManager.isLocalPlayerHost(currentUid, snap.meta, snap.players)) return@onEach
            val result = authoritativeProcessor.processAction(action, game, snap.meta) ?: return@onEach
            _gameState.value = result.updatedState
            result.updatedMeta?.let { publishMeta(it) }
            publishState(result.updatedState)
            result.events.forEach { publishEvent(it) }
        }.launchIn(scope)

        jobs += relay.observeExact(MqttRelay.chatTopic(code)).onEach { raw ->
            runCatching { relay.json.decodeFromString<ChatEvent>(raw) }.getOrNull()?.let { chat ->
                _chatEvents.tryEmit(chat)
            }
        }.launchIn(scope)

        listenerJobs = jobs

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                val c = joinedCode ?: break
                if (!relay.isConnected()) continue
                val self = synchronized(snapshotLock) { latestPlayers[currentUid] } ?: continue
                val beat = relay.publish(
                    MqttRelay.presenceTopic(c, currentUid),
                    relay.json.encodeToString(self.copy(lastSeen = System.currentTimeMillis())),
                    retained = true
                )
                if (beat.isFailure) {
                    // Half-dead socket: Paho still reports connected but
                    // nothing goes out (peers will soon see us as offline
                    // via the Last-Will). Drop it and rejoin the room server.
                    handleLinkFailure()
                }
            }
        }
    }

    private fun maybeTriggerHost() {
        val snap = _roomState.value ?: return
        val game = _gameState.value ?: snap.gameState ?: return
        if (!hostElectionManager.isLocalPlayerHost(currentUid, snap.meta, snap.players)) return
        if (snap.meta.status != RoomStatus.IN_GAME) return
        triggerHostEvaluation(snap.meta, game)
    }

    private fun triggerHostEvaluation(meta: RoomMetadata, state: GameState) {
        if (state.isGameOver) return
        val active = state.activePlayer
        botTurnJob?.cancel()
        timeoutJob?.cancel()

        if (active.isBot) {
            botTurnJob = scope.launch {
                delay(600)
                if (!isActive) return@launch
                when (state.turnPhase) {
                    TurnPhase.WAITING_FOR_ROLL -> rollDice()
                    TurnPhase.WAITING_FOR_MOVE -> {
                        val best = LudoBotEngine.pickBestMove(state, Difficulty.NORMAL)
                        if (best != null) movePiece(best.id)
                        else postAction(
                            meta.roomId,
                            NetworkAction(
                                actionId = UUID.randomUUID().toString(),
                                sequence = sequenceCounter.incrementAndGet(),
                                type = ActionType.PASS_TURN,
                                playerId = active.id,
                                payload = "v=${state.version}",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    else -> Unit
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        val deadline = meta.turnDeadline.takeIf { it > 0 } ?: (now + meta.ruleSet.turnTimerSeconds * 1000L)
        val remainingMs = (deadline - now).coerceAtLeast(0L)
        timeoutJob = scope.launch {
            delay(remainingMs + 500L)
            if (!isActive) return@launch
            val latestState = _gameState.value ?: return@launch
            val latestSnap = _roomState.value ?: return@launch
            if (latestState.activePlayerIndex == state.activePlayerIndex &&
                latestState.version == state.version && !latestState.isGameOver
            ) {
                log("Turn deadline elapsed for ${active.name}, executing timeout step")
                val result = authoritativeProcessor.processTimeout(latestState, latestSnap.meta)
                if (result != null) {
                    _gameState.value = result.updatedState
                    result.updatedMeta?.let { publishMeta(it) }
                    publishState(result.updatedState)
                    result.events.forEach { publishEvent(it) }
                }
            }
        }
    }

    /**
     * Immediately re-announce ourselves after a reconnect: our Last-Will may
     * have stamped us offline on the broker while we were gone, and peers
     * (including our own lobby card) would otherwise show "Disconnected"
     * until the next 15s heartbeat. Hosts also refresh the room meta.
     */
    private suspend fun republishSelf(code: String) {
        val now = System.currentTimeMillis()
        val self = synchronized(snapshotLock) { latestPlayers[currentUid] } ?: return
        relay.publish(
            MqttRelay.presenceTopic(code, currentUid),
            relay.json.encodeToString(self.copy(isConnected = true, lastSeen = now)),
            retained = true
        )
        val meta = synchronized(snapshotLock) { latestMeta }
        if (meta != null && meta.hostId == currentUid) {
            relay.publish(
                MqttRelay.metaTopic(code),
                relay.json.encodeToString(meta.copy(updatedAt = now)),
                retained = true
            )
        }
    }

    /**
     * The link looks dead (failed publish, or Paho reported connection loss):
     * drop the socket and let the retry loop return to the room's server.
     */
    private fun handleLinkFailure() {
        if (joinedCode == null) return
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectManager.onDisconnected()
        scope.launch {
            try {
                relay.disconnect()
            } catch (_: Throwable) {
            }
            startRetryLoop()
        }
    }

    private fun startRetryLoop() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            while (isActive && joinedCode != null && !relay.isConnected()) {
                delay(3000L)
                val code = joinedCode ?: break
                val will = willFor(code)
                // Return to the ROOM's server — racing the list again could
                // land on a broker network where this room doesn't exist.
                val res = relay.connect(
                    MqttRelayDataSource.newClientId(currentUid),
                    will.first,
                    will.second,
                    preferServer = roomServer
                )
                if (res.isSuccess) {
                    roomServer = relay.connectedServer()
                    republishSelf(code)
                    _connectionState.value = ConnectionState.CONNECTED
                    reconnectManager.onConnected()
                    // Retained meta/state/presence re-arrive via resubscribe → snapshot catches up.
                    maybeTriggerHost()
                    break
                }
            }
        }
    }

    private fun publishSnapshotLocked() {
        val meta = latestMeta ?: return
        val players = latestPlayers.values.sortedWith(compareBy({ it.joinedAt }, { it.id }))
        _roomState.value = RoomSnapshot(meta = meta, players = players, gameState = _gameState.value)
    }

    private suspend fun publishMeta(meta: RoomMetadata): Result<Unit> {
        synchronized(snapshotLock) {
            latestMeta = meta
            publishSnapshotLocked()
        }
        return relay.publish(
            MqttRelay.metaTopic(meta.roomId),
            relay.json.encodeToString(meta),
            retained = true
        )
    }

    private suspend fun publishState(state: GameState): Result<Unit> {
        val code = joinedCode ?: return Result.failure(RoomError.RoomNotFound)
        return relay.publish(
            MqttRelay.stateTopic(code),
            relay.json.encodeToString(state),
            retained = true
        )
    }

    private suspend fun publishEvent(event: com.neoludo.game.multiplayer.model.NetworkEvent): Result<Unit> {
        val code = joinedCode ?: return Result.failure(RoomError.RoomNotFound)
        return relay.publish(
            MqttRelay.eventsTopic(code),
            relay.json.encodeToString(event)
        )
    }

    private suspend fun publishChat(code: String, chat: ChatEvent): Result<Unit> =
        relay.publish(MqttRelay.chatTopic(code), relay.json.encodeToString(chat))

    private suspend fun postAction(roomId: String, action: NetworkAction): Result<Unit> =
        relay.publish(MqttRelay.actionsTopic(roomId), relay.json.encodeToString(action))
}
