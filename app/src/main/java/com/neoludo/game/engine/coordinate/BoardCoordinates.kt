package com.neoludo.game.engine.coordinate

import com.neoludo.game.engine.model.PiecePosition
import com.neoludo.game.engine.model.PlayerColor

data class GridCoord(val row: Int, val col: Int)

enum class CellType {
    REGULAR,
    START_RED,
    START_GREEN,
    START_YELLOW,
    START_BLUE,
    STAR_SAFE,
    HOME_STRETCH_RED,
    HOME_STRETCH_GREEN,
    HOME_STRETCH_YELLOW,
    HOME_STRETCH_BLUE,
    HOME_CENTER,
    YARD_RED,
    YARD_GREEN,
    YARD_YELLOW,
    YARD_BLUE
}

object BoardCoordinates {
    const val BOARD_GRID_SIZE = 15
    const val TOTAL_PATH_CELLS = 52
    const val HOME_STEP = 56

    val SAFE_CELL_INDICES = setOf(0, 8, 13, 21, 26, 34, 39, 47)
    val STAR_CELL_INDICES = setOf(8, 21, 34, 47)

    fun getStartOffset(color: PlayerColor): Int = when (color) {
        PlayerColor.RED -> 0
        PlayerColor.GREEN -> 13
        PlayerColor.YELLOW -> 26
        PlayerColor.BLUE -> 39
    }

    val PATH_COORDINATES: List<GridCoord> = listOf(
        // 0..4 (Red Start to corner)
        GridCoord(6, 1), GridCoord(6, 2), GridCoord(6, 3), GridCoord(6, 4), GridCoord(6, 5),
        // 5..10 (Up towards top)
        GridCoord(5, 6), GridCoord(4, 6), GridCoord(3, 6), GridCoord(2, 6), GridCoord(1, 6), GridCoord(0, 6),
        // 11 (Top center peak)
        GridCoord(0, 7),
        // 12..17 (Down right from top)
        GridCoord(0, 8), GridCoord(1, 8), GridCoord(2, 8), GridCoord(3, 8), GridCoord(4, 8), GridCoord(5, 8),
        // 18..23 (Right arm towards right)
        GridCoord(6, 9), GridCoord(6, 10), GridCoord(6, 11), GridCoord(6, 12), GridCoord(6, 13), GridCoord(6, 14),
        // 24 (Right center peak)
        GridCoord(7, 14),
        // 25..30 (Left from right arm)
        GridCoord(8, 14), GridCoord(8, 13), GridCoord(8, 12), GridCoord(8, 11), GridCoord(8, 10), GridCoord(8, 9),
        // 31..36 (Down into bottom right)
        GridCoord(9, 8), GridCoord(10, 8), GridCoord(11, 8), GridCoord(12, 8), GridCoord(13, 8), GridCoord(14, 8),
        // 37 (Bottom center peak)
        GridCoord(14, 7),
        // 38..43 (Up from bottom left)
        GridCoord(14, 6), GridCoord(13, 6), GridCoord(12, 6), GridCoord(11, 6), GridCoord(10, 6), GridCoord(9, 6),
        // 44..49 (Left arm back to red)
        GridCoord(8, 5), GridCoord(8, 4), GridCoord(8, 3), GridCoord(8, 2), GridCoord(8, 1), GridCoord(8, 0),
        // 50..51 (Left peak and turn)
        GridCoord(7, 0), GridCoord(6, 0)
    )

    val HOME_STRETCH_COORDINATES: Map<PlayerColor, List<GridCoord>> = mapOf(
        PlayerColor.RED to listOf(
            GridCoord(7, 1), GridCoord(7, 2), GridCoord(7, 3), GridCoord(7, 4), GridCoord(7, 5)
        ),
        PlayerColor.GREEN to listOf(
            GridCoord(1, 7), GridCoord(2, 7), GridCoord(3, 7), GridCoord(4, 7), GridCoord(5, 7)
        ),
        PlayerColor.YELLOW to listOf(
            GridCoord(7, 13), GridCoord(7, 12), GridCoord(7, 11), GridCoord(7, 10), GridCoord(7, 9)
        ),
        PlayerColor.BLUE to listOf(
            GridCoord(13, 7), GridCoord(12, 7), GridCoord(11, 7), GridCoord(10, 7), GridCoord(9, 7)
        )
    )

