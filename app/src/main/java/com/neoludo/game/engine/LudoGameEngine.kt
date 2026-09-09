package com.neoludo.game.engine

import com.neoludo.game.engine.coordinate.BoardCoordinates
import com.neoludo.game.engine.model.DiceState
import com.neoludo.game.engine.model.GameEngineEvent
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.MoveRecord
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.engine.rules.MoveValidator
import kotlin.random.Random

data class InitialPlayerConfig(
    val id: String,
    val name: String,
    val color: PlayerColor,
    val avatarId: Int = 1,
    val isBot: Boolean = false
)

object LudoGameEngine {

    fun createInitialState(
        gameId: String,
        playerConfigs: List<InitialPlayerConfig>,
        ruleSet: LudoRuleSet = LudoRuleSet(),
        authorityEpoch: Long = 1L,
        authorityHostId: String = ""
    ): GameState {
        require(playerConfigs.size in 2..4) { "Player count must be between 2 and 4" }
        require(playerConfigs.map { it.id }.toSet().size == playerConfigs.size) {
            "Duplicate player ids: ${playerConfigs.map { it.id }}"
        }
        require(playerConfigs.map { it.color }.toSet().size == playerConfigs.size) {
            "Duplicate player colors: ${playerConfigs.map { it.color }}"
        }

        val players = playerConfigs.map { cfg ->
            PlayerState(
                id = cfg.id,
                name = cfg.name,
                color = cfg.color,
                avatarId = cfg.avatarId,
                pieces = (0..3).map { Piece(it, cfg.color, PiecePosition.Yard(it)) },
                isBot = cfg.isBot,
                isDisconnected = false,
                rank = null
            )
        }

        return GameState(
            gameId = gameId,
            players = players,
            activePlayerIndex = 0,
            diceState = DiceState(value = 1, isRolled = false, consecutiveSixes = 0, canRoll = true),
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            ranking = emptyList(),
            ruleSet = ruleSet,
            moveHistory = emptyList(),
            lastEvent = null,
            authorityEpoch = authorityEpoch.coerceAtLeast(1L),
            authorityHostId = authorityHostId
        )
    }

    fun rollDice(state: GameState, forcedValue: Int? = null): GameState {
        if (state.isGameOver || state.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
            return state
        }
        if (!state.diceState.canRoll || state.diceState.isRolled) {
            return state
        }
        require(forcedValue == null || forcedValue in 1..6) {
            "forcedValue must be in 1..6, was $forcedValue"
        }

        val activePlayer = state.activePlayer
        val rollValue = forcedValue ?: Random.nextInt(1, 7)
        // When the 3xSix penalty is disabled the streak must not accumulate.
        val newConsecutiveSixes = when {
            !state.ruleSet.penalty3xSix -> 0
            rollValue == 6 -> state.diceState.consecutiveSixes + 1
            else -> 0
        }

        // 3 consecutive sixes penalty
        if (state.ruleSet.penalty3xSix && newConsecutiveSixes == 3) {
            val nextIndex = getNextActivePlayerIndex(state.players, state.activePlayerIndex)
            return state.copy(
                activePlayerIndex = nextIndex,
                diceState = DiceState(value = 6, isRolled = false, consecutiveSixes = 0, canRoll = true),
                turnPhase = TurnPhase.WAITING_FOR_ROLL,
                lastEvent = GameEngineEvent.TurnForfeited3xSix(activePlayer.color),
                version = state.version + 1
            )
        }

        val legalMoves = MoveValidator.getLegalMoves(activePlayer, rollValue, state.players)

        if (legalMoves.isEmpty()) {
            // A six grants another roll even when no token can legally move. Keep
            // the streak so a third consecutive six still forfeits the turn.
            val bonusRoll = rollValue == 6
            val nextIndex = if (bonusRoll) state.activePlayerIndex
            else getNextActivePlayerIndex(state.players, state.activePlayerIndex)
            return state.copy(
                activePlayerIndex = nextIndex,
                diceState = DiceState(
                    value = rollValue,
                    isRolled = false,
                    consecutiveSixes = if (bonusRoll) newConsecutiveSixes else 0,
                    canRoll = true
                ),
                turnPhase = TurnPhase.WAITING_FOR_ROLL,
                lastEvent = if (bonusRoll) {
                    GameEngineEvent.ExtraTurnGranted(activePlayer.color, "Rolled a 6")
                } else {
                    GameEngineEvent.TurnPassedNoMoves(activePlayer.color)
                },
                version = state.version + 1
            )
        }

        return state.copy(
            diceState = DiceState(
                value = rollValue,
                isRolled = true,
                consecutiveSixes = newConsecutiveSixes,
                canRoll = false
            ),
            turnPhase = TurnPhase.WAITING_FOR_MOVE,
            lastEvent = GameEngineEvent.DiceRolled(activePlayer.color, rollValue, newConsecutiveSixes),
            version = state.version + 1
        )
    }

