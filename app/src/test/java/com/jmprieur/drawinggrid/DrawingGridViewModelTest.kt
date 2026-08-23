package com.jmprieur.drawinggrid

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingGridViewModelTest {
    @Test
    fun `persists a selected photo and grid changes`() {
        val viewModel = DrawingGridViewModel(SavedStateHandle())

        viewModel.selectPhoto("content://example/photo")
        viewModel.updateSettings { it.copy(rows = 12, columns = 1, visible = false) }
        viewModel.updatePerspective {
            it.copy(points = listOf(VanishingPoint(NormalizedPoint(1.5f, -0.2f))))
        }

        assertEquals("content://example/photo", viewModel.photoUri.value)
        assertEquals(GridSettings(rows = 12, columns = 1, visible = false), viewModel.settings.value)
        assertEquals(NormalizedPoint(1.5f, -0.2f), viewModel.perspective.value.points.single().position)
    }

    @Test
    fun `selecting another photo resets perspective state`() {
        val viewModel = DrawingGridViewModel(SavedStateHandle())
        viewModel.updatePerspective { it.copy(anchor = NormalizedPoint(0.4f, 0.6f)) }

        viewModel.selectPhoto("content://example/new-photo")

        assertEquals(PerspectiveSettings(), viewModel.perspective.value)
    }
}
