package com.jmprieur.drawinggrid

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingGridViewModelTest {
    @Test
    fun `new photo uses defaults and saves changes`() = runTest {
        val repository = FakePhotoSettingsRepository()
        val viewModel = DrawingGridViewModel(SavedStateHandle(), repository, this)

        viewModel.selectPhoto("content://example/photo")
        advanceUntilIdle()
        viewModel.updateSettings { it.copy(rows = 12, columns = 1, visible = false) }
        viewModel.updatePerspective {
            it.copy(points = listOf(VanishingPoint(NormalizedPoint(1.5f, -0.2f))))
        }
        advanceUntilIdle()

        assertEquals("content://example/photo", viewModel.photoUri.value)
        assertEquals(GridSettings(rows = 12, columns = 1, visible = false), viewModel.settings.value)
        assertEquals(NormalizedPoint(1.5f, -0.2f), viewModel.perspective.value.points.single().position)
        assertEquals(viewModel.settings.value, repository.records.getValue("content://example/photo").grid)
    }

    @Test
    fun `switching photos restores each photo settings`() = runTest {
        val repository = FakePhotoSettingsRepository()
        val viewModel = DrawingGridViewModel(SavedStateHandle(), repository, this)

        viewModel.selectPhoto("photo-a")
        advanceUntilIdle()
        viewModel.updateSettings { it.copy(rows = 8) }
        advanceUntilIdle()
        viewModel.selectPhoto("photo-b")
        advanceUntilIdle()

        assertEquals(GridSettings(), viewModel.settings.value)
        viewModel.updateSettings { it.copy(columns = 9) }
        advanceUntilIdle()
        viewModel.selectPhoto("photo-a")
        advanceUntilIdle()

        assertEquals(8, viewModel.settings.value.rows)
        assertEquals(4, viewModel.settings.value.columns)
    }

    @Test
    fun `restores the last selected photo in a new view model`() = runTest {
        val repository = FakePhotoSettingsRepository(
            currentUri = "saved-photo",
            records = mutableMapOf(
                "saved-photo" to PhotoSettings(
                    grid = GridSettings(rows = 7),
                    perspective = PerspectiveSettings(anchor = NormalizedPoint(0.4f, 0.6f)),
                ),
            ),
        )

        val viewModel = DrawingGridViewModel(SavedStateHandle(), repository, this)
        advanceUntilIdle()

        assertEquals("saved-photo", viewModel.photoUri.value)
        assertEquals(7, viewModel.settings.value.rows)
        assertEquals(NormalizedPoint(0.4f, 0.6f), viewModel.perspective.value.anchor)
    }

    @Test
    fun `reset removes settings only for the current photo`() = runTest {
        val repository = FakePhotoSettingsRepository(
            records = mutableMapOf(
                "photo-a" to PhotoSettings(GridSettings(rows = 7)),
                "photo-b" to PhotoSettings(GridSettings(rows = 9)),
            ),
        )
        val viewModel = DrawingGridViewModel(SavedStateHandle(), repository, this)

        viewModel.selectPhoto("photo-a")
        advanceUntilIdle()
        viewModel.resetCurrentPhoto()
        advanceUntilIdle()

        assertEquals(GridSettings(), viewModel.settings.value)
        assertEquals(null, repository.records["photo-a"])
        assertEquals(9, repository.records.getValue("photo-b").grid.rows)
    }
}

private class FakePhotoSettingsRepository(
    private var currentUri: String? = null,
    val records: MutableMap<String, PhotoSettings> = mutableMapOf(),
) : PhotoSettingsRepository {
    override suspend fun currentPhotoUri(): String? = currentUri

    override suspend fun selectPhoto(uri: String) {
        currentUri = uri
    }

    override suspend fun load(uri: String): PhotoSettings = records[uri] ?: PhotoSettings()

    override suspend fun save(uri: String, settings: PhotoSettings) {
        records[uri] = settings
    }

    override suspend fun reset(uri: String) {
        records.remove(uri)
    }
}