    fun movePiece(state: GameState, pieceId: Int): GameState {
        if (state.isGameOver || state.turnPhase != TurnPhase.WAITING_FOR_MOVE) {
            return state
        }

        val activePlayer = state.activePlayer
        val diceValue = state.diceState.value
        val legalMoves = MoveValidator.getLegalMoves(activePlayer, diceValue, state.players)
        val moveCalc = legalMoves.find { it.piece.id == pieceId } ?: return state

        val originalPiece = moveCalc.piece
        val destination = moveCalc.destination
        val updatedPiece = originalPiece.copy(position = destination)

        val captured = moveCalc.capturedEnemyPiece
        val updatedPlayers = state.players.map { player ->
            when (player.id) {
                activePlayer.id -> {
                    val newPieces = player.pieces.map { p ->
                        if (p.id == pieceId) updatedPiece else p
                    }
                    player.copy(pieces = newPieces)
                }
                captured?.first?.id -> {
                    val capturedPiece = captured.second
                    val newPieces = player.pieces.map { p ->
                        if (p.id == capturedPiece.id) {
                            p.copy(position = PiecePosition.Yard(capturedPiece.id.coerceIn(0, 3)))
                        } else p
                    }
                    player.copy(pieces = newPieces)
                }
                else -> player
            }
        }
        val enemyCaptured: Pair<PlayerState, Piece>? = captured

        val updatedActivePlayer = updatedPlayers.first { it.id == activePlayer.id }
        val playerJustFinished = updatedActivePlayer.hasFinished && activePlayer.rank == null

        val updatedRanking = if (playerJustFinished) {
            state.ranking + activePlayer.color
        } else state.ranking

        val finalPlayers = if (playerJustFinished) {
            updatedPlayers.map { p ->
                if (p.id == activePlayer.id) p.copy(rank = updatedRanking.size) else p
            }
        } else updatedPlayers

        val remainingActiveCount = finalPlayers.count { !it.hasFinished }
        val isGameOver = remainingActiveCount <= 1

        val record = MoveRecord(
            pieceId = pieceId,
            playerColor = activePlayer.color,
            from = originalPiece.position,
            to = destination,
            capturedPieceId = enemyCaptured?.second?.id,
            capturedPlayerColor = enemyCaptured?.first?.color,
            diceValue = diceValue,
            timestamp = System.currentTimeMillis()
        )

        if (isGameOver) {
            val fullRanking = if (remainingActiveCount == 1) {
                val lastPlayer = finalPlayers.first { !it.hasFinished }
                updatedRanking + lastPlayer.color
            } else updatedRanking

            val scoredPlayers = finalPlayers.map { p ->
                if (p.rank == null) p.copy(rank = fullRanking.indexOf(p.color) + 1) else p
            }

            return state.copy(
                players = scoredPlayers,
                turnPhase = TurnPhase.GAME_OVER,
                ranking = fullRanking,
                moveHistory = state.moveHistory + record,
                lastEvent = GameEngineEvent.GameOver(fullRanking.first(), fullRanking),
                version = state.version + 1
            )
        }

        // Determine if player earned a bonus roll:
        // 1. Rolled a 6
        // 2. Captured an enemy piece
        // 3. Reached Home
        val extraTurn = (diceValue == 6 || enemyCaptured != null || destination is PiecePosition.Home) && !playerJustFinished

        val nextPlayerIndex = if (extraTurn) {
            state.activePlayerIndex
        } else {
            getNextActivePlayerIndex(finalPlayers, state.activePlayerIndex)
        }

        val lastEvent = when {
            playerJustFinished -> GameEngineEvent.PlayerFinished(activePlayer.color, updatedRanking.size)
            enemyCaptured != null -> GameEngineEvent.PieceCaptured(activePlayer.color, enemyCaptured!!.first.color, enemyCaptured!!.second.id)
            destination is PiecePosition.Home -> GameEngineEvent.PieceReachedHome(activePlayer.color, pieceId)
            extraTurn -> GameEngineEvent.ExtraTurnGranted(activePlayer.color, if (diceValue == 6) "Rolled a 6" else "Bonus Turn")
            else -> GameEngineEvent.PieceMoved(activePlayer.color, pieceId, originalPiece.position, destination)
        }

        return state.copy(
            players = finalPlayers,
            activePlayerIndex = nextPlayerIndex,
            diceState = DiceState(
                value = diceValue,
                isRolled = false,
                consecutiveSixes = if (extraTurn && diceValue == 6) state.diceState.consecutiveSixes else 0,
                canRoll = true
            ),
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            ranking = updatedRanking,
            moveHistory = state.moveHistory + record,
            lastEvent = lastEvent,
            version = state.version + 1
        )
    }

    fun passTurn(state: GameState): GameState {
        if (state.isGameOver) return state
        // Manual pass is only valid while waiting for a roll. Never allow abandoning
        // an earned move / bonus turn in WAITING_FOR_MOVE (prevents dodging 3xSix).
        if (state.turnPhase != TurnPhase.WAITING_FOR_ROLL) return state
        val nextIndex = getNextActivePlayerIndex(state.players, state.activePlayerIndex)
        return state.copy(
            activePlayerIndex = nextIndex,
            diceState = DiceState(value = state.diceState.value, isRolled = false, consecutiveSixes = 0, canRoll = true),
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            lastEvent = GameEngineEvent.TurnPassedNoMoves(state.activePlayer.color),
            version = state.version + 1
        )
    }

    fun getNextActivePlayerIndex(players: List<PlayerState>, currentIndex: Int): Int {
        if (players.isEmpty()) return 0
        var next = (currentIndex + 1) % players.size
        var attempts = 0
        while ((players[next].hasFinished || players[next].isDisconnected) && attempts < players.size) {
            next = (next + 1) % players.size
            attempts++
        }
        return next
    }
}
