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

    sealed class Rejection(val reason: String) {
        data object StaleVersion : Rejection("stale_version")
        data object StaleEpoch : Rejection("stale_epoch")
        data object WrongPlayer : Rejection("wrong_player")
        data object WrongPhase : Rejection("wrong_phase")
        data object IllegalMove : Rejection("illegal_move")
        data object Duplicate : Rejection("duplicate")
        data object GameOver : Rejection("game_over")
        data object AuthorityMismatch : Rejection("authority_mismatch")
        data object HostMissing : Rejection("host_missing")
        data object InvalidPayload : Rejection("invalid_payload")
    }

    var lastRejection: Rejection? = null
        private set

    fun clearRejection() { lastRejection = null }

    fun processAction(
        action: NetworkAction,
        currentState: GameState,
        currentMeta: RoomMetadata
    ): ProcessedActionResult? {
        lastRejection = null
        if (deduplicator.isDuplicateOrStale(action.actionId, action.sequence, action.playerId)) {
            lastRejection = Rejection.Duplicate; return null
        }

        if (currentState.isGameOver) { lastRejection = Rejection.GameOver; return null }

        if (action.expectedVersion < 0L || action.expectedHostEpoch < 1L) { lastRejection = Rejection.HostMissing; return null }
        if (action.expectedVersion != currentState.version) { lastRejection = Rejection.StaleVersion; return null }
        if (action.expectedHostEpoch != currentMeta.hostEpoch) { lastRejection = Rejection.StaleEpoch; return null }
        if (currentState.authorityEpoch != currentMeta.hostEpoch || currentState.authorityHostId != currentMeta.hostId) {
            lastRejection = Rejection.AuthorityMismatch; return null
        }

        action.payload.substringAfter("v=", "").substringBefore(";").toLongOrNull()?.let { expected ->
            if (expected != currentState.version) { lastRejection = Rejection.StaleVersion; return null }
        }

        val activePlayer = currentState.activePlayer
        val now = System.currentTimeMillis()

        return when (action.type) {
            ActionType.ROLL_DICE -> {
                if (action.playerId != activePlayer.id) { lastRejection = Rejection.WrongPlayer; return null }
                if (currentState.turnPhase != TurnPhase.WAITING_FOR_ROLL) { lastRejection = Rejection.WrongPhase; return null }

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
                if (action.playerId != activePlayer.id) { lastRejection = Rejection.WrongPlayer; return null }
                if (currentState.turnPhase != TurnPhase.WAITING_FOR_MOVE) { lastRejection = Rejection.WrongPhase; return null }

                val pieceId = action.payload.substringBefore(";").toIntOrNull()
                    ?: run { lastRejection = Rejection.InvalidPayload; return null }
                if (MoveValidator.getLegalMoves(activePlayer, currentState.diceState.value, currentState.players)
                        .none { it.piece.id == pieceId }) {
                    lastRejection = Rejection.IllegalMove; return null
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
                if (action.playerId != activePlayer.id) { lastRejection = Rejection.WrongPlayer; return null }
                if (currentState.turnPhase != TurnPhase.WAITING_FOR_ROLL) { lastRejection = Rejection.WrongPhase; return null }
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
