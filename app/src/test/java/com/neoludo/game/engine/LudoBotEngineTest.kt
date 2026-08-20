package com.neoludo.game.engine

import com.google.common.truth.Truth.assertThat
import com.neoludo.game.engine.ai.Difficulty
import com.neoludo.game.engine.ai.LudoBotEngine
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.engine.rules.MoveValidator
import org.junit.Test
import kotlin.random.Random

class LudoBotEngineTest {

    @Test
    fun testEasyBotReleasesPieceOnSix() {
        val pieces = listOf(
            Piece(0, PlayerColor.RED, PiecePosition.Yard(0)),
            Piece(1, PlayerColor.RED, PiecePosition.Path(10))
        )
        val initial = LudoGameEngine.createInitialState(
            "ai_test",
            listOf(
                InitialPlayerConfig("bot1", "Bot", PlayerColor.RED, isBot = true),
                InitialPlayerConfig("bot2", "Enemy", PlayerColor.GREEN, isBot = true)
            )
        )
        val state = initial.copy(
            players = listOf(initial.players[0].copy(pieces = pieces), initial.players[1]),
            turnPhase = TurnPhase.WAITING_FOR_MOVE,
            diceState = com.neoludo.game.engine.model.DiceState(value = 6, isRolled = true, canRoll = false)
        )

        val bestMove = LudoBotEngine.pickBestMove(state, Difficulty.EASY)
        assertThat(bestMove).isNotNull()
        assertThat(bestMove?.id).isEqualTo(0) // Yard piece released
    }

    @Test
    fun testNormalBotPrefersWinningOverCapturing() {
        // Piece 0 can score into Home (step 53 + 3 = 56 Home)
        // Piece 1 can capture an enemy at step 10
        val pieceWinning = Piece(0, PlayerColor.RED, PiecePosition.Path(53))
        val pieceCapturing = Piece(1, PlayerColor.RED, PiecePosition.Path(7)) // 7 + 3 = 10 (lands on Green)
        val enemyPiece = Piece(0, PlayerColor.GREEN, PiecePosition.Path(49)) // Global 10

        val initial = LudoGameEngine.createInitialState(
            "ai_test_2",
            listOf(
                InitialPlayerConfig("bot1", "Bot", PlayerColor.RED, isBot = true),
                InitialPlayerConfig("bot2", "Enemy", PlayerColor.GREEN, isBot = true)
            )
        )
        val state = initial.copy(
            players = listOf(
                initial.players[0].copy(pieces = listOf(pieceWinning, pieceCapturing)),
                initial.players[1].copy(pieces = listOf(enemyPiece))
            ),
            turnPhase = TurnPhase.WAITING_FOR_MOVE,
            diceState = com.neoludo.game.engine.model.DiceState(value = 3, isRolled = true, canRoll = false)
        )

        val bestMove = LudoBotEngine.pickBestMove(state, Difficulty.NORMAL)
        assertThat(bestMove).isNotNull()
        assertThat(bestMove?.id).isEqualTo(0) // Scoring into Home takes top priority over capturing
    }

    @Test
    fun testNormalBotPrefersCapturingOverSimpleMove() {
        // Piece 0 can capture enemy at step 10
        // Piece 1 can make a simple move from step 20 -> 23
        val pieceCapturing = Piece(0, PlayerColor.RED, PiecePosition.Path(7)) // 7 + 3 = 10
        val pieceSimple = Piece(1, PlayerColor.RED, PiecePosition.Path(20))
        val enemyPiece = Piece(0, PlayerColor.GREEN, PiecePosition.Path(49)) // Global 10

        val initial = LudoGameEngine.createInitialState(
            "ai_test_3",
            listOf(
                InitialPlayerConfig("bot1", "Bot", PlayerColor.RED, isBot = true),
                InitialPlayerConfig("bot2", "Enemy", PlayerColor.GREEN, isBot = true)
            )
        )
        val state = initial.copy(
            players = listOf(
                initial.players[0].copy(pieces = listOf(pieceCapturing, pieceSimple)),
                initial.players[1].copy(pieces = listOf(enemyPiece))
            ),
            turnPhase = TurnPhase.WAITING_FOR_MOVE,
            diceState = com.neoludo.game.engine.model.DiceState(value = 3, isRolled = true, canRoll = false)
        )

        val bestMove = LudoBotEngine.pickBestMove(state, Difficulty.NORMAL)
        assertThat(bestMove).isNotNull()
        assertThat(bestMove?.id).isEqualTo(0) // Capturing chosen
    }

    @Test
    fun testAutomatedAiGameSimulationPlaysOnlyLegalMoves() {
        var state = LudoGameEngine.createInitialState(
            "sim_game",
            listOf(
                InitialPlayerConfig("b1", "Bot 1", PlayerColor.RED, isBot = true),
                InitialPlayerConfig("b2", "Bot 2", PlayerColor.GREEN, isBot = true),
                InitialPlayerConfig("b3", "Bot 3", PlayerColor.YELLOW, isBot = true),
                InitialPlayerConfig("b4", "Bot 4", PlayerColor.BLUE, isBot = true)
            )
        )

        val difficulties = listOf(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.HARD)

        var turns = 0
        while (!state.isGameOver && turns < 200) {
            turns++
            if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
                state = LudoGameEngine.rollDice(state)
            } else if (state.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
                val botDifficulty = difficulties[state.activePlayerIndex]
                val chosenPiece = LudoBotEngine.pickBestMove(state, botDifficulty)
                assertThat(chosenPiece).isNotNull()

                val legal = MoveValidator.getLegalMoves(state.activePlayer, state.diceState.value, state.players)
                assertThat(legal.map { it.piece.id }).contains(chosenPiece!!.id)

                state = LudoGameEngine.movePiece(state, chosenPiece.id)
            }
        }

        assertThat(turns).isGreaterThan(10)
    }
}
