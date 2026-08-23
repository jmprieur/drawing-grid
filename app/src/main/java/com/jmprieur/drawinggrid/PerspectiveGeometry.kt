package com.jmprieur.drawinggrid

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Point2(val x: Float, val y: Float)

data class ViewTransform(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)

object PerspectiveGeometry {
    fun applyGesture(
        transform: ViewTransform,
        centroid: Point2,
        pan: Point2,
        zoom: Float,
    ): ViewTransform {
        val scale = (transform.scale * zoom).coerceIn(0.18f, 8f)
        val appliedZoom = scale / transform.scale
        return ViewTransform(
            scale = scale,
            offsetX = centroid.x + (transform.offsetX - centroid.x) * appliedZoom + pan.x,
            offsetY = centroid.y + (transform.offsetY - centroid.y) * appliedZoom + pan.y,
        )
    }

    fun toWorkspace(point: NormalizedPoint, image: ImageBounds, transform: ViewTransform): Point2 =
        Point2(
            x = (image.left + point.x * image.width) * transform.scale + transform.offsetX,
            y = (image.top + point.y * image.height) * transform.scale + transform.offsetY,
        )

    fun toNormalized(point: Point2, image: ImageBounds, transform: ViewTransform): NormalizedPoint =
        NormalizedPoint(
            x = ((point.x - transform.offsetX) / transform.scale - image.left) / image.width,
            y = ((point.y - transform.offsetY) / transform.scale - image.top) / image.height,
        )

    fun transformedBounds(image: ImageBounds, transform: ViewTransform): ImageBounds =
        ImageBounds(
            left = image.left * transform.scale + transform.offsetX,
            top = image.top * transform.scale + transform.offsetY,
            width = image.width * transform.scale,
            height = image.height * transform.scale,
        )

    fun fitPerspective(
        viewportWidth: Float,
        viewportHeight: Float,
        image: ImageBounds,
        points: List<NormalizedPoint>,
        minimumImageFraction: Float = 0.28f,
    ): ViewTransform {
        if (viewportWidth <= 0f || viewportHeight <= 0f || points.isEmpty()) return ViewTransform()
        val candidates = points.map { Point2(image.left + it.x * image.width, image.top + it.y * image.height) }
        val left = min(image.left, candidates.minOf { it.x })
        val top = min(image.top, candidates.minOf { it.y })
        val right = max(image.right, candidates.maxOf { it.x })
        val bottom = max(image.bottom, candidates.maxOf { it.y })
        val allScale = min(viewportWidth * 0.9f / (right - left), viewportHeight * 0.9f / (bottom - top))
        val minimumScale = min(
            viewportWidth * minimumImageFraction / image.width,
            viewportHeight * minimumImageFraction / image.height,
        )
        val scale = max(allScale, minimumScale).coerceAtMost(1f)
        val contentCenterX = (left + right) / 2f
        val contentCenterY = (top + bottom) / 2f
        return ViewTransform(
            scale = scale,
            offsetX = viewportWidth / 2f - contentCenterX * scale,
            offsetY = viewportHeight / 2f - contentCenterY * scale,
        )
    }

    fun edgeIndicator(point: Point2, width: Float, height: Float, margin: Float = 22f): Point2? {
        if (point.x in 0f..width && point.y in 0f..height) return null
        val center = Point2(width / 2f, height / 2f)
        val dx = point.x - center.x
        val dy = point.y - center.y
        if (abs(dx) < 0.001f && abs(dy) < 0.001f) return null
        val factor = min(
            if (abs(dx) < 0.001f) Float.MAX_VALUE else (width / 2f - margin) / abs(dx),
            if (abs(dy) < 0.001f) Float.MAX_VALUE else (height / 2f - margin) / abs(dy),
        )
        return Point2(center.x + dx * factor, center.y + dy * factor)
    }

    fun hitTestPosition(point: Point2, width: Float, height: Float): Point2 =
        edgeIndicator(point, width, height) ?: point

    fun movePoint(
        point: NormalizedPoint,
        delta: Point2,
        image: ImageBounds,
        transform: ViewTransform,
    ): NormalizedPoint {
        val workspace = toWorkspace(point, image, transform)
        return toNormalized(Point2(workspace.x + delta.x, workspace.y + delta.y), image, transform)
    }

    fun distance(first: Point2, second: Point2): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return sqrt(dx * dx + dy * dy)
    }
}
