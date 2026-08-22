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

        assertEquals("content://example/photo", viewModel.photoUri.value)
        assertEquals(GridSettings(rows = 12, columns = 1, visible = false), viewModel.settings.value)
    }
}
