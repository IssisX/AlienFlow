package io.github.issisx.alienflow

/** Pixel-space position for a measured notice, kept inside the usable screen. */
object StatusNoticePosition {
    data class Position(val x: Int, val y: Int)
    fun place(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int,
              anchorX: Int, anchorY: Int, anchorHeight: Int, gap: Int): Position {
        val maxX = maxOf(left, right - width)
        val maxY = maxOf(top, bottom - height)
        val above = anchorY - gap - height
        val below = anchorY + anchorHeight + gap
        val y = when {
            above >= top -> above
            below <= maxY -> below
            else -> anchorY
        }.coerceIn(top, maxY)
        return Position((anchorX - width / 2).coerceIn(left, maxX), y)
    }
}
