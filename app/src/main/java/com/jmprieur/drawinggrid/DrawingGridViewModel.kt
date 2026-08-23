package com.jmprieur.drawinggrid

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

class DrawingGridViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PhotoSettingsRepository,
    externalScope: CoroutineScope?,
) : ViewModel() {
    private val persistenceScope = externalScope ?: viewModelScope
    private val mutablePhotoUri = MutableStateFlow(savedStateHandle.get<String>(PHOTO_URI))
    private val mutableSettings = MutableStateFlow(GridSettings())
    private val mutablePerspective = MutableStateFlow(PerspectiveSettings())
    private var saveJob: Job? = null

    val photoUri: StateFlow<String?> = mutablePhotoUri
    val settings: StateFlow<GridSettings> = mutableSettings
    val perspective: StateFlow<PerspectiveSettings> = mutablePerspective

    init {
        persistenceScope.launch {
            val uri = mutablePhotoUri.value ?: repository.currentPhotoUri()
            if (uri != null) restorePhoto(uri)
        }
    }

    fun selectPhoto(uri: String) {
        val previousUri = mutablePhotoUri.value
        val previousSettings = PhotoSettings(mutableSettings.value, mutablePerspective.value)
        saveJob?.cancel()
        setCurrentPhoto(uri)
        mutableSettings.value = GridSettings()
        mutablePerspective.value = PerspectiveSettings()
        persistenceScope.launch {
            if (previousUri != null && previousUri != uri) repository.save(previousUri, previousSettings)
            repository.selectPhoto(uri)
            val restored = repository.load(uri)
            if (mutablePhotoUri.value == uri) {
                mutableSettings.value = restored.grid
                mutablePerspective.value = restored.perspective
            }
        }
    }

    fun updateSettings(transform: (GridSettings) -> GridSettings) {
        mutableSettings.value = transform(mutableSettings.value)
        saveCurrentPhoto()
    }

    fun updatePerspective(transform: (PerspectiveSettings) -> PerspectiveSettings) {
        mutablePerspective.value = transform(mutablePerspective.value)
        saveCurrentPhoto()
    }

    fun resetCurrentPhoto() {
        val uri = mutablePhotoUri.value ?: return
        saveJob?.cancel()
        mutableSettings.value = GridSettings()
        mutablePerspective.value = PerspectiveSettings()
        persistenceScope.launch { repository.reset(uri) }
    }

    private suspend fun restorePhoto(uri: String) {
        val restored = repository.load(uri)
        setCurrentPhoto(uri)
        mutableSettings.value = restored.grid
        mutablePerspective.value = restored.perspective
    }

    private fun setCurrentPhoto(uri: String) {
        mutablePhotoUri.value = uri
        savedStateHandle[PHOTO_URI] = uri
    }

    private fun saveCurrentPhoto() {
        val uri = mutablePhotoUri.value ?: return
        val snapshot = PhotoSettings(mutableSettings.value, mutablePerspective.value)
        saveJob?.cancel()
        saveJob = persistenceScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            repository.save(uri, snapshot)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                DrawingGridViewModel(
                    createSavedStateHandle(),
                    DataStorePhotoSettingsRepository(application),
                    null,
                )
            }
        }

        private const val PHOTO_URI = "photo_uri"
        private const val SAVE_DEBOUNCE_MILLIS = 250L
    }
}
