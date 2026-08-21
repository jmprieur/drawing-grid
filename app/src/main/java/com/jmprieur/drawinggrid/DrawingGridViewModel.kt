package com.jmprieur.drawinggrid

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getStateFlow
import java.io.Serializable

data class GridSettings(
    val rows: Int = 4,
    val columns: Int = 4,
    val visible: Boolean = true,
    val color: Long = 0xFFFFFFFF,
    val opacity: Float = 0.8f,
    val thickness: Float = 2f,
) : Serializable

class DrawingGridViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val photoUri: StateFlow<String?> = savedStateHandle.getStateFlow(PHOTO_URI, null)
    val settings: StateFlow<GridSettings> = savedStateHandle.getStateFlow(SETTINGS, GridSettings())

    fun selectPhoto(uri: String) {
        savedStateHandle[PHOTO_URI] = uri
    }

    fun updateSettings(transform: (GridSettings) -> GridSettings) {
        savedStateHandle[SETTINGS] = transform(settings.value)
    }

    private companion object {
        const val PHOTO_URI = "photo_uri"
        const val SETTINGS = "grid_settings"
    }
}
