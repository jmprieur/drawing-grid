package com.jmprieur.drawinggrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridGeometryTest {
    @Test
    fun `fits a portrait image inside a landscape container`() {
        assertEquals(
            ImageBounds(175f, 0f, 450f, 600f),
            GridGeometry.fittedBounds(800f, 600f, 3f, 4f),
        )
    }

    @Test
    fun `fits a landscape image inside a portrait container`() {
        assertEquals(
            ImageBounds(0f, 275f, 600f, 450f),
            GridGeometry.fittedBounds(600f, 1000f, 4f, 3f),
        )
    }

    @Test
    fun `fits a square image without letterboxing in a square container`() {
        assertEquals(
            ImageBounds(0f, 0f, 500f, 500f),
            GridGeometry.fittedBounds(500f, 500f, 1f, 1f),
        )
    }

    @Test
    fun `returns no bounds for invalid dimensions`() {
        assertNull(GridGeometry.fittedBounds(0f, 10f, 10f, 10f))
        assertNull(GridGeometry.fittedBounds(10f, 10f, 0f, 10f))
    }

    @Test
    fun `draws boundary and dividing lines for configured cells`() {
        val bounds = ImageBounds(10f, 20f, 120f, 80f)
        val lines = GridGeometry.lines(bounds, rows = 2, columns = 3)

        assertEquals(7, lines.size)
        assertEquals(GridLine(10f, 20f, 10f, 100f), lines.first())
        assertEquals(GridLine(130f, 20f, 130f, 100f), lines[3])
        assertEquals(GridLine(10f, 100f, 130f, 100f), lines.last())
    }

    @Test
    fun `rejects invalid grid cell counts`() {
        val bounds = ImageBounds(0f, 0f, 100f, 100f)
        assertEquals(emptyList<GridLine>(), GridGeometry.lines(bounds, 0, 4))
        assertEquals(emptyList<GridLine>(), GridGeometry.lines(bounds, 4, 0))
    }
}
