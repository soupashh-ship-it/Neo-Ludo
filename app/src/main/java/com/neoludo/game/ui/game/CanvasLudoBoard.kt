package com.neoludo.game.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.mutableStateListOf
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
import com.neoludo.game.core.designsystem.LudoBoardPalette
import com.neoludo.game.core.designsystem.NeoLudoColors
import com.neoludo.game.core.model.BoardTheme
import com.neoludo.game.core.model.PawnSkin
import com.neoludo.game.engine.coordinate.BoardCoordinates
import com.neoludo.game.engine.model.GameState
import com.neoludo.game.engine.model.Piece
import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor
import com.neoludo.game.engine.model.TurnPhase
import com.neoludo.game.engine.rules.MoveValidator
import kotlinx.coroutines.launch
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

data class BoardVisualParticle(
    val id: Long,
    val center: Offset,
    val color: Color,
    val isShockwave: Boolean, // true = capture shockwave, false = starburst rays
    val progress: Animatable<Float, *>
)

@Composable
fun CanvasLudoBoard(
    gameState: GameState,
    onPieceClick: (pieceId: Int) -> Unit,
    onStepHop: () -> Unit = {},
    boardTheme: BoardTheme = BoardTheme.CYBER_OBSIDIAN,
    pawnSkin: PawnSkin = PawnSkin.CYBER_PIPS,
    modifier: Modifier = Modifier
) {
    val palette = NeoLudoColors.getBoardColors(boardTheme)

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

    // Safe star rotation and pulse shield effect
    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star_rotation"
    )
    val starShieldPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_shield_pulse"
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

    // Active particles (shockwaves and starbursts)
    val activeParticles = remember { mutableStateListOf<BoardVisualParticle>() }

    LaunchedEffect(gameState) {
        val currentPositions = gameState.players.flatMap { it.pieces }.associate { it.id to it.position }
        for (player in gameState.players) {
            for (piece in player.pieces) {
                val oldPos = lastPiecePositions[piece.id]
                val newPos = piece.position
                if (oldPos != null && oldPos != newPos) {
                    // Check if piece just arrived home
                    if (newPos is PiecePosition.Home) {
                        val particleId = System.currentTimeMillis() + piece.id
                        val anim = Animatable(0f)
                        val p = BoardVisualParticle(
                            id = particleId,
                            center = Offset.Zero, // Calculated in draw pass
                            color = palette.starSafeColor,
                            isShockwave = false,
                            progress = anim
                        )
                        activeParticles.add(p)
                        launch {
                            anim.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
                            activeParticles.remove(p)
                        }
                    }
                    // Check if piece was captured (was on path, now in yard)
                    if (oldPos is PiecePosition.Path && newPos is PiecePosition.Yard) {
                        val (r, c) = BoardCoordinates.getGridCoordForPosition(player.color, oldPos)
                        val particleId = System.currentTimeMillis() + piece.id
                        val anim = Animatable(0f)
                        val p = BoardVisualParticle(
                            id = particleId,
                            center = Offset(c.toFloat(), r.toFloat()), // grid col/row marker
                            color = NeoLudoColors.getPlayerColor(player.color, boardTheme),
                            isShockwave = true,
                            progress = anim
                        )
                        activeParticles.add(p)
                        launch {
                            anim.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
                            activeParticles.remove(p)
                        }
                    }

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
            drawYardBases(cellSize, palette)

            // 2. Draw 52 Pathway Tiles with Animated Star Safe Shields
            drawPathwayCells(cellSize, palette, starRotation, starShieldPulse)

            // 3. Draw Home Stretches
            drawHomeStretches(cellSize, palette)

            // 4. Draw Center Home Quadrant Prism
            drawCenterHomePrism(cellSize, palette, starRotation)

            // 5. Draw Stationary Pieces
            drawAllStationaryPieces(
                gameState = gameState,
                cellSize = cellSize,
                selectablePieceIds = selectablePieceIds,
                activeHopPieceId = activeHop?.pieceId,
                haloScale = haloPulse,
                palette = palette,
                pawnSkin = pawnSkin
            )

            // 6. Draw Active Hopping Piece with Parabolic Bounce
            activeHop?.let { hop ->
                drawHoppingPiece(
                    hopState = hop,
                    stepProgress = stepAnimProgress.value,
                    cellSize = cellSize,
                    palette = palette,
                    pawnSkin = pawnSkin
                )
            }

            // 7. Draw Visual Particles (Shockwaves & Home Starbursts)
            drawVisualParticles(activeParticles, cellSize, palette)
        }
    }
}

private fun DrawScope.drawYardBases(cellSize: Float, palette: LudoBoardPalette) {
    val yardSize = cellSize * 6f

    // Red Yard (Top Left)
    drawYardBase(
        topLeft = Offset(0f, 0f),
        size = Size(yardSize, yardSize),
        color = palette.red,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.RED),
        cellSize = cellSize,
        palette = palette
    )

    // Green Yard (Top Right)
    drawYardBase(
        topLeft = Offset(cellSize * 9f, 0f),
        size = Size(yardSize, yardSize),
        color = palette.green,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.GREEN),
        cellSize = cellSize,
        palette = palette
    )

    // Yellow Yard (Bottom Right)
    drawYardBase(
        topLeft = Offset(cellSize * 9f, cellSize * 9f),
        size = Size(yardSize, yardSize),
        color = palette.yellow,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.YELLOW),
        cellSize = cellSize,
        palette = palette
    )

    // Blue Yard (Bottom Left)
    drawYardBase(
        topLeft = Offset(0f, cellSize * 9f),
        size = Size(yardSize, yardSize),
        color = palette.blue,
        slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(PlayerColor.BLUE),
        cellSize = cellSize,
        palette = palette
    )
}