    val HOME_CENTER_COORD = GridCoord(7, 7)

    val YARD_SLOT_COORDINATES: Map<PlayerColor, List<Pair<Float, Float>>> = mapOf(
        PlayerColor.RED to listOf(
            1.5f to 1.5f, 1.5f to 3.5f, 3.5f to 1.5f, 3.5f to 3.5f
        ),
        PlayerColor.GREEN to listOf(
            1.5f to 10.5f, 1.5f to 12.5f, 3.5f to 10.5f, 3.5f to 12.5f
        ),
        PlayerColor.YELLOW to listOf(
            10.5f to 10.5f, 10.5f to 12.5f, 12.5f to 10.5f, 12.5f to 12.5f
        ),
        PlayerColor.BLUE to listOf(
            10.5f to 1.5f, 10.5f to 3.5f, 12.5f to 1.5f, 12.5f to 3.5f
        )
    )

    fun globalPathIndex(color: PlayerColor, relativeStep: Int): Int {
        require(relativeStep in 0..50) { "Relative step must be in 0..50 for main path" }
        return (getStartOffset(color) + relativeStep) % TOTAL_PATH_CELLS
    }

    fun isSafeCell(color: PlayerColor, relativeStep: Int): Boolean {
        if (relativeStep < 0 || relativeStep > 50) return true // Yard & Home stretch are private / safe
        val globalIdx = globalPathIndex(color, relativeStep)
        return globalIdx in SAFE_CELL_INDICES
    }

    fun getGridCoordForPosition(color: PlayerColor, position: PiecePosition): Pair<Float, Float> {
        return when (position) {
            is PiecePosition.Yard -> {
                val slots = YARD_SLOT_COORDINATES[color] ?: YARD_SLOT_COORDINATES.getValue(PlayerColor.RED)
                slots.getOrElse(position.slot.coerceIn(0, 3)) { 1.5f to 1.5f }
            }
            is PiecePosition.Path -> {
                when {
                    position.step in 0..50 -> {
                        val globalIdx = globalPathIndex(color, position.step)
                        val coord = PATH_COORDINATES[globalIdx]
                        coord.row.toFloat() to coord.col.toFloat()
                    }
                    position.step in 51..55 -> {
                        val stretch = HOME_STRETCH_COORDINATES[color] ?: HOME_STRETCH_COORDINATES.getValue(PlayerColor.RED)
                        val coord = stretch[position.step - 51]
                        coord.row.toFloat() to coord.col.toFloat()
                    }
                    else -> HOME_CENTER_COORD.row.toFloat() to HOME_CENTER_COORD.col.toFloat()
                }
            }
            is PiecePosition.Home -> HOME_CENTER_COORD.row.toFloat() to HOME_CENTER_COORD.col.toFloat()
        }
    }

    fun getIntermediatePositions(
        color: PlayerColor,
        from: PiecePosition,
        to: PiecePosition
    ): List<PiecePosition> {
        if (from == to) return listOf(from)

        return when {
            from is PiecePosition.Yard && to is PiecePosition.Path -> {
                listOf(from, PiecePosition.Path(0))
            }
            from is PiecePosition.Path && to is PiecePosition.Path -> {
                if (to.step > from.step) {
                    listOf(from) + (from.step + 1..to.step).map { PiecePosition.Path(it) }
                } else {
                    listOf(from, to)
                }
            }
            from is PiecePosition.Path && to is PiecePosition.Home -> {
                val list = mutableListOf<PiecePosition>(from)
                for (s in (from.step + 1)..55) {
                    list.add(PiecePosition.Path(s))
                }
                list.add(PiecePosition.Home)
                list
            }
            from is PiecePosition.Path && to is PiecePosition.Yard -> {
                listOf(from, to)
            }
            else -> listOf(from, to)
        }
    }
}
