package com.jmprieur.drawinggrid

import kotlin.math.min

data class ImageBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

data class GridLine(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

object GridGeometry {
    fun fittedBounds(
        containerWidth: Float,
        containerHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
    ): ImageBounds? {
        if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
            return null
        }
        val scale = min(containerWidth / imageWidth, containerHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        return ImageBounds(
            left = (containerWidth - width) / 2f,
            top = (containerHeight - height) / 2f,
            width = width,
            height = height,
        )
    }

    fun lines(bounds: ImageBounds, rows: Int, columns: Int): List<GridLine> {
        if (bounds.width <= 0f || bounds.height <= 0f || rows < 1 || columns < 1) return emptyList()
        val vertical = (0..columns).map { column ->
            val x = bounds.left + bounds.width * column / columns
            GridLine(x, bounds.top, x, bounds.bottom)
        }
        val horizontal = (0..rows).map { row ->
            val y = bounds.top + bounds.height * row / rows
            GridLine(bounds.left, y, bounds.right, y)
        }
        return vertical + horizontal
    }
}