private fun DrawScope.drawYardBase(
    topLeft: Offset,
    size: Size,
    color: Color,
    slots: List<Pair<Float, Float>>,
    cellSize: Float,
    palette: LudoBoardPalette
) {
    // Outer Yard Card
    drawRoundRect(
        color = palette.cardSurface,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = color.copy(alpha = 0.5f),
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
            listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.08f)),
            center = Offset(innerTopLeft.x + innerSize.width / 2f, innerTopLeft.y + innerSize.height / 2f),
            radius = innerSize.width * 0.75f
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
            color = palette.background,
            radius = radius,
            center = center
        )
        drawCircle(
            color = color.copy(alpha = 0.65f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.5f)
        )
    }
}

private fun DrawScope.drawPathwayCells(
    cellSize: Float,
    palette: LudoBoardPalette,
    starRotation: Float,
    starShieldPulse: Float
) {
    BoardCoordinates.PATH_COORDINATES.forEachIndexed { index, coord ->
        val topLeft = Offset(coord.col * cellSize, coord.row * cellSize)
        val rectSize = Size(cellSize, cellSize)
        val center = Offset(topLeft.x + cellSize / 2f, topLeft.y + cellSize / 2f)

        val isSafeStar = index in BoardCoordinates.STAR_CELL_INDICES
        val isStartCell = index in setOf(0, 13, 26, 39)

        val cellColor = when (index) {
            0 -> palette.red.copy(alpha = 0.35f)
            13 -> palette.green.copy(alpha = 0.35f)
            26 -> palette.yellow.copy(alpha = 0.35f)
            39 -> palette.blue.copy(alpha = 0.35f)
            else -> palette.cellPathDefault
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
            color = palette.cellBorder,
            topLeft = Offset(topLeft.x + 1.5f, topLeft.y + 1.5f),
            size = Size(rectSize.width - 3f, rectSize.height - 3f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 1f)
        )

        // Safe Cell: Rotating Star with Orbiting Pulse Shield
        if (isSafeStar) {
            // Outer rotating orbiting shield ring
            val shieldRadius = cellSize * 0.42f * starShieldPulse
            drawCircle(
                color = palette.starSafeColor.copy(alpha = 0.25f),
                radius = shieldRadius,
                center = center
            )
            drawCircle(
                color = palette.starSafeColor.copy(alpha = 0.65f),
                radius = shieldRadius,
                center = center,
                style = Stroke(width = 1.2f)
            )

            // Draw rotating star
            drawRotatedStarIcon(
                center = center,
                radius = cellSize * 0.32f,
                color = palette.starSafeColor,
                rotationDegrees = starRotation
            )
        }

        // Color Dot on Start Cells
        if (isStartCell) {
            val dotColor = when (index) {
                0 -> palette.red
                13 -> palette.green
                26 -> palette.yellow
                else -> palette.blue
            }
            drawCircle(
                color = dotColor,
                radius = cellSize * 0.18f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = cellSize * 0.18f,
                center = center,
                style = Stroke(width = 1.2f)
            )
        }
    }
}

