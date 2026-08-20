package com.neoludo.game.engine

import com.google.common.truth.Truth.assertThat
import com.neoludo.game.engine.coordinate.BoardCoordinates
import com.neoludo.game.engine.model.GameEngineEvent
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.engine.rules.MoveValidator
import org.junit.Test

class LudoGameEngineTest {

    private fun create2PlayerGame(): com.neoludo.game.engine.model.GameState {
        return LudoGameEngine.createInitialState(
            gameId = "test_game",
            playerConfigs = listOf(
                InitialPlayerConfig("p1", "Alice", PlayerColor.RED),
                InitialPlayerConfig("p2", "Bob", PlayerColor.GREEN)
            ),
            ruleSet = LudoRuleSet(autoMoveSinglePiece = false, penalty3xSix = true)
        )
    }

    @Test
    fun testDiceRollUpdatesStateAndLegalMoves() {
        val initial = create2PlayerGame()
        assertThat(initial.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)

        // Rolling a non-6 when all pieces are in Yard -> automatically passes turn
        val rolled3 = LudoGameEngine.rollDice(initial, forcedValue = 3)
        assertThat(rolled3.activePlayerIndex).isEqualTo(1) // Passed to Player 2
        assertThat(rolled3.lastEvent).isInstanceOf(GameEngineEvent.TurnPassedNoMoves::class.java)

        // Player 2 rolls a 6 -> enters WAITING_FOR_MOVE
        val rolled6 = LudoGameEngine.rollDice(rolled3, forcedValue = 6)
        assertThat(rolled6.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_MOVE)
        assertThat(rolled6.diceState.value).isEqualTo(6)
        assertThat(rolled6.diceState.consecutiveSixes).isEqualTo(1)
    }

    @Test
    fun testYardPieceEntersBoardOnSix() {
        val initial = create2PlayerGame()
        val rolled6 = LudoGameEngine.rollDice(initial, forcedValue = 6)
        val afterMove = LudoGameEngine.movePiece(rolled6, pieceId = 0)

        val redPlayer = afterMove.players.first { it.color == PlayerColor.RED }
        assertThat(redPlayer.pieces[0].position).isEqualTo(PiecePosition.Path(0))

        // Rolling 6 grants extra turn
        assertThat(afterMove.activePlayerIndex).isEqualTo(0)
        assertThat(afterMove.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)
    }

    @Test
    fun testStandardPathAdvancement() {
        var state = create2PlayerGame()
        state = LudoGameEngine.rollDice(state, forcedValue = 6)
        state = LudoGameEngine.movePiece(state, pieceId = 0) // Piece 0 at Path(0)

        state = LudoGameEngine.rollDice(state, forcedValue = 4)
        state = LudoGameEngine.movePiece(state, pieceId = 0)

        val redPlayer = state.players.first { it.color == PlayerColor.RED }
        assertThat(redPlayer.pieces[0].position).isEqualTo(PiecePosition.Path(4))
        // Roll was 4, turn should pass to Bob (player 1)
        assertThat(state.activePlayerIndex).isEqualTo(1)
    }

    @Test
    fun testSafeZonesPreventCaptures() {
        // Red start tile (step 0) is global index 0 (Safe Cell)
        // Green start tile is global index 13
        // Star cells are 8, 21, 34, 47
        val redPiece = Piece(0, PlayerColor.RED, PiecePosition.Path(8)) // Global 8 (Star Safe)
        val greenPiece = Piece(0, PlayerColor.GREEN, PiecePosition.Path(47)) // 13 + 47 = 60 % 52 = 8 (Same Global 8!)

        val customPlayers = listOf(
            PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(redPiece)),
            PlayerState("p2", "Bob", PlayerColor.GREEN, pieces = listOf(greenPiece))
        )

