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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

// Traditional Ludo palette — one classic look for everyone.
private val ClassicRed = Color(0xFFE53935)
private val ClassicGreen = Color(0xFF43A047)
private val ClassicYellow = Color(0xFFF2B705)
private val ClassicBlue = Color(0xFF1E88E5)
private val ClassicPaper = Color(0xFFFFFFFF)
private val ClassicInk = Color(0xFF616161)
private val ClassicFrame = Color(0xFF37474F)
private val ClassicStarGray = Color(0xFF9E9E9E)
private val ClassicGold = Color(0xFFFFC107)

private fun classicColor(color: PlayerColor): Color = when (color) {
    PlayerColor.RED -> ClassicRed
    PlayerColor.GREEN -> ClassicGreen
    PlayerColor.YELLOW -> ClassicYellow
    PlayerColor.BLUE -> ClassicBlue
}

private fun darker(color: Color, factor: Float = 0.62f): Color =
    Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)

private data class ActivePieceHopState(
    val playerColor: PlayerColor,
    val pieceId: Int,
    val steps: List<PiecePosition>,
    val currentStepIndex: Int,
    val progress: Float
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
    // boardTheme / pawnSkin are intentionally ignored: one classic look.
    val pulseTransition = rememberInfiniteTransition(label = "classic_select_pulse")
    val selectPulse by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "select_pulse"
    )

    val selectablePieceIds = if (gameState.turnPhase == TurnPhase.WAITING_FOR_MOVE) {
        MoveValidator.getLegalMoves(gameState.activePlayer, gameState.diceState.value, gameState.players)
            .map { it.piece.id }
            .toSet()
    } else emptySet()

    var activeHop by remember { mutableStateOf<ActivePieceHopState?>(null) }
    val stepAnimProgress = remember { Animatable(0f) }

    // Persistent visual positions (tracks visual location of every piece on board)
    val visualPositions = remember {
        mutableStateMapOf<String, PiecePosition>().apply {
            gameState.players.forEach { p ->
                p.pieces.forEach { piece ->
                    put("${p.color}_${piece.id}", piece.position)
                }
            }
        }
    }

    LaunchedEffect(gameState) {
        val currentEnginePositions = gameState.players.flatMap { p -> p.pieces.map { "${p.color}_${it.id}" to it.position } }.toMap()

        var movingPieceInfo: Triple<PlayerColor, Piece, PiecePosition>? = null
        for (player in gameState.players) {
            for (piece in player.pieces) {
                val key = "${player.color}_${piece.id}"
                val currentVisualPos = visualPositions[key] ?: piece.position
                val engineTargetPos = piece.position
                if (currentVisualPos != engineTargetPos) {
                    movingPieceInfo = Triple(player.color, piece, currentVisualPos)
                    break
                }
            }
            if (movingPieceInfo != null) break
        }

        if (movingPieceInfo != null) {
            val (moverColor, moverPiece, fromPos) = movingPieceInfo
            val toPos = moverPiece.position
            val moverKey = "${moverColor}_${moverPiece.id}"

            val intermediateSteps = BoardCoordinates.getIntermediatePositions(moverColor, fromPos, toPos)
            if (intermediateSteps.size > 1) {
                for (i in 0 until intermediateSteps.size - 1) {
                    activeHop = ActivePieceHopState(
                        playerColor = moverColor,
                        pieceId = moverPiece.id,
                        steps = intermediateSteps,
                        currentStepIndex = i,
                        progress = 0f
                    )
                    onStepHop()
                    stepAnimProgress.snapTo(0f)
                    val isYardExit = fromPos is PiecePosition.Yard
                    val stepDuration = if (isYardExit) 180 else 115
                    stepAnimProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(stepDuration, easing = LinearOutSlowInEasing)
                    )
                    val nextStepPos = intermediateSteps[i + 1]
                    visualPositions[moverKey] = nextStepPos
                }
                activeHop = null
            }

            // Sync all visual positions to engine positions (captures return to yard now)
            currentEnginePositions.forEach { (k, targetPos) ->
                visualPositions[k] = targetPos
            }
        } else {
            currentEnginePositions.forEach { (k, v) ->
                visualPositions[k] = v
            }
        }
    }

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(gameState, activeHop) {
                    detectTapGestures { offset ->
                        if (activeHop != null || gameState.isGameOver || gameState.activePlayer.isBot || gameState.turnPhase != TurnPhase.WAITING_FOR_MOVE) {
                            return@detectTapGestures
                        }
                        val cellSize = size.width / 15f
                        val touchedPiece = findTouchedPiece(
                            touchX = offset.x,
                            touchY = offset.y,
                            cellSize = cellSize,
                            gameState = gameState,
                            visualPositions = visualPositions,
                            selectablePieceIds = selectablePieceIds
                        )
                        if (touchedPiece != null && touchedPiece.id in selectablePieceIds) {
                            onPieceClick(touchedPiece.id)
                        }
                    }
                }
        ) {
            val boardSize = size.minDimension
            val cellSize = boardSize / 15f

            drawClassicFrame(cellSize)
            drawClassicYards(cellSize)
            drawClassicPath(cellSize)
            drawClassicHomeStretches(cellSize)
            drawClassicHomeCenter(cellSize)
            drawAllStationaryPieces(
                gameState = gameState,
                visualPositions = visualPositions,
                cellSize = cellSize,
                selectablePieceIds = selectablePieceIds,
                activeHop = activeHop,
                selectPulse = selectPulse
            )
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

// ---------- classic board ----------

private fun DrawScope.drawClassicFrame(cellSize: Float) {
    drawRect(color = ClassicPaper, topLeft = Offset.Zero, size = size)
    val w = cellSize * 0.1f
    drawRect(
        color = ClassicFrame,
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        style = Stroke(width = w)
    )
}

private fun DrawScope.drawClassicYards(cellSize: Float) {
    val origins = mapOf(
        PlayerColor.RED to (0 to 0),
        PlayerColor.GREEN to (9 to 0),
        PlayerColor.YELLOW to (9 to 9),
        PlayerColor.BLUE to (0 to 9)
    )
    origins.forEach { (color, origin) ->
        val (col0, row0) = origin
        val fill = classicColor(color)
        // Colored 6x6 quadrant
        drawRect(
            color = fill,
            topLeft = Offset(col0 * cellSize, row0 * cellSize),
            size = Size(cellSize * 6f, cellSize * 6f)
        )
        // White inner court
        drawRoundRect(
            color = ClassicPaper,
            topLeft = Offset((col0 + 0.7f) * cellSize, (row0 + 0.7f) * cellSize),
            size = Size(cellSize * 4.6f, cellSize * 4.6f),
            cornerRadius = CornerRadius(cellSize * 0.45f, cellSize * 0.45f)
        )
        drawRoundRect(
            color = ClassicInk.copy(alpha = 0.55f),
            topLeft = Offset((col0 + 0.7f) * cellSize, (row0 + 0.7f) * cellSize),
            size = Size(cellSize * 4.6f, cellSize * 4.6f),
            cornerRadius = CornerRadius(cellSize * 0.45f, cellSize * 0.45f),
            style = Stroke(width = (cellSize * 0.03f).coerceAtLeast(1.5f))
        )
        // Four start circles
        val slots = BoardCoordinates.YARD_SLOT_COORDINATES.getValue(color)
        slots.forEach { (row, col) ->
            val center = Offset(col * cellSize, row * cellSize)
            drawCircle(color = ClassicPaper, radius = cellSize * 0.44f, center = center)
            drawCircle(
                color = fill,
                radius = cellSize * 0.44f,
                center = center,
                style = Stroke(width = (cellSize * 0.075f).coerceAtLeast(2f))
            )
        }
    }
}

private fun DrawScope.drawClassicPath(cellSize: Float) {
    val stroke = Stroke(width = (cellSize * 0.032f).coerceAtLeast(1.5f))
    val startOwner = mapOf(0 to PlayerColor.RED, 13 to PlayerColor.GREEN, 26 to PlayerColor.YELLOW, 39 to PlayerColor.BLUE)
    BoardCoordinates.PATH_COORDINATES.forEachIndexed { globalIdx, coord ->
        val left = coord.col * cellSize
        val top = coord.row * cellSize
        val owner = startOwner[globalIdx]
        drawRect(
            color = if (owner != null) classicColor(owner) else ClassicPaper,
            topLeft = Offset(left, top),
            size = Size(cellSize, cellSize)
        )
        drawRect(color = ClassicInk, topLeft = Offset(left, top), size = Size(cellSize, cellSize), style = stroke)
        val center = Offset(left + cellSize / 2f, top + cellSize / 2f)
        when {
            owner != null -> drawStar(center, cellSize * 0.3f, Color.White)
            globalIdx in BoardCoordinates.STAR_CELL_INDICES -> drawStar(center, cellSize * 0.3f, ClassicStarGray)
        }
    }
}

private enum class ArrowDir { EAST, SOUTH, WEST, NORTH }

private fun DrawScope.drawClassicHomeStretches(cellSize: Float) {
    val stroke = Stroke(width = (cellSize * 0.032f).coerceAtLeast(1.5f))
    val data = listOf(
        Triple(PlayerColor.RED, ArrowDir.EAST, BoardCoordinates.HOME_STRETCH_COORDINATES.getValue(PlayerColor.RED)),
        Triple(PlayerColor.GREEN, ArrowDir.SOUTH, BoardCoordinates.HOME_STRETCH_COORDINATES.getValue(PlayerColor.GREEN)),
        Triple(PlayerColor.YELLOW, ArrowDir.WEST, BoardCoordinates.HOME_STRETCH_COORDINATES.getValue(PlayerColor.YELLOW)),
        Triple(PlayerColor.BLUE, ArrowDir.NORTH, BoardCoordinates.HOME_STRETCH_COORDINATES.getValue(PlayerColor.BLUE))
    )
    data.forEach { (color, dir, cells) ->
        val fill = classicColor(color)
        cells.forEachIndexed { i, coord ->
            val left = coord.col * cellSize
            val top = coord.row * cellSize
            drawRect(color = fill, topLeft = Offset(left, top), size = Size(cellSize, cellSize))
            drawRect(color = ClassicInk, topLeft = Offset(left, top), size = Size(cellSize, cellSize), style = stroke)
            if (i == 2) {
                drawArrow(Offset(left + cellSize / 2f, top + cellSize / 2f), cellSize * 0.26f, dir, Color.White)
            }
        }
    }
}

private fun DrawScope.drawClassicHomeCenter(cellSize: Float) {
    // 3x3 center: one triangle per color, apex meeting in the middle.
    val x0 = 6f * cellSize
    val x1 = 9f * cellSize
    val cx = 7.5f * cellSize
    val cy = 7.5f * cellSize
    fun tri(color: PlayerColor, a: Offset, b: Offset) {
        val path = Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(cx, cy)
            close()
        }
        drawPath(path, classicColor(color))
        drawPath(path, Color.White, style = Stroke(width = (cellSize * 0.045f).coerceAtLeast(2f)))
    }
    tri(PlayerColor.GREEN, Offset(x0, x0), Offset(x1, x0)) // north
    tri(PlayerColor.YELLOW, Offset(x1, x0), Offset(x1, x1)) // east
    tri(PlayerColor.BLUE, Offset(x0, x1), Offset(x1, x1)) // south
    tri(PlayerColor.RED, Offset(x0, x0), Offset(x0, x1)) // west
}