private fun DrawScope.drawHomeStretches(cellSize: Float, palette: LudoBoardPalette) {
    BoardCoordinates.HOME_STRETCH_COORDINATES.forEach { (color, coords) ->
        val playerColor = when (color) {
            PlayerColor.RED -> palette.red
            PlayerColor.GREEN -> palette.green
            PlayerColor.YELLOW -> palette.yellow
            PlayerColor.BLUE -> palette.blue
        }
        coords.forEach { coord ->
            val topLeft = Offset(coord.col * cellSize + 1.5f, coord.row * cellSize + 1.5f)
            val rectSize = Size(cellSize - 3f, cellSize - 3f)

            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(playerColor.copy(alpha = 0.75f), playerColor.copy(alpha = 0.4f)),
                    center = Offset(topLeft.x + rectSize.width / 2f, topLeft.y + rectSize.height / 2f),
                    radius = rectSize.width
                ),
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = playerColor.copy(alpha = 0.6f),
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

private fun DrawScope.drawCenterHomePrism(cellSize: Float, palette: LudoBoardPalette, starRotation: Float) {
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
    drawPath(redPath, palette.red.copy(alpha = 0.88f))

    // Green Triangle (Top)
    val greenPath = Path().apply {
        moveTo(centerTopLeft.x, centerTopLeft.y)
        lineTo(center.x, center.y)
        lineTo(centerTopLeft.x + centerSize.width, centerTopLeft.y)
        close()
    }
    drawPath(greenPath, palette.green.copy(alpha = 0.88f))

    // Yellow Triangle (Right)
    val yellowPath = Path().apply {
        moveTo(centerTopLeft.x + centerSize.width, centerTopLeft.y)
        lineTo(center.x, center.y)
        lineTo(centerTopLeft.x + centerSize.width, centerTopLeft.y + centerSize.height)
        close()
    }
    drawPath(yellowPath, palette.yellow.copy(alpha = 0.88f))

    // Blue Triangle (Bottom)
    val bluePath = Path().apply {
        moveTo(centerTopLeft.x, centerTopLeft.y + centerSize.height)
        lineTo(center.x, center.y)
        lineTo(centerTopLeft.x + centerSize.width, centerTopLeft.y + centerSize.height)
        close()
    }
    drawPath(bluePath, palette.blue.copy(alpha = 0.88f))

    // Center Star Core
    drawCircle(palette.centerHomeColor, cellSize * 0.72f, center)
    drawCircle(palette.starSafeColor.copy(alpha = 0.85f), cellSize * 0.72f, center, style = Stroke(2.5f))
    drawRotatedStarIcon(center, cellSize * 0.48f, palette.starSafeColor, starRotation)
}

private fun DrawScope.drawRotatedStarIcon(
    center: Offset,
    radius: Float,
    color: Color,
    rotationDegrees: Float
) {
    val path = Path()
    val points = 5
    val innerRadius = radius * 0.45f
    val angleStep = Math.PI / points
    val rotationRad = (rotationDegrees * Math.PI / 180.0)

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = i * angleStep - Math.PI / 2.0 + rotationRad
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
    haloScale: Float,
    palette: LudoBoardPalette,
    pawnSkin: PawnSkin
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
            val playerColor = when (player.color) {
                PlayerColor.RED -> palette.red
                PlayerColor.GREEN -> palette.green
                PlayerColor.YELLOW -> palette.yellow
                PlayerColor.BLUE -> palette.blue
            }

            drawSinglePiece(
                center = pieceCenter,
                radius = pieceRadius,
                color = playerColor,
                isSelectable = isSelectable,
                haloScale = haloScale,
                pawnSkin = pawnSkin
            )
        }
    }
}

private fun DrawScope.drawHoppingPiece(
    hopState: ActivePieceHopState,
    stepProgress: Float,
    cellSize: Float,
    palette: LudoBoardPalette,
    pawnSkin: PawnSkin
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

    val playerColor = when (hopState.color) {
        PlayerColor.RED -> palette.red
        PlayerColor.GREEN -> palette.green
        PlayerColor.YELLOW -> palette.yellow
        PlayerColor.BLUE -> palette.blue
    }

    // Draw the elevated hopping piece
    drawSinglePiece(
        center = animatedCenter,
        radius = pieceRadius,
        color = playerColor,
        isSelectable = false,
        haloScale = 1f,
        pawnSkin = pawnSkin
    )
}

