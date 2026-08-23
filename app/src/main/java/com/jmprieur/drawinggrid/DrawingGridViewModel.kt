package com.jmprieur.drawinggrid

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import java.io.Serializable

data class GridSettings(
    val rows: Int = 4,
    val columns: Int = 4,
    val visible: Boolean = true,
    val color: Long = 0xFFFFFFFF,
    val opacity: Float = 0.8f,
    val thickness: Float = 2f,
) : Serializable

data class NormalizedPoint(val x: Float, val y: Float) : Serializable

data class VanishingPoint(
    val position: NormalizedPoint,
    val enabled: Boolean = true,
) : Serializable

data class PerspectiveSettings(
    val visible: Boolean = true,
    val points: List<VanishingPoint> = emptyList(),
    val anchor: NormalizedPoint? = null,
    val color: Long = 0xFFFFD740L,
    val opacity: Float = 0.9f,
    val thickness: Float = 2f,
) : Serializable

class DrawingGridViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val photoUri: StateFlow<String?> = savedStateHandle.getStateFlow(PHOTO_URI, null)
    val settings: StateFlow<GridSettings> = savedStateHandle.getStateFlow(SETTINGS, GridSettings())
    val perspective: StateFlow<PerspectiveSettings> =
        savedStateHandle.getStateFlow(PERSPECTIVE, PerspectiveSettings())

    fun selectPhoto(uri: String) {
        savedStateHandle[PHOTO_URI] = uri
        savedStateHandle[PERSPECTIVE] = PerspectiveSettings()
    }

    fun updateSettings(transform: (GridSettings) -> GridSettings) {
        savedStateHandle[SETTINGS] = transform(settings.value)
    }

    fun updatePerspective(transform: (PerspectiveSettings) -> PerspectiveSettings) {
        savedStateHandle[PERSPECTIVE] = transform(perspective.value)
    }

    private companion object {
        const val PHOTO_URI = "photo_uri"
        const val SETTINGS = "grid_settings"
        const val PERSPECTIVE = "perspective_settings"
    }
}
