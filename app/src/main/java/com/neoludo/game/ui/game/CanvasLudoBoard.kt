package com.neoludo.game.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.engine.coordinate.BoardCoordinates
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.engine.rules.MoveValidator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ActivePieceHopState(
    val pieceId: Int,
    val color: PlayerColor,
    val steps: List<PiecePosition>,
    val currentStepIndex: Int,
    val progress: Float
)

@Composable
fun CanvasLudoBoard(
    gameState: GameState,
    onPieceClick: (pieceId: Int) -> Unit,
    onStepHop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "board_halo")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    val selectablePieceIds = if (gameState.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
        MoveValidator.getLegalMoves(gameState.activePlayer, gameState.diceState.value, gameState.players)
            .map { it.piece.id }
            .toSet()
    } else emptySet()

    // Animation tracking for step-by-step hopping
    var activeHop by remember { mutableStateOf<ActivePieceHopState?>(null) }
    val stepAnimProgress = remember { Animatable(0f) }
    var lastPiecePositions by remember {
        mutableStateOf(
            gameState.players.flatMap { it.pieces }.associate { it.id to it.position }
        )
    }

    LaunchedEffect(gameState) {
        val currentPositions = gameState.players.flatMap { it.pieces }.associate { it.id to it.position }
        for (player in gameState.players) {
            for (piece in player.pieces) {
                val oldPos = lastPiecePositions[piece.id]
                val newPos = piece.position
                if (oldPos != null && oldPos != newPos) {
                    val intermediate = BoardCoordinates.getIntermediatePositions(player.color, oldPos, newPos)
                    if (intermediate.size > 1) {
                        // Animate tile by tile
                        for (i in 0 until intermediate.size - 1) {
                            activeHop = ActivePieceHopState(
                                pieceId = piece.id,
                                color = player.color,
                                steps = intermediate,
                                currentStepIndex = i,
                                progress = 0f
                            )
                            onStepHop()
                            stepAnimProgress.snapTo(0f)
                            stepAnimProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(135, easing = LinearOutSlowInEasing)
                            )
                        }
                        activeHop = null
                    }
                }
            }
        }
        lastPiecePositions = currentPositions
    }

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(gameState) {
                    detectTapGestures { offset ->
                        val cellSize = size.width / 15f
                        val touchedPiece = findTouchedPiece(
                            touchX = offset.x,
                            touchY = offset.y,
                            cellSize = cellSize,
                            gameState = gameState,
                            selectablePieceIds = selectablePieceIds
                        )
                        if (touchedPiece != null) {
                            onPieceClick(touchedPiece.id)
                        }
                    }
                }
        ) {
            val boardSize = size.minDimension
            val cellSize = boardSize / 15f

            // 1. Draw Yard Quadrants
            drawYardBases(cellSize)

            // 2. Draw 52 Pathway Tiles
            drawPathwayCells(cellSize)

            // 3. Draw Home Stretches
            drawHomeStretches(cellSize)

            // 4. Draw Center Home Quadrant Prism
            drawCenterHomePrism(cellSize)

            // 5. Draw Stationary Pieces
            drawAllStationaryPieces(
                gameState = gameState,
                cellSize = cellSize,
                selectablePieceIds = selectablePieceIds,
                activeHopPieceId = activeHop?.pieceId,
                haloScale = haloPulse
            )

            // 6. Draw Active Hopping Piece with Parabolic Bounce
            activeHop?.let { hop ->
                drawHoppingPiece(
                    hopState = hop,
                    stepProgress = stepAnimProgress.value,
                    cellSize = cellSize
                )
            }
        }
    }
}

private fun DrawScope.drawYardBases(cellSize: Float) {
    val yardSize = cellSize * 6f

    // Red Yard (Top Left)
    drawYardBase(
        topLeft = Offset(0f, 0f),
        size = Size(yardSize, yardSize),
        color = NeoLudoColors.RubyRed,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.RED),
        cellSize = cellSize
    )

    // Green Yard (Top Right)
    drawYardBase(
        topLeft = Offset(cellSize * 9f, 0f),
        size = Size(yardSize, yardSize),
        color = NeoLudoColors.EmeraldGreen,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.GREEN),
        cellSize = cellSize
    )

    // Yellow Yard (Bottom Right)
    drawYardBase(
        topLeft = Offset(cellSize * 9f, cellSize * 9f),
        size = Size(yardSize, yardSize),
        color = NeoLudoColors.AmberYellow,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.YELLOW),
        cellSize = cellSize
    )

    // Blue Yard (Bottom Left)
    drawYardBase(
        topLeft = Offset(0f, cellSize * 9f),
        size = Size(yardSize, yardSize),
        color = NeoLudoColors.CobaltBlue,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.BLUE),
        cellSize = cellSize
    )
}