        val capture = MoveValidator.checkCapture(PlayerColor.RED, destStep = 8, allPlayers = customPlayers)
        assertThat(capture).isNull() // Star Safe cell protects from capture
    }

    @Test
    fun testCaptureOnUnsafeCellSendsEnemyToYardAndGrantsBonusTurn() {
        // Global cell 10 is unsafe
        // Red step 10 = global 10
        // Green start is 13. To land on global 10, Green relative step is (10 - 13 + 52) = 49.
        val redPiece = Piece(0, PlayerColor.RED, PiecePosition.Path(7))
        val greenPiece = Piece(0, PlayerColor.GREEN, PiecePosition.Path(49)) // Global 10

        var state = LudoGameEngine.createInitialState(
            gameId = "capture_test",
            playerConfigs = listOf(
                InitialPlayerConfig("p1", "Alice", PlayerColor.RED),
                InitialPlayerConfig("p2", "Bob", PlayerColor.GREEN)
            )
        )

        // Inject piece positions
        val customPlayers = listOf(
            state.players[0].copy(pieces = listOf(redPiece)),
            state.players[1].copy(pieces = listOf(greenPiece))
        )
        state = state.copy(players = customPlayers)

        // Red rolls 3: 7 + 3 = 10 (lands on Green at global 10)
        state = LudoGameEngine.rollDice(state, forcedValue = 3)
        state = LudoGameEngine.movePiece(state, pieceId = 0)

        val updatedGreen = state.players.first { it.color == PlayerColor.GREEN }
        assertThat(updatedGreen.pieces[0].position).isEqualTo(PiecePosition.Yard(0))

        // Red earns a bonus turn for capturing!
        assertThat(state.activePlayerIndex).isEqualTo(0)
        assertThat(state.lastEvent).isInstanceOf(GameEngineEvent.PieceCaptured::class.java)
    }

    @Test
    fun testExactRollRequiredForHome() {
        val redPiece = Piece(0, PlayerColor.RED, PiecePosition.Path(53))
        val player = PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(redPiece))

        // Can move on roll 3 (53 + 3 = 56 Home)
        val canRoll3 = MoveValidator.canPieceMove(redPiece, 3)
        assertThat(canRoll3).isTrue()
        val dest3 = MoveValidator.calculateDestination(redPiece, 3)
        assertThat(dest3).isEqualTo(PiecePosition.Home)

        // Overshoot on roll 4 (53 + 4 = 57 > 56) -> Illegal
        val canRoll4 = MoveValidator.canPieceMove(redPiece, 4)
        assertThat(canRoll4).isFalse()
    }

    @Test
    fun testReachingHomeGrantsBonusTurn() {
        val redPiece = Piece(0, PlayerColor.RED, PiecePosition.Path(54))
        val otherPiece = Piece(1, PlayerColor.RED, PiecePosition.Path(10))

        var state = create2PlayerGame()
        val customPlayers = listOf(
            state.players[0].copy(pieces = listOf(redPiece, otherPiece)),
            state.players[1]
        )
        state = state.copy(players = customPlayers)

        // Roll 2 -> 54 + 2 = 56 (Home)
        state = LudoGameEngine.rollDice(state, forcedValue = 2)
        state = LudoGameEngine.movePiece(state, pieceId = 0)

        val redPlayer = state.players.first { it.color == PlayerColor.RED }
        assertThat(redPlayer.pieces[0].position).isEqualTo(PiecePosition.Home)

        // Bonus turn granted for scoring into Home
        assertThat(state.activePlayerIndex).isEqualTo(0)
    }

    @Test
    fun testThreeConsecutiveSixesForfeitsTurn() {
        var state = create2PlayerGame()
        // 1st six
        state = LudoGameEngine.rollDice(state, forcedValue = 6)
        state = LudoGameEngine.movePiece(state, pieceId = 0)
        assertThat(state.activePlayerIndex).isEqualTo(0)

        // 2nd six
        state = LudoGameEngine.rollDice(state, forcedValue = 6)
        state = LudoGameEngine.movePiece(state, pieceId = 1)
        assertThat(state.activePlayerIndex).isEqualTo(0)

        // 3rd six -> Turn immediately forfeited!
        state = LudoGameEngine.rollDice(state, forcedValue = 6)
        assertThat(state.activePlayerIndex).isEqualTo(1) // Passed to Bob
        assertThat(state.lastEvent).isInstanceOf(GameEngineEvent.TurnForfeited3xSix::class.java)
    }

    @Test
    fun testMultiplayerRankingAndGameOver() {
        // Player 1 has 3 pieces in Home, 1 piece at Path(55)
        val pieces = listOf(
            Piece(0, PlayerColor.RED, PiecePosition.Home),
            Piece(1, PlayerColor.RED, PiecePosition.Home),
            Piece(2, PlayerColor.RED, PiecePosition.Home),
            Piece(3, PlayerColor.RED, PiecePosition.Path(55))
        )

        var state = create2PlayerGame()
        state = state.copy(players = listOf(state.players[0].copy(pieces = pieces), state.players[1]))

        // Roll 1: 55 + 1 = 56 Home (All 4 pieces home!)
        state = LudoGameEngine.rollDice(state, forcedValue = 1)
        state = LudoGameEngine.movePiece(state, pieceId = 3)

        assertThat(state.turnPhase).isEqualTo(TurnPhase.GAME_OVER)
        assertThat(state.ranking).containsExactly(PlayerColor.RED, PlayerColor.GREEN).inOrder()
        assertThat(state.lastEvent).isInstanceOf(GameEngineEvent.GameOver::class.java)
    }
}