private fun DrawScope.drawStar(center: Offset, rOuter: Float, color: Color) {
    val rInner = rOuter * 0.45f
    val path = Path()
    repeat(10) { i ->
        val r = if (i % 2 == 0) rOuter else rInner
        val a = -PI / 2.0 + i * PI / 5.0
        val p = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawArrow(center: Offset, size: Float, dir: ArrowDir, color: Color) {
    val path = Path()
    when (dir) {
        ArrowDir.EAST -> {
            path.moveTo(center.x + size, center.y)
            path.lineTo(center.x - size * 0.6f, center.y - size * 0.8f)
            path.lineTo(center.x - size * 0.6f, center.y + size * 0.8f)
        }
        ArrowDir.WEST -> {
            path.moveTo(center.x - size, center.y)
            path.lineTo(center.x + size * 0.6f, center.y - size * 0.8f)
            path.lineTo(center.x + size * 0.6f, center.y + size * 0.8f)
        }
        ArrowDir.SOUTH -> {
            path.moveTo(center.x, center.y + size)
            path.lineTo(center.x - size * 0.8f, center.y - size * 0.6f)
            path.lineTo(center.x + size * 0.8f, center.y - size * 0.6f)
        }
        ArrowDir.NORTH -> {
            path.moveTo(center.x, center.y - size)
            path.lineTo(center.x - size * 0.8f, center.y + size * 0.6f)
            path.lineTo(center.x + size * 0.8f, center.y + size * 0.6f)
        }
    }
    path.close()
    drawPath(path, color)
}

// ---------- classic pieces ----------

private fun DrawScope.drawAllStationaryPieces(
    gameState: GameState,
    visualPositions: Map<String, PiecePosition>,
    cellSize: Float,
    selectablePieceIds: Set<Int>,
    activeHop: ActivePieceHopState?,
    selectPulse: Float
) {
    val allPieces = gameState.players.flatMap { player ->
        player.pieces
            .filterNot { activeHop != null && activeHop.playerColor == player.color && activeHop.pieceId == it.id }
            .map { piece ->
                val key = "${player.color}_${piece.id}"
                val currentVisualPos = visualPositions[key] ?: piece.position
                Triple(player, piece, BoardCoordinates.getGridCoordForPosition(player.color, currentVisualPos))
            }
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
                val angle = (index.toDouble() / count.toDouble()) * (PI * 2.0)
                Offset(
                    (baseCenter.x + clusterRadius * cos(angle)).toFloat(),
                    (baseCenter.y + clusterRadius * sin(angle)).toFloat()
                )
            } else {
                baseCenter
            }
            val isSelectable = piece.color == gameState.activePlayer.color && piece.id in selectablePieceIds
            val pieceRadius = if (count > 1) cellSize * 0.28f else cellSize * 0.36f
            drawClassicPiece(
                center = pieceCenter,
                radius = pieceRadius,
                color = classicColor(player.color),
                isSelectable = isSelectable,
                selectPulse = selectPulse
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

    val isYardExit = stepFrom is PiecePosition.Yard

    val (rFrom, cFrom) = BoardCoordinates.getGridCoordForPosition(hopState.playerColor, stepFrom)
    val (rTo, cTo) = BoardCoordinates.getGridCoordForPosition(hopState.playerColor, stepTo)

    val currentR = (1f - stepProgress) * rFrom + stepProgress * rTo
    val currentC = (1f - stepProgress) * cFrom + stepProgress * cTo

    val baseCenter = Offset(
        currentC * cellSize + cellSize / 2f,
        currentR * cellSize + cellSize / 2f
    )

    val jumpArcHeight = if (isYardExit) cellSize * 0.85f else cellSize * 0.45f
    val jumpArc = -(sin(stepProgress * PI) * jumpArcHeight).toFloat()
    val animatedCenter = Offset(baseCenter.x, baseCenter.y + jumpArc)

    val scale = 1.0f + (sin(stepProgress * PI) * (if (isYardExit) 0.35f else 0.22f)).toFloat()
    val pieceRadius = cellSize * 0.36f * scale

    val shadowAlpha = (0.3f * (1f - 0.35f * sin(stepProgress * PI))).toFloat()
    drawCircle(
        color = Color.Black.copy(alpha = shadowAlpha),
        radius = pieceRadius * 0.9f,
        center = Offset(baseCenter.x + 2f, baseCenter.y + 3f)
    )
    drawClassicPiece(
        center = animatedCenter,
        radius = pieceRadius,
        color = classicColor(hopState.playerColor),
        isSelectable = false,
        selectPulse = 1f
    )
}

private fun DrawScope.drawClassicPiece(
    center: Offset,
    radius: Float,
    color: Color,
    isSelectable: Boolean,
    selectPulse: Float
) {
    if (isSelectable) {
        drawCircle(
            color = ClassicGold.copy(alpha = 0.28f),
            radius = radius * 1.5f * selectPulse,
            center = center
        )
        drawCircle(
            color = ClassicGold,
            radius = radius * 1.28f * selectPulse,
            center = center,
            style = Stroke(width = (radius * 0.14f).coerceAtLeast(2f))
        )
    }
    // Soft ground shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.22f),
        radius = radius,
        center = Offset(center.x + 1.5f, center.y + 2.5f)
    )
    // Classic pawn: dark rim, solid disc, white ring, highlight dot.
    drawCircle(color = darker(color), radius = radius, center = center)
    drawCircle(color = color, radius = radius * 0.86f, center = center)
    drawCircle(
        color = Color.White,
        radius = radius * 0.6f,
        center = center,
        style = Stroke(width = (radius * 0.13f).coerceAtLeast(1.5f))
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = radius * 0.15f,
        center = Offset(center.x - radius * 0.3f, center.y - radius * 0.32f)
    )
}

private fun findTouchedPiece(
    touchX: Float,
    touchY: Float,
    cellSize: Float,
    gameState: GameState,
    visualPositions: Map<String, PiecePosition>,
    selectablePieceIds: Set<Int>
): Piece? {
    if (selectablePieceIds.isEmpty()) return null

    val activePlayer = gameState.activePlayer
    val allPieces = gameState.players.flatMap { player ->
        player.pieces.map { piece ->
            val key = "${player.color}_${piece.id}"
            val visualPos = visualPositions[key] ?: piece.position
            val coord = BoardCoordinates.getGridCoordForPosition(player.color, visualPos)
            Triple(player, piece, coord)
        }
    }
    val groupedByCoord = allPieces.groupBy { it.third }

    var closestPiece: Piece? = null
    var minDistance = Float.MAX_VALUE
    val maxTouchRadius = cellSize * 0.95f

    groupedByCoord.forEach { (_, piecesAtCell) ->
        val count = piecesAtCell.size
        piecesAtCell.forEachIndexed { index, (player, piece, coord) ->
            if (player.color == activePlayer.color && piece.id in selectablePieceIds) {
                val baseCenter = Offset(
                    coord.second * cellSize + cellSize / 2f,
                    coord.first * cellSize + cellSize / 2f
                )

                val pieceCenter = if (count > 1) {
                    val clusterRadius = cellSize * 0.22f
                    val angle = (index.toDouble() / count.toDouble()) * (PI * 2.0)
                    Offset(
                        (baseCenter.x + clusterRadius * cos(angle)).toFloat(),
                        (baseCenter.y + clusterRadius * sin(angle)).toFloat()
                    )
                } else {
                    baseCenter
                }

                val dx = touchX - pieceCenter.x
                val dy = touchY - pieceCenter.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                if (dist <= maxTouchRadius && dist < minDistance) {
                    minDistance = dist
                    closestPiece = piece
                }
            }
        }
    }

    return closestPiece
}
