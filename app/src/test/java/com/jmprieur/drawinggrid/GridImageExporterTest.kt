package com.jmprieur.drawinggrid

import org.junit.Assert.assertEquals
import org.junit.Test

class GridImageExporterTest {
    @Test
    fun `suggested filename keeps original base name and includes grid size`() {
        assertEquals(
            "portrait-grid4x8.png",
            GridImageExporter.suggestedFileName("portrait.jpg", rows = 4, columns = 8),
        )
    }

    @Test
    fun `suggested filename handles missing and unsafe source names`() {
        assertEquals(
            "drawing-grid2x3.png",
            GridImageExporter.suggestedFileName(null, rows = 2, columns = 3),
        )
        assertEquals(
            "folder_photo-grid2x3.png",
            GridImageExporter.suggestedFileName("folder/photo.jpeg", rows = 2, columns = 3),
        )
    }
}
