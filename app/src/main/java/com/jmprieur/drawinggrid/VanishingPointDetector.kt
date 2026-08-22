package com.jmprieur.drawinggrid

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

object VanishingPointDetector {
    private data class Line(val a: Float, val b: Float, val c: Float, val angle: Float, val votes: Int)

    fun detect(bitmap: Bitmap): List<VanishingPoint> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return emptyList()
        val step = max(1, max(width, height) / 240)
        val thetaCount = 90
        val diagonal = hypot(width.toDouble(), height.toDouble()).toInt()
        val rhoCount = diagonal * 2 + 1
        val accumulator = Array(thetaCount) { IntArray(rhoCount) }
        var edgeCount = 0

        for (y in step until height - step step step) {
            for (x in step until width - step step step) {
                val gx = luminance(bitmap.getPixel(x + step, y)) - luminance(bitmap.getPixel(x - step, y))
                val gy = luminance(bitmap.getPixel(x, y + step)) - luminance(bitmap.getPixel(x, y - step))
                if (abs(gx) + abs(gy) < 90) continue
                edgeCount++
                for (theta in 0 until thetaCount) {
                    val radians = theta * PI / thetaCount
                    val rho = (x * cos(radians) + y * sin(radians)).toInt() + diagonal
                    if (rho in 0 until rhoCount) accumulator[theta][rho]++
                }
            }
        }
        if (edgeCount < 20) return emptyList()
        val lines = buildList {
            for (theta in 0 until thetaCount) {
                for (rho in 1 until rhoCount - 1) {
                    val votes = accumulator[theta][rho]
                    if (votes < 8 || votes < accumulator[theta][rho - 1] || votes < accumulator[theta][rho + 1]) continue
                    val radians = (theta * PI / thetaCount).toFloat()
                    add(Line(cos(radians), sin(radians), (rho - diagonal).toFloat(), radians, votes))
                }
            }
        }.sortedByDescending { it.votes }.take(24)

        val intersections = mutableListOf<Pair<NormalizedPoint, Int>>()
        for (first in lines.indices) {
            for (second in first + 1 until lines.size) {
                val l1 = lines[first]
                val l2 = lines[second]
                if (abs(sin((l1.angle - l2.angle).toDouble())) < 0.15) continue
                val determinant = l1.a * l2.b - l2.a * l1.b
                if (abs(determinant) < 0.001f) continue
                val x = (l1.c * l2.b - l2.c * l1.b) / determinant
                val y = (l1.a * l2.c - l2.a * l1.c) / determinant
                val point = NormalizedPoint(x / width, y / height)
                if (point.x in -10f..11f && point.y in -10f..11f) {
                    intersections += point to (l1.votes + l2.votes)
                }
            }
        }
        val clusters = mutableListOf<MutableList<Pair<NormalizedPoint, Int>>>()
        intersections.sortedByDescending { it.second }.forEach { candidate ->
            val cluster = clusters.firstOrNull {
                val center = weightedCenter(it)
                hypot((center.x - candidate.first.x).toDouble(), (center.y - candidate.first.y).toDouble()) < 0.18
            }
            if (cluster == null && clusters.size < 6) clusters += mutableListOf(candidate) else cluster?.add(candidate)
        }
        return clusters
            .filter { it.size >= 2 }
            .sortedByDescending { cluster -> cluster.sumOf { it.second } }
            .take(3)
            .map { VanishingPoint(weightedCenter(it)) }
    }

    private fun weightedCenter(points: List<Pair<NormalizedPoint, Int>>): NormalizedPoint {
        val weight = points.sumOf { it.second }.coerceAtLeast(1)
        return NormalizedPoint(
            points.sumOf { it.first.x.toDouble() * it.second }.toFloat() / weight,
            points.sumOf { it.first.y.toDouble() * it.second }.toFloat() / weight,
        )
    }

    private fun luminance(color: Int): Int {
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        return (red * 3 + green * 6 + blue) / 10
    }
}
