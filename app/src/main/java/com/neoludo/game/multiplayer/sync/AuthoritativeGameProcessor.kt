package com.neoludo.game.multiplayer.sync

import com.neoludo.game.engine.InitialPlayerConfig
import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.model.GameEngineEvent
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.engine.rules.MoveValidator
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.NetworkEvent
import com.neoludo.game.multiplayer.model.NetworkEventType
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomStatus
import java.util.UUID

data class ProcessedActionResult(
    val updatedState: GameState,
    val updatedMeta: RoomMetadata? = null,
    val events: List<NetworkEvent> = emptyList()
)

class AuthoritativeGameProcessor(
    private val deduplicator: ActionDeduplicator = ActionDeduplicator(),
    /**
     * Prod must be false: clients send payload="" and the host rolls server-side random.
     * Tests that exercise deterministic rolls construct with true.
     */
    private val allowForcedDice: Boolean = false
) {

    fun initializeGame(
        meta: RoomMetadata,
        players: List<PlayerPresence>
    ): ProcessedActionResult {
        val playerConfigs = if (meta.fillBots && players.size < meta.maxPlayers) {
            val allColors = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
            val usedColors = players.map { it.color }.toSet()
            val missingCount = meta.maxPlayers - players.size
            val availableColors = allColors.filter { it !in usedColors }.take(missingCount)

            val humanConfigs = players.map {
                InitialPlayerConfig(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    avatarId = it.avatarId,
                    isBot = it.isAi
                )
            }

            val botConfigs = availableColors.map { col ->
                InitialPlayerConfig(
                    // Stable per-color id — survives restarts/migrations without colliding.
                    id = "bot_${col.name.lowercase()}",
                    name = "Bot ${col.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    color = col,
                    avatarId = col.ordinal + 2,
                    isBot = true
                )
            }

            humanConfigs + botConfigs
        } else {
            players.map {
                InitialPlayerConfig(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    avatarId = it.avatarId,
                    isBot = it.isAi
                )
            }
        }

        val initialState = LudoGameEngine.createInitialState(
            gameId = meta.roomId,
            playerConfigs = playerConfigs,
            ruleSet = meta.ruleSet,
            authorityEpoch = meta.hostEpoch,
            authorityHostId = meta.hostId
        )

        val now = System.currentTimeMillis()
        val deadline = now + (meta.ruleSet.turnTimerSeconds * 1000L)

        val updatedMeta = meta.copy(
            status = RoomStatus.IN_GAME,
            updatedAt = now,
            turnStartedAt = now,
            turnDeadline = deadline
        )

        val startEvent = NetworkEvent(
            eventId = UUID.randomUUID().toString(),
            type = NetworkEventType.GAME_STARTED,
            playerId = meta.hostId,
            payload = meta.roomId,
            version = initialState.version,
            timestamp = now
        )

        return ProcessedActionResult(
            updatedState = initialState,
            updatedMeta = updatedMeta,
            events = listOf(startEvent)
        )
    }

    fun processAction(
        action: NetworkAction,
        currentState: GameState,
        currentMeta: RoomMetadata
    ): ProcessedActionResult? {
        if (deduplicator.isDuplicateOrStale(action.actionId, action.sequence, action.playerId)) {
            return null
        }

        if (currentState.isGameOver) {
            return null
        }

        // Every production action MUST be fenced to both the game revision and
        // authority generation.  Old 1.9.x frames decode with -1 defaults, but
        // accepting those after host migration would let a Firebase child replay
        // (or a delayed MQTT packet) mutate a later turn.
        if (action.expectedVersion < 0L || action.expectedHostEpoch < 1L) return null
        if (action.expectedVersion != currentState.version) return null
        if (action.expectedHostEpoch != currentMeta.hostEpoch) return null
        if (currentState.authorityEpoch != currentMeta.hostEpoch) return null
        if (currentState.authorityHostId != currentMeta.hostId) return null

        // Backward compatibility with 1.9.x PASS_TURN messages which encoded v= in payload.
        action.payload.substringAfter("v=", "").substringBefore(";").toLongOrNull()?.let { expected ->
            if (expected != currentState.version) return null
        }

        val activePlayer = currentState.activePlayer
        val now = System.currentTimeMillis()

        return when (action.type) {
            ActionType.ROLL_DICE -> {
                // The action always names the canonical active seat. Host proxying for a
                // bot/disconnected seat still uses that seat id, never the host id.
                if (action.playerId != activePlayer.id) {
                    return null
                }
                if (currentState.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
                    return null
                }

                // Forged-dice fix: normal clients send payload="" and the host rolls random.
                // Numeric payloads are only honored when allowForcedDice=true (tests).
                val forcedVal = if (allowForcedDice) {
                    action.payload.substringBefore(";").toIntOrNull()?.takeIf { it in 1..6 }
                } else null
                val nextState = LudoGameEngine.rollDice(currentState, forcedValue = forcedVal)
                if (nextState.version == currentState.version) return null
                deduplicator.markProcessed(action.actionId, action.sequence, action.playerId)

                val deadline = now + (currentMeta.ruleSet.turnTimerSeconds * 1000L)
                val updatedMeta = currentMeta.copy(
                    updatedAt = now,
                    turnStartedAt = now,
                    turnDeadline = deadline
                )

                val events = mutableListOf<NetworkEvent>()
                events.add(
                    NetworkEvent(
                        eventId = UUID.randomUUID().toString(),
                        type = NetworkEventType.DICE_ROLLED,
                        playerId = activePlayer.id,
                        payload = nextState.diceState.value.toString(),
                        version = nextState.version,
                        timestamp = now
                    )
                )

                when (val ev = nextState.lastEvent) {
                    is GameEngineEvent.TurnForfeited3xSix -> {
                        events.add(
                            NetworkEvent(
                                eventId = UUID.randomUUID().toString(),
                                type = NetworkEventType.TURN_FORFEITED,
                                playerId = activePlayer.id,
                                payload = "3x_SIX",
                                version = nextState.version,
                                timestamp = now
                            )
                        )
                    }
                    is GameEngineEvent.TurnPassedNoMoves -> {
                        events.add(
                            NetworkEvent(
                                eventId = UUID.randomUUID().toString(),
                                type = NetworkEventType.TURN_FORFEITED,
                                playerId = activePlayer.id,
                                payload = "NO_MOVES",
                                version = nextState.version,
                                timestamp = now
                            )
                        )
                    }
                    else -> Unit
                }

                ProcessedActionResult(
                    updatedState = nextState,
                    updatedMeta = updatedMeta,
                    events = events
                )
            }

            ActionType.MOVE_PIECE -> {
                if (action.playerId != activePlayer.id) {
                    return null
                }
                if (currentState.turnPhase != TurnPhase.WAITING_FOR_MOVE) {
                    return null
                }

                val pieceId = action.payload.substringBefore(";").toIntOrNull() ?: return null
                // Validate against the full canonical rule calculation, including exact-home
                // and capture/safe-cell semantics, not only distance arithmetic.
                if (MoveValidator.getLegalMoves(activePlayer, currentState.diceState.value, currentState.players)
                        .none { it.piece.id == pieceId }) {
                    return null
                }

                val nextState = LudoGameEngine.movePiece(currentState, pieceId)
                if (nextState.version == currentState.version) return null
                deduplicator.markProcessed(action.actionId, action.sequence, action.playerId)

                val deadline = now + (currentMeta.ruleSet.turnTimerSeconds * 1000L)
                val updatedMeta = currentMeta.copy(
                    status = if (nextState.isGameOver) RoomStatus.COMPLETED else currentMeta.status,
                    updatedAt = now,
                    turnStartedAt = now,
                    turnDeadline = deadline
                )

                val events = mutableListOf<NetworkEvent>()
                events.add(
                    NetworkEvent(
                        eventId = UUID.randomUUID().toString(),
                        type = NetworkEventType.PIECE_MOVED,
                        playerId = activePlayer.id,
                        payload = pieceId.toString(),
                        version = nextState.version,
                        timestamp = now
                    )
                )

                when (val ev = nextState.lastEvent) {
                    is GameEngineEvent.PieceCaptured -> {
                        events.add(
                            NetworkEvent(
                                eventId = UUID.randomUUID().toString(),
                                type = NetworkEventType.PIECE_CAPTURED,
                                playerId = activePlayer.id,
                                payload = "${ev.victim}_${ev.victimPieceId}",
                                version = nextState.version,
                                timestamp = now
                            )
                        )
                    }
                    is GameEngineEvent.ExtraTurnGranted -> {
                        events.add(
                            NetworkEvent(
                                eventId = UUID.randomUUID().toString(),
                                type = NetworkEventType.BONUS_TURN,
                                playerId = activePlayer.id,
                                payload = ev.reason,
                                version = nextState.version,
                                timestamp = now
                            )
                        )
                    }
                    is GameEngineEvent.GameOver -> {
                        events.add(
                            NetworkEvent(
                                eventId = UUID.randomUUID().toString(),
                                type = NetworkEventType.GAME_OVER,
                                playerId = activePlayer.id,
                                payload = ev.winner.name,
                                version = nextState.version,
                                timestamp = now
                            )
                        )
                    }
                    else -> Unit
                }

                ProcessedActionResult(
                    updatedState = nextState,
                    updatedMeta = updatedMeta,
                    events = events
                )
            }

            ActionType.PASS_TURN -> {
                if (action.playerId != activePlayer.id) {
                    return null
                }
                // Phase guard: PASS_TURN is only valid while waiting for a roll.
                // Prevents replayed PASS_TURN (full-history re-delivery on host migration)
                // from advancing the turn a second time mid-move.
                if (currentState.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
                    return null
                }
                val nextState = LudoGameEngine.passTurn(currentState)
                // passTurn is a no-op guard internally too; only mark when version advanced.
                if (nextState.version == currentState.version) return null
                deduplicator.markProcessed(action.actionId, action.sequence, action.playerId)

                val deadline = now + (currentMeta.ruleSet.turnTimerSeconds * 1000L)
                val updatedMeta = currentMeta.copy(
                    updatedAt = now,
                    turnStartedAt = now,
                    turnDeadline = deadline
                )

                ProcessedActionResult(
                    updatedState = nextState,
                    updatedMeta = updatedMeta,
                    events = emptyList()
                )
            }

            else -> null
        }
    }

    fun processTimeout(
        currentState: GameState,
        currentMeta: RoomMetadata
    ): ProcessedActionResult? {
        if (currentState.isGameOver) return null
        if (currentState.authorityEpoch != currentMeta.hostEpoch) return null
        if (currentState.authorityHostId != currentMeta.hostId) return null
        val nextState = DisconnectAiProxy.executeProxyStep(currentState)
        if (nextState.version == currentState.version) return null
        val now = System.currentTimeMillis()
        val deadline = now + (currentMeta.ruleSet.turnTimerSeconds * 1000L)

        val updatedMeta = currentMeta.copy(
            status = if (nextState.isGameOver) RoomStatus.COMPLETED else currentMeta.status,
            updatedAt = now,
            turnStartedAt = now,
            turnDeadline = deadline
        )

        return ProcessedActionResult(
            updatedState = nextState,
            updatedMeta = updatedMeta,
            events = listOf(
                NetworkEvent(
                    eventId = UUID.randomUUID().toString(),
                    type = NetworkEventType.AI_TAKEOVER,
                    playerId = currentState.activePlayer.id,
                    payload = "TIMEOUT_STEP",
                    version = nextState.version,
                    timestamp = now
                )
            )
        )
    }

    fun reset() {
        deduplicator.reset()
    }
}
