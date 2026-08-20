package com.neoludo.game.engine.ai

import com.neoludo.game.engine.coordinate.BoardCoordinates
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState
import com.neoludo.game.engine.rules.MoveCalculation
import com.neoludo.game.engine.rules.MoveValidator
import kotlin.random.Random

enum class Difficulty {
    EASY,
    NORMAL,
    HARD
}

object LudoBotEngine {

    fun pickBestMove(
        state: GameState,
        difficulty: Difficulty = Difficulty.NORMAL
    ): Piece? {
        val activePlayer = state.activePlayer
        val diceValue = state.diceState.value
        val legalMoves = MoveValidator.getLegalMoves(activePlayer, diceValue, state.players)

        if (legalMoves.isEmpty()) return null
        if (legalMoves.size == 1) return legalMoves.first().piece

        return when (difficulty) {
            Difficulty.EASY -> pickEasyMove(legalMoves)
            Difficulty.NORMAL -> pickNormalMove(legalMoves, state)
            Difficulty.HARD -> pickHardMove(legalMoves, state)
        }
    }

    private fun pickEasyMove(moves: List<MoveCalculation>): Piece {
        // Easy: Always release piece from yard on 6 if possible, else random
        val yardMove = moves.find { it.piece.position is PiecePosition.Yard }
        return yardMove?.piece ?: moves.random().piece
    }

    private fun pickNormalMove(moves: List<MoveCalculation>, state: GameState): Piece {
        return moves.maxByOrNull { move ->
            evaluateNormalMove(move, state)
        }?.piece ?: moves.first().piece
    }

    private fun evaluateNormalMove(move: MoveCalculation, state: GameState): Double {
        var score = 0.0
        val piece = move.piece
        val dest = move.destination
        val player = state.activePlayer

        // 1. Reaching Home: Maximum priority
        if (dest is PiecePosition.Home) {
            score += 1000.0
        }

        // 2. Capturing Enemy piece: Very high priority
        if (move.capturedEnemyPiece != null) {
            score += 500.0
        }

        // 3. Releasing from Yard on 6
        if (piece.position is PiecePosition.Yard) {
            score += 300.0
        }

        // 4. Landing on Safe Zone / Star
        if (dest is PiecePosition.Path) {
            if (BoardCoordinates.isSafeCell(player.color, dest.step)) {
                score += 200.0
            }

            // 5. Escaping danger if current position is threatened
            if (piece.position is PiecePosition.Path) {
                val wasThreatened = isCellThreatened(player.color, piece.position.step, state.players)
                val willBeThreatened = isCellThreatened(player.color, dest.step, state.players)
                if (wasThreatened && !willBeThreatened) {
                    score += 150.0
                } else if (!wasThreatened && willBeThreatened) {
                    score -= 100.0
                }
            }

            // 6. Prefer advancing pieces closer to Home
            score += (dest.step * 2.0)
        }

        return score
    }

    private fun pickHardMove(moves: List<MoveCalculation>, state: GameState): Piece {
        return moves.maxByOrNull { move ->
            evaluateHardMove(move, state)
        }?.piece ?: moves.first().piece
    }

    private fun evaluateHardMove(move: MoveCalculation, state: GameState): Double {
        var score = 0.0
        val piece = move.piece
        val dest = move.destination
        val player = state.activePlayer

        // 1. Home entry
        if (dest is PiecePosition.Home) {
            score += 2000.0
        }

        // 2. Captures: value based on how advanced the enemy piece is
        if (move.capturedEnemyPiece != null) {
            val enemyPiece = move.capturedEnemyPiece.second
            val enemyStep = (enemyPiece.position as? PiecePosition.Path)?.step ?: 0
            score += 800.0 + (enemyStep * 5.0)
        }

        // 3. Bringing pieces into the game
        if (piece.position is PiecePosition.Yard) {
            val piecesOnBoard = player.pieces.count { it.isOnPath }
            score += if (piecesOnBoard == 0) 500.0 else 350.0
        }

        // 4. Safe zone calculations & danger heatmaps
        if (dest is PiecePosition.Path) {
            val isSafe = BoardCoordinates.isSafeCell(player.color, dest.step)
            if (isSafe) {
                score += 300.0
            }

            // Calculate threat probability from enemy pieces behind
            val threatWeight = calculateThreatSeverity(player.color, dest.step, state.players)
            score -= threatWeight

            // Distance progression bonus
            if (dest.step > 50) {
                // In safe home stretch
                score += 400.0 + (dest.step * 10.0)
            } else {
                score += (dest.step * 3.0)
            }

            // If leaving a safe cell into danger, penalize heavily
            if (piece.position is PiecePosition.Path && BoardCoordinates.isSafeCell(player.color, piece.position.step)) {
                if (threatWeight > 100.0) {
                    score -= 250.0
                }
            }
        }

        return score
    }

    private fun isCellThreatened(playerColor: PlayerColor, step: Int, allPlayers: List<PlayerState>): Boolean {
        if (BoardCoordinates.isSafeCell(playerColor, step)) return false
        if (step > 50) return false // Private home stretch

        val globalIdx = BoardCoordinates.globalPathIndex(playerColor, step)

        for (enemy in allPlayers) {
            if (enemy.color == playerColor || enemy.hasFinished) continue
            for (enemyPiece in enemy.pieces) {
                val enemyPos = enemyPiece.position
                if (enemyPos is PiecePosition.Path && enemyPos.step in 0..50) {
                    val enemyGlobalIdx = BoardCoordinates.globalPathIndex(enemy.color, enemyPos.step)
                    val distance = (globalIdx - enemyGlobalIdx + BoardCoordinates.TOTAL_PATH_CELLS) % BoardCoordinates.TOTAL_PATH_CELLS
                    if (distance in 1..6) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun calculateThreatSeverity(playerColor: PlayerColor, step: Int, allPlayers: List<PlayerState>): Double {
        if (BoardCoordinates.isSafeCell(playerColor, step)) return 0.0
        if (step > 50) return 0.0

        var totalSeverity = 0.0
        val globalIdx = BoardCoordinates.globalPathIndex(playerColor, step)

        for (enemy in allPlayers) {
            if (enemy.color == playerColor || enemy.hasFinished) continue
            for (enemyPiece in enemy.pieces) {
                val enemyPos = enemyPiece.position
                if (enemyPos is PiecePosition.Path && enemyPos.step in 0..50) {
                    val enemyGlobalIdx = BoardCoordinates.globalPathIndex(enemy.color, enemyPos.step)
                    val distance = (globalIdx - enemyGlobalIdx + BoardCoordinates.TOTAL_PATH_CELLS) % BoardCoordinates.TOTAL_PATH_CELLS
                    if (distance in 1..6) {
                        // Threat weight inversely proportional to distance (closer = higher threat)
                        val threat = (7.0 - distance) * 40.0
                        totalSeverity += threat
                    }
                }
            }
        }
        return totalSeverity
    }
}
