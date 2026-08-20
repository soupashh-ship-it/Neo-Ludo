package com.neoludo.game.engine.rules

import com.neoludo.game.engine.coordinate.BoardCoordinates
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState

data class MoveCalculation(
    val piece: Piece,
    val destination: PiecePosition,
    val capturedEnemyPiece: Pair<PlayerState, Piece>? = null
)

object MoveValidator {

    fun canPieceMove(piece: Piece, diceValue: Int): Boolean {
        return when (val pos = piece.position) {
            is PiecePosition.Yard -> diceValue == 6
            is PiecePosition.Path -> (pos.step + diceValue) <= BoardCoordinates.HOME_STEP
            is PiecePosition.Home -> false
        }
    }

    fun calculateDestination(piece: Piece, diceValue: Int): PiecePosition? {
        if (!canPieceMove(piece, diceValue)) return null
        return when (val pos = piece.position) {
            is PiecePosition.Yard -> PiecePosition.Path(0)
            is PiecePosition.Path -> {
                val nextStep = pos.step + diceValue
                if (nextStep == BoardCoordinates.HOME_STEP) {
                    PiecePosition.Home
                } else {
                    PiecePosition.Path(nextStep)
                }
            }
            is PiecePosition.Home -> null
        }
    }

    fun getLegalMoves(
        player: PlayerState,
        diceValue: Int,
        allPlayers: List<PlayerState>
    ): List<MoveCalculation> {
        if (player.hasFinished) return emptyList()

        val legal = mutableListOf<MoveCalculation>()
        for (piece in player.pieces) {
            val dest = calculateDestination(piece, diceValue) ?: continue
            val capture = if (dest is PiecePosition.Path && dest.step in 0..50) {
                checkCapture(player.color, dest.step, allPlayers)
            } else null

            legal.add(MoveCalculation(piece, dest, capture))
        }
        return legal
    }

    fun checkCapture(
        attackerColor: PlayerColor,
        destStep: Int,
        allPlayers: List<PlayerState>
    ): Pair<PlayerState, Piece>? {
        if (BoardCoordinates.isSafeCell(attackerColor, destStep)) {
            return null // Safe cell protects from capture
        }

        val attackerGlobalIdx = BoardCoordinates.globalPathIndex(attackerColor, destStep)

        for (enemyPlayer in allPlayers) {
            if (enemyPlayer.color == attackerColor || enemyPlayer.hasFinished) continue

            for (enemyPiece in enemyPlayer.pieces) {
                val enemyPos = enemyPiece.position
                if (enemyPos is PiecePosition.Path && enemyPos.step in 0..50) {
                    val enemyGlobalIdx = BoardCoordinates.globalPathIndex(enemyPlayer.color, enemyPos.step)
                    if (enemyGlobalIdx == attackerGlobalIdx) {
                        return enemyPlayer to enemyPiece
                    }
                }
            }
        }
        return null
    }
}
