package com.neoludo.game.multiplayer

import com.google.common.truth.Truth.assertThat
import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.sync.DisconnectAiProxy
import com.neoludo.game.multiplayer.sync.StateReconciler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerSyncTest {

    @Test
    fun testStateReconcilerRejectsDuplicatesAndOutOfOrderPackets() {
        val reconciler = StateReconciler()

        val action1 = NetworkAction("act_1", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "p1")
        val action2 = NetworkAction("act_2", sequence = 2L, type = ActionType.MOVE_PIECE, playerId = "p1")
        val staleAction = NetworkAction("act_0", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "p1")

        assertThat(reconciler.canApplyAction(action1)).isTrue()
        reconciler.recordAction(action1)

        // Duplicate action1 rejected
        assertThat(reconciler.canApplyAction(action1)).isFalse()

        // Valid subsequent action2 accepted
        assertThat(reconciler.canApplyAction(action2)).isTrue()
        reconciler.recordAction(action2)

        // Stale sequence (seq <= 2) rejected
        assertThat(reconciler.canApplyAction(staleAction)).isFalse()
    }

    @Test
    fun testDisconnectAiProxyExecutesLegalMoveWhenWaitingForRoll() {
        val initial = LudoGameEngine.createInitialState(
            "proxy_test",
            listOf(
                com.neoludo.game.engine.InitialPlayerConfig("p1", "DisconnectedPlayer", PlayerColor.RED),
                com.neoludo.game.engine.InitialPlayerConfig("p2", "PlayerTwo", PlayerColor.GREEN)
            )
        )

        assertThat(initial.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)

        val next = DisconnectAiProxy.executeProxyStep(initial)
        // Should have rolled dice or passed turn
        assertThat(next.diceState.isRolled).isTrue()
    }

    @Test
    fun testDisconnectAiProxyExecutesLegalMoveWhenWaitingForMove() {
        val initial = LudoGameEngine.createInitialState(
            "proxy_test_2",
            listOf(
                com.neoludo.game.engine.InitialPlayerConfig("p1", "DisconnectedPlayer", PlayerColor.RED),
                com.neoludo.game.engine.InitialPlayerConfig("p2", "PlayerTwo", PlayerColor.GREEN)
            )
        )

        // Roll 6 to enable moving out of yard
        val rolled = LudoGameEngine.rollDice(initial, forcedValue = 6)
        assertThat(rolled.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_MOVE)

        val afterAiStep = DisconnectAiProxy.executeProxyStep(rolled)
        val redPlayer = afterAiStep.players.first { it.color == PlayerColor.RED }
        // One piece should have moved to Path(0)
        assertThat(redPlayer.pieces.any { it.position == PiecePosition.Path(0) }).isTrue()
    }

    @Test
    fun testLocalMultiplayerClientTurnCycle() = runTest {
        val client = LocalMultiplayerClient(playerCount = 2)

        val initialGame = client.gameState.value
        assertThat(initialGame).isNotNull()
        assertThat(initialGame?.players?.size).isEqualTo(2)
        assertThat(initialGame?.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)

        // Roll dice
        val rollResult = client.rollDice()
        assertThat(rollResult.isSuccess).isTrue()

        val afterRoll = client.gameState.value
        assertThat(afterRoll?.diceState?.isRolled).isTrue()
    }

    @Test
    fun testBotMultiplayerClientInitialization() = runTest {
        val client = BotMultiplayerClient(
            humanName = "Human",
            humanColor = PlayerColor.RED,
            botCount = 1
        )

        val state = client.gameState.value
        assertThat(state).isNotNull()
        assertThat(state?.players?.size).isEqualTo(2)
        assertThat(state?.players?.get(0)?.isBot).isFalse()
        assertThat(state?.players?.get(1)?.isBot).isTrue()

        client.release()
    }

    @Test
    fun testConcurrentDiceSpamHandledSafely() = runTest {
        val client = LocalMultiplayerClient(playerCount = 2)

        // First roll
        val r1 = client.rollDice()
        assertThat(r1.isSuccess).isTrue()

        val state = client.gameState.value
        // If state is waiting for move, a second immediate roll attempt MUST fail
        if (state?.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
            val r2 = client.rollDice()
            assertThat(r2.isFailure).isTrue()
        }
    }
    @Test
    fun testInvalidPhaseMoveRejection() = runTest {
        val client = LocalMultiplayerClient(playerCount = 2)
        // In WAITING_FOR_ROLL phase, moving a piece must fail
        val moveResult = client.movePiece(0)
        assertThat(moveResult.isFailure).isTrue()
    }
}
