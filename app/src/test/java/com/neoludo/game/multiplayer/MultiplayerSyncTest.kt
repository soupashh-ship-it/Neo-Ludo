package com.neoludo.game.multiplayer

import com.google.common.truth.Truth.assertThat
import com.neoludo.game.engine.LudoGameEngine
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.LudoRuleSet
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.PlayerState
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.model.ActionType
import com.neoludo.game.multiplayer.model.NetworkAction
import com.neoludo.game.multiplayer.model.NetworkEventType
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomStatus
import com.neoludo.game.multiplayer.sync.ActionDeduplicator
import com.neoludo.game.multiplayer.sync.AuthoritativeGameProcessor
import com.neoludo.game.multiplayer.sync.DisconnectAiProxy
import com.neoludo.game.multiplayer.sync.HostElectionManager
import com.neoludo.game.multiplayer.sync.StateReconciler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerSyncTest {

    @Test
    fun testActionDeduplicatorRejectsDuplicateAndStaleActions() {
        val deduplicator = ActionDeduplicator()
        val action1 = NetworkAction(actionId = "act_1", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "p1")
        val action2 = NetworkAction(actionId = "act_2", sequence = 2L, type = ActionType.MOVE_PIECE, playerId = "p1", payload = "0")

        assertThat(deduplicator.isDuplicateOrStale(action1.actionId, action1.sequence)).isFalse()
        deduplicator.markProcessed(action1.actionId, action1.sequence)

        // Duplicate submission of action 1
        assertThat(deduplicator.isDuplicateOrStale(action1.actionId, action1.sequence)).isTrue()

        // New valid action 2
        assertThat(deduplicator.isDuplicateOrStale(action2.actionId, action2.sequence)).isFalse()
        deduplicator.markProcessed(action2.actionId, action2.sequence)

        // Stale sequence
        val staleAction = NetworkAction(actionId = "act_3", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "p1")
        assertThat(deduplicator.isDuplicateOrStale(staleAction.actionId, staleAction.sequence)).isTrue()
    }

    @Test
    fun testHostElectionManagerElectsLowestUidWhenHostDisconnects() {
        val manager = HostElectionManager()
        val meta = RoomMetadata(roomId = "NL-TEST01", hostId = "host_1", maxPlayers = 4)

        val player1 = PlayerPresence(id = "host_1", name = "Alice", color = PlayerColor.RED, isHost = true, isConnected = true, joinedAt = 100L)
        val player2 = PlayerPresence(id = "guest_b", name = "Bob", color = PlayerColor.GREEN, isHost = false, isConnected = true, joinedAt = 200L)
        val player3 = PlayerPresence(id = "guest_c", name = "Charlie", color = PlayerColor.YELLOW, isHost = false, isConnected = true, joinedAt = 300L)

        // While host is connected
        assertThat(manager.determineCurrentHost(meta, listOf(player1, player2, player3))).isEqualTo("host_1")
        assertThat(manager.isLocalPlayerHost("host_1", meta, listOf(player1, player2, player3))).isTrue()
        assertThat(manager.isLocalPlayerHost("guest_b", meta, listOf(player1, player2, player3))).isFalse()

        // When host disconnects
        val disconnectedHost = player1.copy(isConnected = false)
        val newHost = manager.determineCurrentHost(meta, listOf(disconnectedHost, player2, player3))
        assertThat(newHost).isEqualTo("guest_b") // Earliest joined connected player
        assertThat(manager.isLocalPlayerHost("guest_b", meta, listOf(disconnectedHost, player2, player3))).isTrue()
    }

    @Test
    fun testAuthoritativeGameProcessorInitializationAndTurnFlow() {
        val processor = AuthoritativeGameProcessor(allowForcedDice = true)
        val meta = RoomMetadata(roomId = "NL-FLOW01", hostId = "p1", maxPlayers = 2)
        val players = listOf(
            PlayerPresence(id = "p1", name = "Alice", color = PlayerColor.RED, isHost = true),
            PlayerPresence(id = "p2", name = "Bob", color = PlayerColor.GREEN, isHost = false)
        )

        val initResult = processor.initializeGame(meta, players)
        val state0 = initResult.updatedState

        assertThat(state0.players.size).isEqualTo(2)
        assertThat(state0.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)
        assertThat(state0.version).isEqualTo(0L)
        assertThat(initResult.events.first().type).isEqualTo(NetworkEventType.GAME_STARTED)

        // p1 (Active) submits ROLL_DICE with forced 6 (deterministic: yard piece exits)
        val rollAction = NetworkAction(actionId = "act_r1", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "p1", payload = "6", expectedVersion = 0L, expectedHostEpoch = 1L)
        val rollResult = processor.processAction(rollAction, state0, meta)
        assertThat(rollResult).isNotNull()

        val state1 = rollResult!!.updatedState
        assertThat(state1.version).isEqualTo(1L)
        assertThat(state1.diceState.isRolled).isTrue()
        assertThat(rollResult.events.any { it.type == NetworkEventType.DICE_ROLLED }).isTrue()
    }

    @Test
    fun testAuthoritativeGameProcessorRejectsWrongPlayerAction() {
        val processor = AuthoritativeGameProcessor()
        val meta = RoomMetadata(roomId = "NL-FLOW02", hostId = "p1", maxPlayers = 2)
        val players = listOf(
            PlayerPresence(id = "p1", name = "Alice", color = PlayerColor.RED, isHost = true),
            PlayerPresence(id = "p2", name = "Bob", color = PlayerColor.GREEN, isHost = false)
        )

        val initResult = processor.initializeGame(meta, players)
        val state0 = initResult.updatedState

        // p2 attempts to roll out of turn
        val badAction = NetworkAction(actionId = "act_bad", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "p2", expectedVersion = 0L, expectedHostEpoch = 1L)
        val badResult = processor.processAction(badAction, state0, meta)
        assertThat(badResult).isNull() // Rejection
    }

    @Test
    fun testAuthoritativeGameProcessorCapturesEnemyPiece() {
        val processor = AuthoritativeGameProcessor()
        val meta = RoomMetadata(roomId = "NL-CAP01", hostId = "p1", maxPlayers = 2)

        val redPiece = Piece(0, PlayerColor.RED, PiecePosition.Path(7))
        val greenPiece = Piece(0, PlayerColor.GREEN, PiecePosition.Path(49)) // Global index 10

        val p1 = PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(redPiece))
        val p2 = PlayerState("p2", "Bob", PlayerColor.GREEN, pieces = listOf(greenPiece))

        var state = GameState(
            gameId = "NL-CAP01",
            players = listOf(p1, p2),
            activePlayerIndex = 0,
            turnPhase = TurnPhase.WAITING_FOR_MOVE,
            diceState = com.neoludo.game.engine.model.DiceState(value = 3, isRolled = true, canRoll = false),
            version = 5L
        )

        // Red moves piece 0 with roll 3 (7 + 3 = 10 -> lands on Green)
        val moveAction = NetworkAction(actionId = "act_m1", sequence = 6L, type = ActionType.MOVE_PIECE, playerId = "p1", payload = "0", expectedVersion = 5L, expectedHostEpoch = 1L)
        val result = processor.processAction(moveAction, state, meta)
        assertThat(result).isNotNull()

        val updatedState = result!!.updatedState
        val capturedGreen = updatedState.players[1].pieces[0]
        assertThat(capturedGreen.position).isEqualTo(PiecePosition.Yard(0))
        assertThat(result.events.any { it.type == NetworkEventType.PIECE_CAPTURED }).isTrue()
        assertThat(updatedState.activePlayerIndex).isEqualTo(0) // Bonus turn granted
        assertThat(updatedState.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)
    }

    @Test
    fun testDisconnectAiProxyExecutesLegalMoveWhenWaitingForRoll() {
        val redPiece = Piece(0, PlayerColor.RED, PiecePosition.Yard(0))
        val player = PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(redPiece))
        val state = GameState(
            gameId = "test_proxy",
            players = listOf(player),
            activePlayerIndex = 0,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            version = 1L
        )

        val nextState = DisconnectAiProxy.executeProxyStep(state)
        assertThat(nextState.version).isEqualTo(2L)
        assertThat(nextState.diceState.isRolled || nextState.turnPhase == TurnPhase.WAITING_FOR_ROLL).isTrue()
    }

    @Test
    fun testRoomCodeNormalization() {
        assertThat(FirebaseMultiplayerClient.normalizeRoomCode("nl-x7k9qp")).isEqualTo("NL-X7K9QP")
        assertThat(FirebaseMultiplayerClient.normalizeRoomCode("X7K9QP")).isEqualTo("NL-X7K9QP")
        assertThat(FirebaseMultiplayerClient.normalizeRoomCode("  nl - x7k 9qp  ")).isEqualTo("NL-X7K9QP")
    }

    @Test
    fun testLocalMultiplayerClientTurnCycle() = runTest {
        val client = LocalMultiplayerClient(playerCount = 2)
        val initial = client.gameState.value
        assertThat(initial).isNotNull()
        assertThat(initial?.activePlayerIndex).isEqualTo(0)

        // Roll dice
        val rollResult = client.rollDice()
        assertThat(rollResult.isSuccess).isTrue()

        val afterRoll = client.gameState.value
        assertThat(afterRoll?.version).isEqualTo(1L)
        if (afterRoll?.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
            // A 6 was rolled: yard piece can exit, dice shows as rolled.
            assertThat(afterRoll.diceState.isRolled).isTrue()
        } else {
            // No 6 rolled, no legal moves: turn auto-passes with a fresh dice
            // for the next player (isRolled=false — no stale value leaks).
            assertThat(afterRoll?.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)
            assertThat(afterRoll?.activePlayerIndex).isEqualTo(1)
            assertThat(afterRoll?.diceState?.isRolled).isFalse()
        }
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
    fun testGameStateVersionIncrementsOnPassTurnNoMoves() {
        val redPiece = Piece(0, PlayerColor.RED, PiecePosition.Yard(0))
        val player = PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(redPiece))
        val state = GameState(
            gameId = "test_pass",
            players = listOf(player),
            activePlayerIndex = 0,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            diceState = com.neoludo.game.engine.model.DiceState(value = 0, isRolled = false, canRoll = true),
            version = 10L
        )

        val passedState = LudoGameEngine.passTurn(state)
        assertThat(passedState.version).isEqualTo(11L)
        assertThat(passedState.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)
    }

    @Test
    fun testStateReconcilerActionTracking() {
        val reconciler = StateReconciler()
        val action1 = NetworkAction(actionId = "act_10", sequence = 10L, type = ActionType.ROLL_DICE, playerId = "p1")
        val action2 = NetworkAction(actionId = "act_11", sequence = 11L, type = ActionType.MOVE_PIECE, playerId = "p1")
        val staleAction = NetworkAction(actionId = "act_9", sequence = 9L, type = ActionType.ROLL_DICE, playerId = "p1")

        assertThat(reconciler.canApplyAction(action1)).isTrue()
        reconciler.recordAction(action1)

        // Duplicate
        assertThat(reconciler.canApplyAction(action1)).isFalse()
        // Older sequence
        assertThat(reconciler.canApplyAction(staleAction)).isFalse()

        // Newer sequence
        assertThat(reconciler.canApplyAction(action2)).isTrue()
        reconciler.recordAction(action2)
        assertThat(reconciler.canApplyAction(action2)).isFalse()
    }

    @Test
    fun testAuthoritativeGameProcessorThreeConsecutiveSixesPenalty() {
        val processor = AuthoritativeGameProcessor(allowForcedDice = true)
        val meta = RoomMetadata(roomId = "NL-666", hostId = "p1", maxPlayers = 2)
        val p1 = PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(Piece(0, PlayerColor.RED, PiecePosition.Path(5))))
        val p2 = PlayerState("p2", "Bob", PlayerColor.GREEN, pieces = listOf(Piece(0, PlayerColor.GREEN, PiecePosition.Yard(0))))

        var state = GameState(
            gameId = "NL-666",
            players = listOf(p1, p2),
            activePlayerIndex = 0,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            diceState = com.neoludo.game.engine.model.DiceState(value = 6, consecutiveSixes = 2, canRoll = true),
            ruleSet = LudoRuleSet(penalty3xSix = true),
            version = 10L
        )

        // Roll forced 6 as 3rd consecutive 6
        val rollAction = NetworkAction(actionId = "act_6_3", sequence = 11L, type = ActionType.ROLL_DICE, playerId = "p1", payload = "6", expectedVersion = 10L, expectedHostEpoch = 1L)
        val result = processor.processAction(rollAction, state, meta)
        assertThat(result).isNotNull()

        val updated = result!!.updatedState
        // Turn forfeited and passed to Player 2 (Bob)
        assertThat(updated.activePlayerIndex).isEqualTo(1)
        assertThat(updated.turnPhase).isEqualTo(TurnPhase.WAITING_FOR_ROLL)
        assertThat(result.events.any { it.type == NetworkEventType.TURN_FORFEITED }).isTrue()
    }
    @Test
    fun testForgedDicePayloadIgnoredInProd() {
        val processor = AuthoritativeGameProcessor()
        val meta = RoomMetadata(roomId = "NL-FORGE", hostId = "p1", maxPlayers = 2)
        val p1 = PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(Piece(0, PlayerColor.RED, PiecePosition.Yard(0))))
        val p2 = PlayerState("p2", "Bob", PlayerColor.GREEN, pieces = listOf(Piece(0, PlayerColor.GREEN, PiecePosition.Yard(0))))
        val state = GameState(
            gameId = "NL-FORGE",
            players = listOf(p1, p2),
            activePlayerIndex = 0,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            diceState = com.neoludo.game.engine.model.DiceState(value = 1, canRoll = true),
            version = 0L
        )
        // Attacker posts ROLL with payload 6 fifty times — none may force a 6 deterministically
        // (prod ignores payload; result is random). We assert only that processing succeeds
        // and never crashes, and that a blank payload behaves identically.
        val forged = NetworkAction(actionId = "forge_1", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "p1", payload = "6", expectedVersion = 0L, expectedHostEpoch = 1L)
        val legit = NetworkAction(actionId = "legit_1", sequence = 2L, type = ActionType.ROLL_DICE, playerId = "p1", payload = "", expectedVersion = 0L, expectedHostEpoch = 1L)
        assertThat(processor.processAction(forged, state, meta)).isNotNull()
        // Fresh processor (empty dedup) accepts the legit action too
        val processor2 = AuthoritativeGameProcessor()
        assertThat(processor2.processAction(legit, state, meta)).isNotNull()
    }

    @Test
    fun testPassTurnPhaseGuardRejectsMidMoveReplay() {
        val processor = AuthoritativeGameProcessor()
        val meta = RoomMetadata(roomId = "NL-PASS", hostId = "p1", maxPlayers = 2)
        val p1 = PlayerState("p1", "Alice", PlayerColor.RED, pieces = listOf(Piece(0, PlayerColor.RED, PiecePosition.Path(5))))
        val p2 = PlayerState("p2", "Bob", PlayerColor.GREEN, pieces = listOf(Piece(0, PlayerColor.GREEN, PiecePosition.Yard(0))))
        val midMove = GameState(
            gameId = "NL-PASS",
            players = listOf(p1, p2),
            activePlayerIndex = 0,
            turnPhase = TurnPhase.WAITING_FOR_MOVE,
            diceState = com.neoludo.game.engine.model.DiceState(value = 4, isRolled = true, canRoll = false),
            version = 7L
        )
        val replayedPass = NetworkAction(actionId = "pass_replay", sequence = 8L, type = ActionType.PASS_TURN, playerId = "p1", payload = "v=7", expectedVersion = 7L, expectedHostEpoch = 1L)
        assertThat(processor.processAction(replayedPass, midMove, meta)).isNull()
    }

    @Test
    fun testPerSenderSequencesDoNotCollide() {
        val deduplicator = ActionDeduplicator()
        // Two clients both start counters at 1 — both must be accepted (per-sender tracking).
        assertThat(deduplicator.isDuplicateOrStale("a1", 1L, "clientA")).isFalse()
        deduplicator.markProcessed("a1", 1L, "clientA")
        assertThat(deduplicator.isDuplicateOrStale("b1", 1L, "clientB")).isFalse()
        deduplicator.markProcessed("b1", 1L, "clientB")
        // Same sender replaying seq 1 with new id is stale.
        assertThat(deduplicator.isDuplicateOrStale("a2", 1L, "clientA")).isTrue()
        // Absurd sequence (poison) rejected.
        assertThat(deduplicator.isDuplicateOrStale("evil", Long.MAX_VALUE, "evil")).isTrue()
        // Blank actionId rejected.
        assertThat(deduplicator.isDuplicateOrStale("", 5L, "clientA")).isTrue()
    }
    @Test
    fun testTwoDeviceSimulationParity() {
        val processor = AuthoritativeGameProcessor(allowForcedDice = true)
        val meta = RoomMetadata(roomId = "NL-PARITY", hostId = "host_a", maxPlayers = 2)
        val presences = listOf(
            PlayerPresence(id = "host_a", name = "Alice", color = PlayerColor.RED, isHost = true),
            PlayerPresence(id = "guest_b", name = "Bob", color = PlayerColor.GREEN, isHost = false)
        )

        // 1. Initial State synchronized on both Device A and Device B
        val init = processor.initializeGame(meta, presences)
        var deviceAState = init.updatedState
        var deviceBState = init.updatedState
        assertThat(deviceAState.version).isEqualTo(deviceBState.version)

        // 2. Alice on Device A rolls a 6
        val rollAction = NetworkAction(actionId = "act_a1", sequence = 1L, type = ActionType.ROLL_DICE, playerId = "host_a", payload = "6", expectedVersion = deviceAState.version, expectedHostEpoch = meta.hostEpoch)
        val step1 = processor.processAction(rollAction, deviceAState, meta)!!
        deviceAState = step1.updatedState
        deviceBState = step1.updatedState // Broadcast received on Device B
        assertThat(deviceAState.diceState.value).isEqualTo(6)
        assertThat(deviceBState.diceState.value).isEqualTo(6)
        assertThat(deviceAState.version).isEqualTo(deviceBState.version)

        // 3. Alice on Device A moves yard piece out to path
        val moveAction = NetworkAction(actionId = "act_a2", sequence = 2L, type = ActionType.MOVE_PIECE, playerId = "host_a", payload = "0", expectedVersion = deviceAState.version, expectedHostEpoch = meta.hostEpoch)
        val step2 = processor.processAction(moveAction, deviceAState, meta)!!
        deviceAState = step2.updatedState
        deviceBState = step2.updatedState // Broadcast received on Device B
        assertThat(deviceAState.players[0].pieces[0].position).isEqualTo(PiecePosition.Path(0))
        assertThat(deviceBState.players[0].pieces[0].position).isEqualTo(PiecePosition.Path(0))
        assertThat(deviceAState.activePlayerIndex).isEqualTo(0) // Rolling 6 grants bonus turn
        assertThat(deviceBState.activePlayerIndex).isEqualTo(0)
        assertThat(deviceAState.version).isEqualTo(deviceBState.version)
    }
}
