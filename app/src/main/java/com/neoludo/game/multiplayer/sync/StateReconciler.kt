package com.neoludo.game.multiplayer.sync

import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.model.NetworkAction

class StateReconciler {
    private val processedActionIds = mutableSetOf<String>()
    private val lastSequenceBySender = mutableMapOf<String, Long>()

    @Synchronized
    fun canApplyAction(action: NetworkAction): Boolean {
        if (action.actionId.isBlank()) return false
        if (action.actionId in processedActionIds) return false
        if (action.sequence == 0L) return true
        if (action.sequence < 0L || action.sequence > ActionDeduplicator.MAX_SEQUENCE) return false
        if (action.playerId.isNotBlank()) {
            val last = lastSequenceBySender[action.playerId] ?: 0L
            if (action.sequence <= last) return false
        }
        return true
    }

    @Synchronized
    fun recordAction(action: NetworkAction) {
        if (action.actionId.isNotBlank()) processedActionIds.add(action.actionId)
        if (action.sequence in 1L..ActionDeduplicator.MAX_SEQUENCE && action.playerId.isNotBlank()) {
            val last = lastSequenceBySender[action.playerId] ?: 0L
            if (action.sequence > last) lastSequenceBySender[action.playerId] = action.sequence
        }
    }

    @Synchronized
    fun reset() {
        lastSequenceBySender.clear()
        processedActionIds.clear()
    }
}

object DisconnectAiProxy {

    fun executeProxyStep(gameState: GameState): GameState {
        val active = gameState.activePlayer
        if (gameState.isGameOver) return gameState

        return when (gameState.turnPhase) {
            TurnPhase.WAITING_FOR_ROLL -> {
                LudoGameEngine.rollDice(gameState)
            }
            TurnPhase.WAITING_FOR_MOVE -> {
                val bestMove = LudoBotEngine.pickBestMove(gameState, Difficulty.NORMAL)
                if (bestMove != null) {
                    LudoGameEngine.movePiece(gameState, bestMove.id)
                } else {
                    // No legal moves: engine auto-passes on roll, so a proxy in
                    // WAITING_FOR_MOVE with zero moves is already inconsistent —
                    // return unchanged rather than a passTurn no-op (which is now
                    // correctly guarded to WAITING_FOR_ROLL only).
                    gameState
                }
            }
            else -> gameState
        }
    }
}
