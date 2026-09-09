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
import com.neoludo.game.multiplayer.sync.ActionDeduplicator
import com.neoludo.game.multiplayer.sync.AuthoritativeGameProcessor
import com.neoludo.game.multiplayer.sync.HostElectionManager
import com.neoludo.game.multiplayer.sync.ReconnectManager
import com.neoludo.game.multiplayer.sync.ProtocolSafety
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
import kotlinx.coroutines.withTimeoutOrNull
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
    internal val relay: MqttRelayDataSource = MqttRelayDataSource()
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
    // Seeded with epoch seconds (not 0): if the app restarts mid-game and
    // rejoins, its actions must NOT look older than pre-restart ones, or the
    // host's deduplicator drops every tap as stale.
    private val sequenceCounter = AtomicLong(ActionDeduplicator.restartSafeSeed())

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
    private var hostClaimJob: Job? = null
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
            // Never recycle a retained code. Old player/state retained topics
            // can outlive meta and resurrect a previous match under the same
            // short code. The code space is large enough to simply retry.
            if (existing == null) break
            code = MqttRelay.generateRoomCode()
            attempts++
        }
        // Final check on the chosen code.
        val existing = relay.awaitRetained(MqttRelay.metaTopic(code), 2000L)
        if (existing != null) {
            return@withContext Result.failure(RoomError.NetworkFailure("Room code collision, try again"))
        }
        currentRoomId = code
        currentAssignedColor = preferredColor
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
        // Register a Last-Will that preserves this seat's stable join/ready/host fields.
        val willConn = ensureConnectedWithWill(code, hostPresence)
        if (willConn.isFailure) return@withContext Result.failure(willConn.exceptionOrNull()!!)
        _connectionState.value = ConnectionState.CONNECTED

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
        val found = when (val lookup = relay.fetchRetainedFromAny(MqttRelay.metaTopic(code))) {
            is RelayLookup.Found -> lookup
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
        val rawMeta = found.payload
        // The probe may have found the room on a different broker network than
        // our initial socket. Move the live client to THAT network before
        // reading presence/state; otherwise room-full/color checks use the
        // wrong roster and two peers can silently split into different rooms.
        roomServer = found.server
        if (!MqttRelay.isSameNetwork(relay.connectedServer(), roomServer)) {
            relay.disconnect()
            val pinned = relay.connect(
                MqttRelayDataSource.newClientId(currentUid),
                preferServer = roomServer,
                restrictToPreferredNetwork = true
            )
            if (pinned.isFailure) {
                return@withContext Result.failure(
                    RoomError.NetworkFailure("Found the room, but could not connect to its relay. Retry in a moment.")
                )
            }
            roomServer = relay.connectedServer()
        }
        val meta = runCatching { relay.json.decodeFromString<RoomMetadata>(rawMeta) }.getOrNull()
            ?: return@withContext Result.failure(RoomError.RoomNotFound)
        if (!ProtocolSafety.isValidMeta(meta, code)) return@withContext Result.failure(RoomError.RoomNotFound)
        if (isReusableMeta(rawMeta)) {
            return@withContext Result.failure(RoomError.RoomNotFound)
        }
        currentRoomId = code

        // Seed players from retained presence docs. A room always holds its
        // host, so an empty result means our window missed the retained docs
        // (slow network) — wait out one more round rather than joining blind
        // (blind joins pick clashing colors and break game start).
        val now = System.currentTimeMillis()
        var known = fetchPlayers(code)
        if (known.isEmpty()) known = fetchPlayers(code, extraWaitMs = 2000L)
        // Cloned installs share one id: if OUR seat is heartbeating, another
        // live device owns it — mint a fresh id instead of stealing the seat.
        currentUid = JoinUidResolver.resolve(currentUid, known, now)
        val existing = known[currentUid]
        // A new player may only enter during the lobby. An existing logical
        // player is allowed to reclaim their retained seat during IN_GAME so
        // process death/background-kill can recover from the canonical state.
        if (meta.status != RoomStatus.LOBBY && existing == null) {
            return@withContext Result.failure(RoomError.GameAlreadyStarted)
        }
        val assigned: PlayerPresence
        if (existing != null) {
            // Rejoining our own seat.
            assigned = existing.copy(isConnected = true, lastSeen = now)
            currentAssignedColor = assigned.color
        } else {
            // Ghost seats (crashed/leaked presences) don't count: only live
            // seats fill the room or reserve colors.
            val live = known.values.filter { isSeatLive(it, now) }
            if (live.size >= meta.maxPlayers) {
                return@withContext Result.failure(RoomError.RoomFull)
            }
            val used = live.map { it.color }.toSet()
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
        val willConn = ensureConnectedWithWill(code, assigned)
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

        // Public MQTT brokers cannot atomically reserve the last room slot.
        // Stabilize the retained roster after announcing ourselves and let
        // every racing joiner make the same deterministic admission choice.
        // This guarantees that at most maxPlayers live human seats survive,
        // even when two phones join the fourth slot at the same instant.
        if (existing == null) {
            val settled = fetchPlayers(code)
            val admittedIds = selectAdmittedPlayerIds(settled.values, meta.maxPlayers, System.currentTimeMillis())
            if (currentUid !in admittedIds) {
                val rejected = assigned.copy(isConnected = false, lastSeen = System.currentTimeMillis())
                relay.publish(
                    MqttRelay.presenceTopic(code, currentUid),
                    relay.json.encodeToString(rejected),
                    retained = true
                )
                synchronized(snapshotLock) {
                    latestPlayers[currentUid] = rejected
                    publishSnapshotLocked()
                }
                release()
                return@withContext Result.failure(RoomError.RoomFull)
            }
            synchronized(snapshotLock) {
                latestPlayers.putAll(settled)
                latestPlayers[currentUid] = assigned
                publishSnapshotLocked()
            }
        }

        // Pull the latest retained state snapshot, if the match already has one.
        relay.awaitRetained(MqttRelay.stateTopic(code), 2500L)?.let { raw ->
            runCatching { relay.json.decodeFromString<GameState>(raw) }.getOrNull()?.let { game ->
                if (ProtocolSafety.isValidGameState(game, code, meta.hostId) && ProtocolSafety.isNewerState(_gameState.value, game)) {
                    _gameState.value = game
                    synchronized(snapshotLock) { publishSnapshotLocked() }
                }
            }
        }
        Result.success(Unit)
    }

    override suspend fun setReady(isReady: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val code = joinedCode ?: return@withContext Result.failure(RoomError.RoomNotFound)
        val snap = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        if (snap.meta.status != RoomStatus.LOBBY) return@withContext Result.failure(RoomError.GameAlreadyStarted)
        val self = synchronized(snapshotLock) { latestPlayers[currentUid] }
            ?: return@withContext Result.failure(RoomError.RoomNotFound)
        relay.publish(
            MqttRelay.presenceTopic(code, currentUid),
            relay.json.encodeToString(self.copy(isReady = isReady, lastSeen = System.currentTimeMillis())),
            retained = true
        ).map { Unit }
    }

    override suspend fun setFillBots(fillBots: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val snap = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        if (snap.meta.hostId != currentUid) return@withContext Result.failure(RoomError.NotHost)
        if (snap.meta.status != RoomStatus.LOBBY) return@withContext Result.failure(RoomError.GameAlreadyStarted)
        publishMeta(snap.meta.copy(fillBots = fillBots, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun startMatch(): Result<Unit> = withContext(Dispatchers.IO) {
        val snap = _roomState.value ?: return@withContext Result.failure(RoomError.RoomNotFound)
        if (snap.meta.hostId != currentUid) return@withContext Result.failure(RoomError.NotHost)
        if (snap.meta.status != RoomStatus.LOBBY) return@withContext Result.failure(RoomError.GameAlreadyStarted)
        val liveHumans = snap.players.filter { !it.isAi && it.isConnected }
        if (liveHumans.any { !it.isReady }) return@withContext Result.failure(RoomError.PlayersNotReady)
        if (!snap.meta.fillBots && liveHumans.size != snap.meta.maxPlayers) {
            return@withContext Result.failure(RoomError.NetworkFailure("Waiting for ${snap.meta.maxPlayers} connected players."))
        }
        if (snap.meta.fillBots && liveHumans.isEmpty()) {
            return@withContext Result.failure(RoomError.NetworkFailure("At least one connected player is required."))
        }
        // Never throw out of here (a throw from the tap handler crashes the
        // app): sanitize the roster and convert violations into lobby errors.
        val players = sanitizePlayersForStart(liveHumans, allowSingle = snap.meta.fillBots)
            ?: return@withContext Result.failure(
                RoomError.NetworkFailure(
                    "Need 2 to 4 players with different colors to start. " +
                        "Wait for everyone to appear below and retry."
                )
            )
        val initResult = runCatching { authoritativeProcessor.initializeGame(snap.meta, players) }.getOrElse {
            return@withContext Result.failure(
                RoomError.NetworkFailure("Could not start the game. Please retry.")
            )
        }
        // Publish back any host-remapped colors so plates match the board.
        val now = System.currentTimeMillis()
        val originalColors = snap.players.associate { it.id to it.color }
        players.forEach { p ->
            if (originalColors[p.id] != null && originalColors[p.id] != p.color) {
                relay.publish(
                    MqttRelay.presenceTopic(snap.meta.roomId, p.id),
                    relay.json.encodeToString(p.copy(lastSeen = now)),
                    retained = true
                )
            }
        }
        _gameState.value = initResult.updatedState
        // State first, lifecycle meta second. If a relay hiccups between the
        // two, peers stay in the lobby rather than entering IN_GAME without a
        // recoverable canonical state snapshot.
        val stateResult = publishState(initResult.updatedState)
        if (stateResult.isFailure) return@withContext stateResult
        val metaResult = publishMeta(initResult.updatedMeta ?: snap.meta)
        if (metaResult.isFailure) return@withContext metaResult
        initResult.events.forEach { publishEvent(it) }
        Result.success(Unit)
    }

    // ---------- game actions ----------

    override suspend fun rollDice(): Result<Unit> = withContext(Dispatchers.IO) {
        postGameActionWithRetry { snap, state ->
            NetworkAction(
                actionId = UUID.randomUUID().toString(),
                sequence = sequenceCounter.incrementAndGet(),
                type = ActionType.ROLL_DICE,
                playerId = state.activePlayer.id,
                payload = "",
                expectedVersion = state.version,
                expectedHostEpoch = snap.meta.hostEpoch,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    override suspend fun movePiece(pieceId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        postGameActionWithRetry { snap, state ->
            NetworkAction(
                actionId = UUID.randomUUID().toString(),
                sequence = sequenceCounter.incrementAndGet(),
                type = ActionType.MOVE_PIECE,
                playerId = state.activePlayer.id,
                payload = pieceId.toString(),
                expectedVersion = state.version,
                expectedHostEpoch = snap.meta.hostEpoch,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private suspend fun postGameActionWithRetry(
        build: (RoomSnapshot, GameState) -> NetworkAction
    ): Result<Unit> {
        var lastErr: Throwable? = null
        repeat(2) { attempt ->
            val snap = _roomState.value ?: return Result.failure(RoomError.RoomNotFound)
            val state = _gameState.value ?: return Result.failure(
                RoomError.NetworkFailure("Still loading the game — wait a moment and retry.")
            )
            if (!isLocalTurn(snap, state)) return Result.failure(RoomError.NotYourTurn)
            val action = build(snap, state)
            val pub = postAction(snap.meta.roomId, action)
            if (pub.isFailure) {
                lastErr = pub.exceptionOrNull(); return@repeat
            }
            if (snap.meta.hostId == currentUid) {
                val local = authoritativeProcessor.processAction(action, state, snap.meta)
                if (local != null) {
                    _gameState.value = local.updatedState
                    publishState(local.updatedState)
                    local.updatedMeta?.let { publishMeta(it) }
                    local.events.forEach { publishEvent(it) }
                    return Result.success(Unit)
                }
                when (authoritativeProcessor.lastRejection) {
                    is AuthoritativeGameProcessor.Rejection.StaleVersion,
                    is AuthoritativeGameProcessor.Rejection.StaleEpoch,
                    is AuthoritativeGameProcessor.Rejection.AuthorityMismatch -> {
                        if (attempt == 0) { delay(220); return@repeat }
                        return Result.failure(RoomError.StaleAction)
                    }
                    is AuthoritativeGameProcessor.Rejection.WrongPhase ->
                        return Result.failure(RoomError.NetworkFailure("Not the right moment for that action."))
                    is AuthoritativeGameProcessor.Rejection.IllegalMove ->
                        return Result.failure(RoomError.IllegalMove)
                    is AuthoritativeGameProcessor.Rejection.WrongPlayer ->
                        return Result.failure(RoomError.NotYourTurn)
                    else -> {}
                }
            }
            val waitVersion = action.expectedVersion
            val observed = withTimeoutOrNull(2200L) {
                var cur: GameState? = _gameState.value
                while (cur == null || cur.version == waitVersion) {
                    delay(90)
                    cur = _gameState.value
                    if (cur != null && cur.version != waitVersion) break
                }
                cur
            }
            if (observed != null && observed.version != waitVersion) return Result.success(Unit)
            val cur2 = _gameState.value
            if (cur2 != null && cur2.version != waitVersion) return Result.success(Unit)
            if (authoritativeProcessor.lastRejection is AuthoritativeGameProcessor.Rejection.StaleVersion ||
                authoritativeProcessor.lastRejection is AuthoritativeGameProcessor.Rejection.StaleEpoch) {
                if (attempt == 0) { delay(220); return@repeat }
                return Result.failure(RoomError.StaleAction)
            }
            if (attempt == 0) { delay(260); return@repeat }
        }
        return lastErr?.let { Result.failure(it) } ?: Result.failure(RoomError.NetworkFailure("Action not confirmed — will retry when the board syncs."))
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
            val now = System.currentTimeMillis()
            val (meta, players, self) = synchronized(snapshotLock) {
                Triple(latestMeta, latestPlayers.values.toList(), latestPlayers[currentUid])
            }
            // Leave a durable tombstone when the host is the last live human.
            // This prevents stale retained player/state records from making an
            // abandoned room look joinable later, while keeping the short code
            // permanently collision-safe on public brokers.
            val otherLiveHumans = players.filter {
                it.id != currentUid && !it.isAi && isSeatLive(it, now)
            }
            if (meta != null && meta.hostId == currentUid && otherLiveHumans.isEmpty() &&
                meta.status != RoomStatus.COMPLETED && meta.status != RoomStatus.ABANDONED) {
                publishMeta(meta.copy(status = RoomStatus.ABANDONED, updatedAt = now))
                relay.clearRetained(MqttRelay.stateTopic(code))
            }
            if (self != null) {
                relay.publish(
                    MqttRelay.presenceTopic(code, currentUid),
                    relay.json.encodeToString(self.copy(isConnected = false, lastSeen = now)),
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
            preferServer = roomServer,
            restrictToPreferredNetwork = roomServer != null
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
        val codeToLeave = joinedCode
        val selfToLeave = synchronized(snapshotLock) { latestPlayers[currentUid] }
        botTurnJob?.cancel()
        timeoutJob?.cancel()
        heartbeatJob?.cancel()
        retryJob?.cancel()
        hostClaimJob?.cancel()
        listenerJobs.forEach { it.cancel() }
        listenerJobs = emptyList()
        joinedCode = null
        roomServer = null
        // Do not launch cleanup into the scope cancelled on the next line.
        // On a clean MQTT disconnect the broker does NOT publish our Last-Will,
        // so explicitly stamp the retained seat offline first.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (codeToLeave != null && selfToLeave != null && relay.isConnected()) {
                relay.publish(
                    MqttRelay.presenceTopic(codeToLeave, currentUid),
                    relay.json.encodeToString(
                        selfToLeave.copy(isConnected = false, lastSeen = System.currentTimeMillis())
                    ),
                    retained = true
                )
            }
            relay.disconnect()
        }
        scope.cancel()
    }

    // ---------- internals ----------

    private fun isLocalTurn(snap: RoomSnapshot, state: GameState): Boolean {
        if (state.activePlayer.id == currentUid) return true
        // Hosts cover bots / disconnected / missing seats — never a connected
        // human's turn (otherwise the host could play the whole game alone).
        if (snap.meta.hostId != currentUid) return false
        val active = snap.players.find { it.id == state.activePlayer.id }
        return active == null || active.isAi || !active.isConnected
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
        presence: PlayerPresence? = null,
        preferServer: String? = roomServer
    ): Result<Unit> {
        relay.disconnect()
        val id = MqttRelayDataSource.newClientId(currentUid.ifBlank { localPlayerId })
        val will = willFor(code, presence)
        val res = relay.connect(
            id,
            will.first,
            will.second,
            preferServer = preferServer,
            restrictToPreferredNetwork = preferServer != null
        )
        if (res.isSuccess) roomServer = relay.connectedServer()
        return res
    }

    private fun willFor(code: String, presence: PlayerPresence? = null): Pair<String, String> {
        val stable = presence ?: synchronized(snapshotLock) { latestPlayers[currentUid] } ?: PlayerPresence(
            id = currentUid, name = localPlayerName, avatarId = localAvatarId, color = currentAssignedColor
        )
        val offline = stable.copy(isConnected = false, lastSeen = System.currentTimeMillis())
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

    private suspend fun fetchPlayers(code: String, extraWaitMs: Long = 0L): Map<String, PlayerPresence> {
        // Retained presence docs arrive on wildcard subscribe; collect briefly.
        val found = mutableMapOf<String, PlayerPresence>()
        val handler: (String, String) -> Unit = { topic, payload ->
            val uid = MqttRelay.uidFromPresenceTopic(topic, code)
            if (uid != null) {
                runCatching { relay.json.decodeFromString<PlayerPresence>(payload) }.getOrNull()?.let { presence ->
                    if (ProtocolSafety.isValidPresence(presence, uid)) {
                        synchronized(found) { found[uid] = presence }
                    }
                }
            }
        }
        relay.subscribePrefix(MqttRelay.playersPrefix(code), handler = handler)
        try {
            delay(2000L + extraWaitMs)
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
                if (!ProtocolSafety.isValidMeta(meta, code)) return@let
                val accepted = synchronized(snapshotLock) {
                    val old = latestMeta
                    if (!ProtocolSafety.shouldAcceptMeta(old, meta)) false else {
                        latestMeta = meta
                        publishSnapshotLocked()
                        true
                    }
                }
                if (accepted) {
                    maybeClaimHost()
                    maybeTriggerHost()
                }
            }
        }.launchIn(scope)

        jobs += relay.observePrefix(MqttRelay.playersPrefix(code)).onEach { (topic, payload) ->
            val uid = MqttRelay.uidFromPresenceTopic(topic, code) ?: return@onEach
            runCatching { relay.json.decodeFromString<PlayerPresence>(payload) }.getOrNull()?.let { presence ->
                if (!ProtocolSafety.isValidPresence(presence, uid)) return@let
                synchronized(snapshotLock) {
                    latestPlayers[uid] = presence
                    publishSnapshotLocked()
                }
                maybeClaimHost()
            }
        }.launchIn(scope)

        jobs += relay.observeExact(MqttRelay.stateTopic(code)).onEach { raw ->
            runCatching { relay.json.decodeFromString<GameState>(raw) }.getOrNull()?.let { remote ->
                val acceptedMeta = synchronized(snapshotLock) { latestMeta } ?: return@let
                if (!ProtocolSafety.isValidGameState(remote, code, acceptedMeta.hostId)) return@let
                if (remote.authorityEpoch != acceptedMeta.hostEpoch) return@let
                if (ProtocolSafety.isNewerState(_gameState.value, remote)) {
                    _gameState.value = remote
                    synchronized(snapshotLock) { publishSnapshotLocked() }
                    maybeClaimHost()
                    maybeTriggerHost()
                }
            }
        }.launchIn(scope)

        jobs += relay.observeExact(MqttRelay.actionsTopic(code)).onEach { raw ->
            val action = runCatching { relay.json.decodeFromString<NetworkAction>(raw) }.getOrNull()
                ?: return@onEach
            val snap = _roomState.value ?: return@onEach
            val game = _gameState.value ?: snap.gameState ?: return@onEach
            if (snap.meta.hostId != currentUid) return@onEach
            if (game.authorityEpoch != snap.meta.hostEpoch || game.authorityHostId != snap.meta.hostId) return@onEach
            val result = authoritativeProcessor.processAction(action, game, snap.meta) ?: return@onEach
            _gameState.value = result.updatedState
            if (publishState(result.updatedState).isFailure) return@onEach
            result.updatedMeta?.let { if (publishMeta(it).isFailure) return@onEach }
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
                    continue
                }
                // Hosts refresh the room meta so late joiners find the room
                // even if the broker restarted and dropped retained messages.
                val meta = synchronized(snapshotLock) { latestMeta }
                if (meta != null && meta.hostId == currentUid && meta.status == RoomStatus.LOBBY) {
                    relay.publish(
                        MqttRelay.metaTopic(c),
                        relay.json.encodeToString(meta.copy(updatedAt = System.currentTimeMillis())),
                        retained = true
                    )
                }
                // Self-heal silent subscription loss: re-confirm every topic
                // each beat (cheap, idempotent); recycle the link if refused.
                if (!relay.refreshSubscriptions(maxAttempts = 2)) {
                    handleLinkFailure()
                }
            }
        }
    }

    private fun maybeTriggerHost() {
        val snap = _roomState.value ?: return
        val game = _gameState.value ?: snap.gameState ?: return
        if (snap.meta.hostId != currentUid) return
        if (snap.meta.status != RoomStatus.IN_GAME) return
        if (game.authorityEpoch != snap.meta.hostEpoch || game.authorityHostId != snap.meta.hostId) return
        triggerHostEvaluation(snap.meta, game)
    }

    /** Persist deterministic host election so every client agrees on one authority. */
    private fun maybeClaimHost() {
        val snap = _roomState.value ?: return
        if (snap.meta.status == RoomStatus.COMPLETED || snap.meta.status == RoomStatus.ABANDONED) return
        if (snap.meta.hostId == currentUid) {
            adoptAuthorityIfNeeded(snap.meta)
            return
        }
        if (hostElectionManager.determineCurrentHost(snap.meta, snap.players) != currentUid) return
        if (hostClaimJob?.isActive == true) return
        val observedEpoch = snap.meta.hostEpoch
        hostClaimJob = scope.launch {
            // Give retained presence updates a short convergence window before
            // publishing a new authority generation on a non-transactional relay.
            delay(1_200L)
            val latest = _roomState.value ?: return@launch
            if (latest.meta.hostEpoch != observedEpoch || latest.meta.hostId == currentUid) return@launch
            if (hostElectionManager.determineCurrentHost(latest.meta, latest.players) != currentUid) return@launch
            val claimed = latest.meta.copy(
                hostId = currentUid,
                hostEpoch = observedEpoch + 1L,
                updatedAt = System.currentTimeMillis()
            )
            if (publishMeta(claimed).isSuccess) {
                val self = synchronized(snapshotLock) { latestPlayers[currentUid] }
                if (self != null) {
                    relay.publish(
                        MqttRelay.presenceTopic(claimed.roomId, currentUid),
                        relay.json.encodeToString(self.copy(isHost = true, lastSeen = System.currentTimeMillis())),
                        retained = true
                    )
                }
                adoptAuthorityIfNeeded(claimed)
            }
        }
    }

    private fun adoptAuthorityIfNeeded(meta: RoomMetadata) {
        val state = _gameState.value ?: return
        if (state.authorityEpoch > meta.hostEpoch ||
            (state.authorityEpoch == meta.hostEpoch && state.authorityHostId == meta.hostId)) return
        if (meta.hostId != currentUid) return
        scope.launch {
            val latest = _gameState.value ?: return@launch
            if (latest.authorityEpoch > meta.hostEpoch ||
                (latest.authorityEpoch == meta.hostEpoch && latest.authorityHostId == meta.hostId)) return@launch
            val adopted = latest.copy(authorityEpoch = meta.hostEpoch, authorityHostId = meta.hostId)
            _gameState.value = adopted
            synchronized(snapshotLock) { publishSnapshotLocked() }
            if (publishState(adopted).isSuccess) maybeTriggerHost()
        }
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
                                expectedVersion = state.version,
                                expectedHostEpoch = meta.hostEpoch,
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
                latestState.version == state.version &&
                latestState.authorityEpoch == latestSnap.meta.hostEpoch &&
                latestState.authorityHostId == latestSnap.meta.hostId && !latestState.isGameOver
            ) {
                log("Turn deadline elapsed for ${active.name}, executing timeout step")
                val result = authoritativeProcessor.processTimeout(latestState, latestSnap.meta)
                if (result != null) {
                    _gameState.value = result.updatedState
                    if (publishState(result.updatedState).isFailure) return@launch
                    result.updatedMeta?.let { if (publishMeta(it).isFailure) return@launch }
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
                    preferServer = roomServer,
                    restrictToPreferredNetwork = roomServer != null
                )
                if (res.isSuccess) {
                    roomServer = relay.connectedServer()
                    republishSelf(code)
                    // If we are canonical host, restore retained state too.
                    // Public brokers can restart and lose retained snapshots;
                    // the live host still owns the authoritative in-memory copy.
                    val snap = _roomState.value
                    val state = _gameState.value
                    if (snap?.meta?.hostId == currentUid && state != null &&
                        state.authorityEpoch == snap.meta.hostEpoch && state.authorityHostId == snap.meta.hostId) {
                        publishState(state)
                    }
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
        val visible = if (meta.status == RoomStatus.LOBBY) {
            val admitted = selectAdmittedPlayerIds(latestPlayers.values, meta.maxPlayers)
            latestPlayers.values.filter { isSeatLive(it) && it.id in admitted }
        } else {
            latestPlayers.values
        }
        val players = visible.sortedWith(compareBy({ it.joinedAt }, { it.id }))
        _roomState.value = RoomSnapshot(meta = meta, players = players, gameState = _gameState.value)
    }

    private suspend fun publishMeta(meta: RoomMetadata): Result<Unit> {
        val result = relay.publish(
            MqttRelay.metaTopic(meta.roomId),
            relay.json.encodeToString(meta),
            retained = true
        )
        if (result.isSuccess) {
            synchronized(snapshotLock) {
                latestMeta = meta
                publishSnapshotLocked()
            }
        } else {
            handleLinkFailure()
        }
        return result
    }

    private suspend fun publishState(state: GameState): Result<Unit> {
        val code = joinedCode ?: return Result.failure(RoomError.RoomNotFound)
        val result = relay.publish(
            MqttRelay.stateTopic(code),
            relay.json.encodeToString(state),
            retained = true
        )
        if (result.isFailure) handleLinkFailure()
        return result
    }

    private suspend fun publishEvent(event: com.neoludo.game.multiplayer.model.NetworkEvent): Result<Unit> {
        val code = joinedCode ?: return Result.failure(RoomError.RoomNotFound)
        return relay.publish(
            MqttRelay.eventsTopic(code),
            relay.json.encodeToString(event)
        )
    }

    private suspend fun publishChat(code: String, chat: ChatEvent): Result<Unit> {
        val result = relay.publish(MqttRelay.chatTopic(code), relay.json.encodeToString(chat))
        if (result.isFailure) handleLinkFailure()
        return result
    }

    private suspend fun postAction(roomId: String, action: NetworkAction): Result<Unit> {
        val res = relay.publish(MqttRelay.actionsTopic(roomId), relay.json.encodeToString(action))
        if (res.isFailure) handleLinkFailure()
        return res
    }
}

/**
 * A seat counts as occupied when connected or heartbeating recently.
 * Stale offline presences are broker litter (crashes, killed apps) — they
 * must neither fill the room nor reserve colors for new joiners.
 */
internal const val SEAT_LIVE_WINDOW_MS = 60_000L

internal fun isSeatLive(p: PlayerPresence, now: Long = System.currentTimeMillis()): Boolean =
    p.isConnected || (now - p.lastSeen) < SEAT_LIVE_WINDOW_MS


/**
 * Deterministic admission for relay rooms. MQTT has no compare-and-set
 * transaction, so simultaneous joins converge by ordering live human seats
 * by their immutable join timestamp and id and keeping only the first N.
 */
internal fun selectAdmittedPlayerIds(
    players: Collection<PlayerPresence>,
    maxPlayers: Int,
    now: Long = System.currentTimeMillis()
): Set<String> = players
    .asSequence()
    .filter { !it.isAi && isSeatLive(it, now) }
    .distinctBy { it.id }
    .sortedWith(compareBy<PlayerPresence>({ it.joinedAt }, { it.id }))
    .take(maxPlayers.coerceIn(1, 4))
    .map { it.id }
    .toSet()

/**
 * Host cover rule: the host may act for bots, disconnected players and
 * seats missing from presence — but never for a connected human's turn.
 */
internal fun canHostCoverTurn(isHost: Boolean, activePresence: PlayerPresence?): Boolean {
    if (!isHost) return false
    return activePresence == null || activePresence.isAi || !activePresence.isConnected
}

/**
 * Roster guard for game start. Slow networks can deliver a skewed presence
 * set (e.g. the host's seat missed during join, so both phones picked the
 * same color) — and `createInitialState` answers that with `require`
 * crashes. Keep join order, remap duplicate colors deterministically, and
 * return null when no valid game is possible (caller shows a lobby error
 * instead of crashing).
 */
internal fun sanitizePlayersForStart(players: List<PlayerPresence>, allowSingle: Boolean = false): List<PlayerPresence>? {
    val distinct = players.distinctBy { it.id }
    val minPlayers = if (allowSingle) 1 else 2
    if (distinct.size < minPlayers || distinct.size > 4) return null
    val allColors = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
    val used = mutableSetOf<PlayerColor>()
    val free = ArrayDeque(allColors.filter { c -> distinct.none { it.color == c } })
    return distinct.map { p ->
        if (used.add(p.color)) p
        else {
            val replacement = free.removeFirstOrNull() ?: return null
            used.add(replacement)
            p.copy(color = replacement)
        }
    }
}
