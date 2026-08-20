package com.neoludo.game.multiplayer.sync

import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.model.NetworkAction

class StateReconciler {
    private var lastProcessedSequence: Long = 0L
    private val processedActionIds = mutableSetOf<String>()

    fun canApplyAction(action: NetworkAction): Boolean {
        if (action.actionId in processedActionIds) return false
        if (action.sequence <= lastProcessedSequence) return false
        return true
    }

    fun recordAction(action: NetworkAction) {
        processedActionIds.add(action.actionId)
        lastProcessedSequence = maxOf(lastProcessedSequence, action.sequence)
    }

    fun reset() {
        lastProcessedSequence = 0L
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
                    LudoGameEngine.passTurn(gameState)
                }
            }
            else -> gameState
        }
    }
}