private fun DrawScope.drawYardBase(
    topLeft: Offset,
    size: Size,
    color: Color,
    slots: List<Pair<Float, Float>>,
    cellSize: Float
) {
    // Outer Yard Card
    drawRoundRect(
        color = NeoLudoColors.ObsidianSurfaceCard,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = color.copy(alpha = 0.4f),
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(24f, 24f),
        style = Stroke(width = 3f)
    )

    // Inner Colored Inset Plate
    val inset = cellSize * 0.7f
    val innerTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
    val innerSize = Size(size.width - inset * 2, size.height - inset * 2)

    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.08f)),
            center = Offset(innerTopLeft.x + innerSize.width / 2f, innerTopLeft.y + innerSize.height / 2f),
            radius = innerSize.width * 0.7f
        ),
        topLeft = innerTopLeft,
        size = innerSize,
        cornerRadius = CornerRadius(16f, 16f)
    )

    // Circular Recessed Pawn Slots
    slots.forEach { (r, c) ->
        val center = Offset(c * cellSize + cellSize / 2f, r * cellSize + cellSize / 2f)
        val radius = cellSize * 0.65f

        drawCircle(
            color = NeoLudoColors.ObsidianBackground,
            radius = radius,
            center = center
        )
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.5f)
        )
    }
}

