package com.neoludo.game.multiplayer.sync

import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.multiplayer.model.PlayerPresence
import com.neoludo.game.multiplayer.model.RoomMetadata
import com.neoludo.game.multiplayer.model.RoomStatus

/** Pure guards used by both network transports before untrusted snapshots reach UI/engine code. */
object ProtocolSafety {
    /**
     * MQTT can deliver retained metadata late/out of order.  Never allow an
     * equal authority generation to change host identity or move the room
     * lifecycle backwards (for example IN_GAME -> LOBBY).
     */
    fun shouldAcceptMeta(previous: RoomMetadata?, incoming: RoomMetadata): Boolean {
        if (previous == null) return true
        if (incoming.hostEpoch < previous.hostEpoch) return false
        if (incoming.hostEpoch > previous.hostEpoch) return true
        if (incoming.hostId != previous.hostId) return false
        if (statusStage(incoming.status) < statusStage(previous.status)) return false
        if (previous.updatedAt > 0L && incoming.updatedAt > 0L && incoming.updatedAt < previous.updatedAt) return false
        return true
    }

    private fun statusStage(status: RoomStatus): Int = when (status) {
        RoomStatus.CREATING -> 0
        RoomStatus.LOBBY -> 1
        RoomStatus.STARTING -> 2
        RoomStatus.IN_GAME, RoomStatus.PAUSED, RoomStatus.RECONNECTING -> 3
        RoomStatus.COMPLETED, RoomStatus.ABANDONED -> 4
    }

    fun isNewerState(local: GameState?, remote: GameState): Boolean {
        if (local == null) return true
        if (remote.authorityEpoch != local.authorityEpoch) return remote.authorityEpoch > local.authorityEpoch
        if (remote.authorityHostId != local.authorityHostId) return false
        return remote.version > local.version
    }

    fun isValidMeta(meta: RoomMetadata, expectedRoomId: String): Boolean {
        if (meta.roomId != expectedRoomId) return false
        if (meta.hostId.isBlank() || meta.hostEpoch < 1L) return false
        if (meta.maxPlayers !in 2..4) return false
        if (meta.ruleSet.maxPlayers !in 2..4) return false
        if (meta.ruleSet.turnTimerSeconds !in 5..300) return false
        if (meta.turnDeadline < 0L || meta.turnStartedAt < 0L) return false
        return true
    }

    fun isValidPresence(presence: PlayerPresence, expectedId: String? = null): Boolean {
        if (presence.id.isBlank() || (expectedId != null && presence.id != expectedId)) return false
        if (presence.name.isBlank() || presence.name.length > 30) return false
        if (presence.avatarId !in 1..1000) return false
        if (presence.joinedAt < 0L || presence.lastSeen < 0L) return false
        return true
    }

    fun isValidGameState(
        state: GameState,
        expectedRoomId: String? = null,
        expectedHostId: String? = null
    ): Boolean {
        if (expectedRoomId != null && state.gameId != expectedRoomId) return false
        if (state.authorityEpoch < 1L || state.version < 0L) return false
        if (expectedHostId != null && state.authorityHostId != expectedHostId) return false
        if (state.players.size !in 2..4 || state.activePlayerIndex !in state.players.indices) return false
        if (state.players.map { it.id }.any { it.isBlank() }) return false
        if (state.players.map { it.id }.toSet().size != state.players.size) return false
        if (state.players.map { it.color }.toSet().size != state.players.size) return false
        if (state.ranking.distinct().size != state.ranking.size) return false
        if (!state.players.map { it.color }.toSet().containsAll(state.ranking)) return false
        if (state.diceState.value !in 1..6 || state.diceState.consecutiveSixes !in 0..2) return false
        if (state.ruleSet.maxPlayers !in 2..4 || state.ruleSet.turnTimerSeconds !in 5..300) return false
        if (state.turnPhase == TurnPhase.WAITING_FOR_MOVE && (!state.diceState.isRolled || state.diceState.canRoll)) return false
        if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL && state.diceState.isRolled) return false

        for (player in state.players) {
            if (player.name.isBlank() || player.name.length > 30) return false
            if (player.pieces.size != 4) return false
            if (player.pieces.map { it.id }.toSet() != setOf(0, 1, 2, 3)) return false
            if (player.pieces.any { it.color != player.color }) return false
            if (player.rank != null && player.rank !in 1..4) return false
            for (piece in player.pieces) {
                when (val pos = piece.position) {
                    is PiecePosition.Yard -> if (pos.slot !in 0..3) return false
                    is PiecePosition.Path -> if (pos.step !in 0..55) return false
                    PiecePosition.Home -> Unit
                }
            }
        }
        return true
    }
}
