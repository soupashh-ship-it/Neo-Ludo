package com.neoludo.game.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerColor {
    RED,
    GREEN,
    YELLOW,
    BLUE;

    val next: PlayerColor
        get() = when (this) {
            RED -> GREEN
            GREEN -> YELLOW
            YELLOW -> BLUE
            BLUE -> RED
        }
}

@Serializable
sealed class PiecePosition {
    @Serializable
    data class Yard(val slot: Int) : PiecePosition()

    @Serializable
    data class Path(val step: Int) : PiecePosition() {
        init {
            require(step in 0..55) { "Path step must be in 0..55" }
        }
    }

    @Serializable
    data object Home : PiecePosition()
}

@Serializable
data class Piece(
    val id: Int,
    val color: PlayerColor,
    val position: PiecePosition
) {
    val isInYard: Boolean get() = position is PiecePosition.Yard
    val isOnPath: Boolean get() = position is PiecePosition.Path
    val isHome: Boolean get() = position is PiecePosition.Home
}

@Serializable
data class DiceState(
    val value: Int = 1,
    val isRolled: Boolean = false,
    val consecutiveSixes: Int = 0,
    val canRoll: Boolean = true
)

@Serializable
enum class TurnPhase {
    WAITING_FOR_ROLL,
    WAITING_FOR_MOVE,
    AUTO_ADVANCING,
    GAME_OVER
}

@Serializable
sealed class GameEngineEvent {
    @Serializable
    data class DiceRolled(val player: PlayerColor, val value: Int, val consecutiveSixes: Int) : GameEngineEvent()

    @Serializable
    data class PieceMoved(val player: PlayerColor, val pieceId: Int, val from: PiecePosition, val to: PiecePosition) : GameEngineEvent()

    @Serializable
    data class PieceCaptured(val attacker: PlayerColor, val victim: PlayerColor, val victimPieceId: Int) : GameEngineEvent()

    @Serializable
    data class PieceReachedHome(val player: PlayerColor, val pieceId: Int) : GameEngineEvent()

    @Serializable
    data class ExtraTurnGranted(val player: PlayerColor, val reason: String) : GameEngineEvent()

    @Serializable
    data class TurnForfeited3xSix(val player: PlayerColor) : GameEngineEvent()

    @Serializable
    data class TurnPassedNoMoves(val player: PlayerColor) : GameEngineEvent()

    @Serializable
    data class PlayerFinished(val player: PlayerColor, val rank: Int) : GameEngineEvent()

    @Serializable
    data class GameOver(val winner: PlayerColor, val finalRanking: List<PlayerColor>) : GameEngineEvent()
}

@Serializable
data class MoveRecord(
    val pieceId: Int,
    val playerColor: PlayerColor,
    val from: PiecePosition,
    val to: PiecePosition,
    val capturedPieceId: Int? = null,
    val diceValue: Int,
    val timestamp: Long = 0L
)

@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    val color: PlayerColor,
    val avatarId: Int = 1,
    val pieces: List<Piece> = (0..3).map { Piece(it, color, PiecePosition.Yard(it)) },
    val isBot: Boolean = false,
    val isDisconnected: Boolean = false,
    val rank: Int? = null
) {
    val hasFinished: Boolean get() = pieces.all { it.isHome }
    val piecesInHome: Int get() = pieces.count { it.isHome }
    val piecesOnPath: Int get() = pieces.count { it.isOnPath }
    val piecesInYard: Int get() = pieces.count { it.isInYard }
}

@Serializable
data class LudoRuleSet(
    val maxPlayers: Int = 4,
    val autoMoveSinglePiece: Boolean = true,
    val penalty3xSix: Boolean = true,
    val turnTimerSeconds: Int = 30
)

@Serializable
data class GameState(
    val gameId: String,
    val players: List<PlayerState>,
    val activePlayerIndex: Int = 0,
    val diceState: DiceState = DiceState(),
    val turnPhase: TurnPhase = TurnPhase.WAITING_FOR_ROLL,
    val ranking: List<PlayerColor> = emptyList(),
    val ruleSet: LudoRuleSet = LudoRuleSet(),
    val moveHistory: List<MoveRecord> = emptyList(),
    val lastEvent: GameEngineEvent? = null
) {
    val activePlayer: PlayerState get() = players[activePlayerIndex]
    val isGameOver: Boolean get() = turnPhase == TurnPhase.GAME_OVER
}