private fun DrawScope.drawSinglePiece(
    center: Offset,
    radius: Float,
    color: Color,
    isSelectable: Boolean,
    haloScale: Float,
    pawnSkin: PawnSkin
) {
    // Glowing Selection Halo
    if (isSelectable) {
        drawCircle(
            color = color.copy(alpha = 0.38f),
            radius = radius * 1.55f * haloScale,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = radius * 1.25f,
            center = center,
            style = Stroke(width = 2.5f)
        )
    }

    // Drop Shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = radius * 1.05f,
        center = Offset(center.x + 2f, center.y + 3f)
    )

    when (pawnSkin) {
        PawnSkin.CYBER_PIPS -> {
            // Glass Neon Orb
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.95f), color, Color.Black.copy(alpha = 0.35f)),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                    radius = radius * 1.2f
                ),
                radius = radius,
                center = center
            )
            // Inner Specular Ring
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = radius * 0.65f,
                center = center,
                style = Stroke(width = 2f)
            )
            // Glowing Core Pip
            drawCircle(
                color = Color.White,
                radius = radius * 0.26f,
                center = center
            )
        }
        PawnSkin.ROYAL_CROWNS -> {
            // Imperial 3D Crown
            val goldLight = Color(0xFFFFE082)
            val goldDark = Color(0xFFC79100)
            // Golden base disc
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(goldLight, goldDark),
                    center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            // Colored velvet inset
            drawCircle(
                color = color,
                radius = radius * 0.72f,
                center = center
            )
            // Center Gold Crown Point
            val crownPath = Path().apply {
                moveTo(center.x - radius * 0.45f, center.y + radius * 0.3f)
                lineTo(center.x - radius * 0.45f, center.y - radius * 0.25f)
                lineTo(center.x - radius * 0.2f, center.y + radius * 0.05f)
                lineTo(center.x, center.y - radius * 0.42f)
                lineTo(center.x + radius * 0.2f, center.y + radius * 0.05f)
                lineTo(center.x + radius * 0.45f, center.y - radius * 0.25f)
                lineTo(center.x + radius * 0.45f, center.y + radius * 0.3f)
                close()
            }
            drawPath(crownPath, goldLight)
            drawPath(crownPath, Color(0xFF5D4037), style = Stroke(width = 1.2f))
            // Center Crown Ruby
            drawCircle(Color.White, radius * 0.16f, Offset(center.x, center.y + radius * 0.15f))
        }
        PawnSkin.CRYSTAL_GEMS -> {
            // Faceted Hexagonal Gem
            val hexPath = Path()
            val points = 6
            for (i in 0 until points) {
                val angle = i * (2.0 * Math.PI / points) - Math.PI / 2.0
                val x = (center.x + radius * cos(angle)).toFloat()
                val y = (center.y + radius * sin(angle)).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()

            drawPath(
                hexPath,
                brush = Brush.linearGradient(
                    listOf(color.copy(alpha = 0.95f), color, Color.Black.copy(alpha = 0.4f)),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                )
            )
            drawPath(hexPath, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2f))

            // Inner Gem Facets
            val innerPath = Path()
            for (i in 0 until points) {
                val angle = i * (2.0 * Math.PI / points) - Math.PI / 2.0
                val x = (center.x + radius * 0.5f * cos(angle)).toFloat()
                val y = (center.y + radius * 0.5f * sin(angle)).toFloat()
                if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
            }
            innerPath.close()
            drawPath(innerPath, Color.White.copy(alpha = 0.4f), style = Stroke(width = 1.2f))
            drawCircle(Color.White, radius * 0.2f, center)
        }
    }
}

private fun DrawScope.drawVisualParticles(
    particles: List<BoardVisualParticle>,
    cellSize: Float,
    palette: LudoBoardPalette
) {
    particles.forEach { particle ->
        val progress = particle.progress.value
        if (particle.isShockwave) {
            // Capture shockwave ripple
            val center = Offset(
                particle.center.x * cellSize + cellSize / 2f,
                particle.center.y * cellSize + cellSize / 2f
            )
            val maxRadius = cellSize * 2.2f
            val currentRadius = maxRadius * progress
            val alpha = (1f - progress).coerceIn(0f, 1f)

            drawCircle(
                color = particle.color.copy(alpha = alpha * 0.35f),
                radius = currentRadius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.85f),
                radius = currentRadius,
                center = center,
                style = Stroke(width = 3f * (1f - progress * 0.5f))
            )
        } else {
            // Home Starburst Rays
            val center = Offset(cellSize * 7.5f, cellSize * 7.5f)
            val rayCount = 8
            val maxLen = cellSize * 2.8f
            val currentLen = maxLen * progress
            val alpha = (1f - progress).coerceIn(0f, 1f)

            for (i in 0 until rayCount) {
                val angle = (i.toDouble() / rayCount) * (2.0 * Math.PI)
                val startX = (center.x + cellSize * 0.6f * cos(angle)).toFloat()
                val startY = (center.y + cellSize * 0.6f * sin(angle)).toFloat()
                val endX = (center.x + (cellSize * 0.6f + currentLen) * cos(angle)).toFloat()
                val endY = (center.y + (cellSize * 0.6f + currentLen) * sin(angle)).toFloat()

                drawLine(
                    color = palette.starSafeColor.copy(alpha = alpha),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.5f * (1f - progress * 0.5f)
                )
                // Sparkling spark head
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 3.5f * (1f - progress * 0.5f),
                    center = Offset(endX, endY)
                )
            }
        }
    }
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
