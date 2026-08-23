package com.jmprieur.drawinggrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `normalized points outside image map beyond displayed bounds`() {
        val image = ImageBounds(100f, 50f, 400f, 200f)
        val transform = ViewTransform(scale = 0.5f, offsetX = 10f, offsetY = 20f)

        val workspace = PerspectiveGeometry.toWorkspace(NormalizedPoint(1.5f, -0.5f), image, transform)

        assertEquals(Point2(360f, -5f), workspace)
        assertEquals(
            NormalizedPoint(1.5f, -0.5f),
            PerspectiveGeometry.toNormalized(workspace, image, transform),
        )
    }

    @Test
    fun `fit perspective keeps image at a usable size for distant points`() {
        val transform = PerspectiveGeometry.fitPerspective(
            viewportWidth = 1000f,
            viewportHeight = 800f,
            image = ImageBounds(100f, 100f, 800f, 600f),
            points = listOf(NormalizedPoint(20f, 0.5f)),
        )

        assertTrue(transform.scale >= 0.28f)
    }

    @Test
    fun `outside point gets an edge indicator`() {
        val indicator = PerspectiveGeometry.edgeIndicator(Point2(900f, 300f), 600f, 400f)

        assertEquals(578f, indicator!!.x, 0.001f)
        assertEquals(246.33333f, indicator.y, 0.001f)
        assertNull(PerspectiveGeometry.edgeIndicator(Point2(300f, 200f), 600f, 400f))
    }

    @Test
    fun `consecutive gestures start from the latest transform`() {
        val first = PerspectiveGeometry.applyGesture(
            ViewTransform(),
            centroid = Point2(100f, 100f),
            pan = Point2(20f, 10f),
            zoom = 2f,
        )
        val second = PerspectiveGeometry.applyGesture(
            first,
            centroid = Point2(100f, 100f),
            pan = Point2(-5f, 15f),
            zoom = 1.5f,
        )

        assertEquals(ViewTransform(3f, -175f, -170f), second)
    }

    @Test
    fun `visible point hit position and drag follow the point`() {
        val image = ImageBounds(0f, 0f, 400f, 300f)
        val point = NormalizedPoint(0.5f, 0.5f)
        val workspace = PerspectiveGeometry.toWorkspace(point, image, ViewTransform())

        assertEquals(workspace, PerspectiveGeometry.hitTestPosition(workspace, 600f, 400f))
        assertEquals(
            NormalizedPoint(0.55f, 0.6f),
            PerspectiveGeometry.movePoint(point, Point2(20f, 30f), image, ViewTransform()),
        )
    }

    @Test
    fun `off-screen point hit position uses indicator and drag preserves distance`() {
        val image = ImageBounds(0f, 0f, 400f, 300f)
        val transform = ViewTransform(scale = 2f, offsetX = 10f, offsetY = -20f)
        val point = NormalizedPoint(2f, -1f)
        val workspace = PerspectiveGeometry.toWorkspace(point, image, transform)
        val indicator = PerspectiveGeometry.edgeIndicator(workspace, 600f, 400f)

        assertEquals(indicator, PerspectiveGeometry.hitTestPosition(workspace, 600f, 400f))
        assertEquals(
            NormalizedPoint(2.025f, -0.95f),
            PerspectiveGeometry.movePoint(point, Point2(20f, 30f), image, transform),
        )
    }
}