private fun DrawScope.drawPathwayCells(cellSize: Float) {
    BoardCoordinates.PATH_COORDINATES.forEachIndexed { index, coord ->
        val topLeft = Offset(coord.col * cellSize, coord.row * cellSize)
        val rectSize = Size(cellSize, cellSize)

        val isSafeStar = index in BoardCoordinates.STAR_CELL_INDICES
        val isStartCell = index in setOf(0, 13, 26, 39)

        val cellColor = when (index) {
            0 -> NeoLudoColors.RubyRed.copy(alpha = 0.35f)
            13 -> NeoLudoColors.EmeraldGreen.copy(alpha = 0.35f)
            26 -> NeoLudoColors.AmberYellow.copy(alpha = 0.35f)
            39 -> NeoLudoColors.CobaltBlue.copy(alpha = 0.35f)
            else -> NeoLudoColors.ObsidianSurfaceCard
        }

        // Tile background
        drawRoundRect(
            color = cellColor,
            topLeft = Offset(topLeft.x + 1.5f, topLeft.y + 1.5f),
            size = Size(rectSize.width - 3f, rectSize.height - 3f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Tile border
        drawRoundRect(
            color = NeoLudoColors.ObsidianBorder,
            topLeft = Offset(topLeft.x + 1.5f, topLeft.y + 1.5f),
            size = Size(rectSize.width - 3f, rectSize.height - 3f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 1f)
        )

        // Star on safe cells
        if (isSafeStar) {
            drawStarIcon(
                center = Offset(topLeft.x + cellSize / 2f, topLeft.y + cellSize / 2f),
                radius = cellSize * 0.32f,
                color = NeoLudoColors.AmberYellow
            )
        }

        // Color Dot on Start Cells
        if (isStartCell) {
            val dotColor = when (index) {
                0 -> NeoLudoColors.RubyRed
                13 -> NeoLudoColors.EmeraldGreen
                26 -> NeoLudoColors.AmberYellow
                else -> NeoLudoColors.CobaltBlue
            }
            drawCircle(
                color = dotColor,
                radius = cellSize * 0.18f,
                center = Offset(topLeft.x + cellSize / 2f, topLeft.y + cellSize / 2f)
            )
        }
    }
}

private fun DrawScope.drawHomeStretches(cellSize: Float) {
    BoardCoordinates.HOME_STRETCH_COORDINATES.forEach { (color, coords) ->
        val playerColor = NeoLudoColors.getPlayerColor(color)
        coords.forEach { coord ->
            val topLeft = Offset(coord.col * cellSize + 1.5f, coord.row * cellSize + 1.5f)
            val rectSize = Size(cellSize - 3f, cellSize - 3f)

            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(playerColor.copy(alpha = 0.7f), playerColor.copy(alpha = 0.35f)),
                    center = Offset(topLeft.x + rectSize.width / 2f, topLeft.y + rectSize.height / 2f),
                    radius = rectSize.width
                ),
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = playerColor.copy(alpha = 0.5f),
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

private fun DrawScope.drawCenterHomePrism(cellSize: Float) {
    val centerTopLeft = Offset(cellSize * 6f, cellSize * 6f)
    val centerSize = Size(cellSize * 3f, cellSize * 3f)
    val center = Offset(centerTopLeft.x + centerSize.width / 2f, centerTopLeft.y + centerSize.height / 2f)

    // Red Triangle (Left)
    val redPath = Path().apply {
        moveTo(centerTopLeft.x, centerTopLeft.y)
        lineTo(center.x, center.y)
        lineTo(centerTopLeft.x, centerTopLeft.y + centerSize.height)
        close()
    }
    drawPath(redPath, NeoLudoColors.RubyRed.copy(alpha = 0.85f))

    // Green Triangle (Top)
    val greenPath = Path().apply {
        moveTo(centerTopLeft.x, centerTopLeft.y)
        lineTo(center.x, center.y)
        lineTo(centerTopLeft.x + centerSize.width, centerTopLeft.y)
        close()
    }
    drawPath(greenPath, NeoLudoColors.EmeraldGreen.copy(alpha = 0.85f))

    // Yellow Triangle (Right)
    val yellowPath = Path().apply {
        moveTo(centerTopLeft.x + centerSize.width, centerTopLeft.y)
        lineTo(center.x, center.y)
        lineTo(centerTopLeft.x + centerSize.width, centerTopLeft.y + centerSize.height)
        close()
    }
    drawPath(yellowPath, NeoLudoColors.AmberYellow.copy(alpha = 0.85f))

    // Blue Triangle (Bottom)
    val bluePath = Path().apply {
        moveTo(centerTopLeft.x, centerTopLeft.y + centerSize.height)
        lineTo(center.x, center.y)
        lineTo(centerTopLeft.x + centerSize.width, centerTopLeft.y + centerSize.height)
        close()
    }
    drawPath(bluePath, NeoLudoColors.CobaltBlue.copy(alpha = 0.85f))

    // Center Gold Star
    drawCircle(NeoLudoColors.ObsidianSurfaceCard, cellSize * 0.65f, center)
    drawCircle(Color.White.copy(alpha = 0.7f), cellSize * 0.65f, center, style = Stroke(2f))
    drawStarIcon(center, cellSize * 0.45f, Color.White)
}

private fun DrawScope.drawStarIcon(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val points = 5
    val innerRadius = radius * 0.45f
    val angleStep = Math.PI / points

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = i * angleStep - Math.PI / 2.0
        val x = (center.x + r * cos(angle)).toFloat()
        val y = (center.y + r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Fill)
}

private fun DrawScope.drawAllStationaryPieces(
    gameState: GameState,
    cellSize: Float,
    selectablePieceIds: Set<Int>,
    activeHopPieceId: Int?,
    haloScale: Float
) {
    val allPieces = gameState.players.flatMap { player ->
        player.pieces
            .filter { it.id != activeHopPieceId }
            .map { piece -> Triple(player, piece, BoardCoordinates.getGridCoordForPosition(player.color, piece.position)) }
    }

    val groupedByCoord = allPieces.groupBy { it.third }

    groupedByCoord.forEach { (_, piecesAtCell) ->
        val count = piecesAtCell.size
        piecesAtCell.forEachIndexed { index, (player, piece, coord) ->
            val baseCenter = Offset(
                coord.second * cellSize + cellSize / 2f,
                coord.first * cellSize + cellSize / 2f
            )

            val pieceCenter = if (count > 1) {
                val clusterRadius = cellSize * 0.22f
                val angle = (index.toDouble() / count.toDouble()) * (Math.PI * 2.0)
                Offset(
                    (baseCenter.x + clusterRadius * cos(angle)).toFloat(),
                    (baseCenter.y + clusterRadius * sin(angle)).toFloat()
                )
            } else {
                baseCenter
            }

            val isSelectable = piece.color == gameState.activePlayer.color && piece.id in selectablePieceIds
            val pieceRadius = if (count > 1) cellSize * 0.28f else cellSize * 0.36f

            drawSinglePiece(
                center = pieceCenter,
                radius = pieceRadius,
                color = NeoLudoColors.getPlayerColor(player.color),
                isSelectable = isSelectable,
                haloScale = haloScale
            )
        }
    }
}

private fun DrawScope.drawHoppingPiece(
    hopState: ActivePieceHopState,
    stepProgress: Float,
    cellSize: Float
) {
    val stepFrom = hopState.steps.getOrNull(hopState.currentStepIndex) ?: return
    val stepTo = hopState.steps.getOrNull(hopState.currentStepIndex + 1) ?: stepFrom

    val (rFrom, cFrom) = BoardCoordinates.getGridCoordForPosition(hopState.color, stepFrom)
    val (rTo, cTo) = BoardCoordinates.getGridCoordForPosition(hopState.color, stepTo)

    val currentR = (1f - stepProgress) * rFrom + stepProgress * rTo
    val currentC = (1f - stepProgress) * cFrom + stepProgress * cTo

    val baseCenter = Offset(
        currentC * cellSize + cellSize / 2f,
        currentR * cellSize + cellSize / 2f
    )

    // Parabolic vertical jump arc: max jump height at progress = 0.5
    val jumpArc = -(sin(stepProgress * PI) * (cellSize * 0.5f)).toFloat()
    val animatedCenter = Offset(baseCenter.x, baseCenter.y + jumpArc)

    // Scale bounce during jump
    val scale = 1.0f + (sin(stepProgress * PI) * 0.28f).toFloat()
    val baseRadius = cellSize * 0.36f
    val pieceRadius = baseRadius * scale

    // Dynamic ground shadow directly under the hopping piece
    val shadowAlpha = (0.45f * (1f - 0.35f * sin(stepProgress * PI))).toFloat()
    val shadowRadius = (baseRadius * (1f - 0.2f * sin(stepProgress * PI))).toFloat()
    drawCircle(
        color = Color.Black.copy(alpha = shadowAlpha),
        radius = shadowRadius,
        center = Offset(baseCenter.x + 2f, baseCenter.y + 3f)
    )

    // Draw the elevated hopping piece
    drawSinglePiece(
        center = animatedCenter,
        radius = pieceRadius,
        color = NeoLudoColors.getPlayerColor(hopState.color),
        isSelectable = false,
        haloScale = 1f
    )
}

private fun DrawScope.drawSinglePiece(
    center: Offset,
    radius: Float,
    color: Color,
    isSelectable: Boolean,
    haloScale: Float
) {
    // Glowing Selection Halo
    if (isSelectable) {
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = radius * 1.55f * haloScale,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = radius * 1.25f,
            center = center,
            style = Stroke(width = 2.5f)
        )
    }

    // Shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = radius * 1.05f,
        center = Offset(center.x + 2f, center.y + 3f)
    )

    // Token Body Gradient
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = 0.95f), color, Color.Black.copy(alpha = 0.3f)),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
            radius = radius * 1.2f
        ),
        radius = radius,
        center = center
    )

    // Inner Specular Ring
    drawCircle(
        color = Color.White.copy(alpha = 0.6f),
        radius = radius * 0.65f,
        center = center,
        style = Stroke(width = 2f)
    )

    // Center Jewel Pip
    drawCircle(
        color = Color.White,
        radius = radius * 0.25f,
        center = center
    )
}

private fun findTouchedPiece(
    touchX: Float,
    touchY: Float,
    cellSize: Float,
    gameState: GameState,
    selectablePieceIds: Set<Int>
): Piece? {
    if (selectablePieceIds.isEmpty()) return null

    val activePlayer = gameState.activePlayer
    var closestPiece: Piece? = null
    var minDistance = Float.MAX_VALUE
    val maxTouchRadius = cellSize * 0.9f

    for (piece in activePlayer.pieces) {
        if (piece.id !in selectablePieceIds) continue
        val (row, col) = BoardCoordinates.getGridCoordForPosition(activePlayer.color, piece.position)
        val pieceCenterX = col * cellSize + cellSize / 2f
        val pieceCenterY = row * cellSize + cellSize / 2f

        val dx = touchX - pieceCenterX
        val dy = touchY - pieceCenterY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

        if (dist <= maxTouchRadius && dist < minDistance) {
            minDistance = dist
            closestPiece = piece
        }
    }

    return closestPiece
}
