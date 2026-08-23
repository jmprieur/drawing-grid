package com.jmprieur.drawinggrid

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

data class PhotoSettings(
    val grid: GridSettings = GridSettings(),
    val perspective: PerspectiveSettings = PerspectiveSettings(),
)

interface PhotoSettingsRepository {
    suspend fun currentPhotoUri(): String?
    suspend fun selectPhoto(uri: String)
    suspend fun load(uri: String): PhotoSettings
    suspend fun save(uri: String, settings: PhotoSettings)
    suspend fun reset(uri: String)
}

private val Context.photoSettingsDataStore by preferencesDataStore(name = "photo_settings")

class DataStorePhotoSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : PhotoSettingsRepository {
    constructor(context: Context) : this(context.photoSettingsDataStore)

    override suspend fun currentPhotoUri(): String? =
        dataStore.data.first()[CURRENT_PHOTO]

    override suspend fun selectPhoto(uri: String) {
        dataStore.edit { preferences -> preferences[CURRENT_PHOTO] = uri }
    }

    override suspend fun load(uri: String): PhotoSettings {
        val values = dataStore.data.first()
        val key = photoKey(uri)
        if (values[booleanPreferencesKey("$key.exists")] != true) return PhotoSettings()

        val grid = GridSettings(
            rows = values[intPreferencesKey("$key.grid.rows")] ?: 4,
            columns = values[intPreferencesKey("$key.grid.columns")] ?: 4,
            visible = values[booleanPreferencesKey("$key.grid.visible")] ?: true,
            color = values[longPreferencesKey("$key.grid.color")] ?: 0xFFFFFFFF,
            opacity = values[floatPreferencesKey("$key.grid.opacity")] ?: 0.8f,
            thickness = values[floatPreferencesKey("$key.grid.thickness")] ?: 2f,
        )
        val anchorX = values[floatPreferencesKey("$key.perspective.anchor.x")]
        val anchorY = values[floatPreferencesKey("$key.perspective.anchor.y")]
        val perspective = PerspectiveSettings(
            visible = values[booleanPreferencesKey("$key.perspective.visible")] ?: true,
            points = decodePoints(values[stringPreferencesKey("$key.perspective.points")]),
            anchor = if (anchorX != null && anchorY != null) NormalizedPoint(anchorX, anchorY) else null,
            color = values[longPreferencesKey("$key.perspective.color")] ?: 0xFFFFD740L,
            opacity = values[floatPreferencesKey("$key.perspective.opacity")] ?: 0.9f,
            thickness = values[floatPreferencesKey("$key.perspective.thickness")] ?: 2f,
        )
        return PhotoSettings(grid, perspective)
    }

    override suspend fun save(uri: String, settings: PhotoSettings) {
        val key = photoKey(uri)
        dataStore.edit { values ->
            values[booleanPreferencesKey("$key.exists")] = true
            values[intPreferencesKey("$key.grid.rows")] = settings.grid.rows
            values[intPreferencesKey("$key.grid.columns")] = settings.grid.columns
            values[booleanPreferencesKey("$key.grid.visible")] = settings.grid.visible
            values[longPreferencesKey("$key.grid.color")] = settings.grid.color
            values[floatPreferencesKey("$key.grid.opacity")] = settings.grid.opacity
            values[floatPreferencesKey("$key.grid.thickness")] = settings.grid.thickness
            values[booleanPreferencesKey("$key.perspective.visible")] = settings.perspective.visible
            values[stringPreferencesKey("$key.perspective.points")] = encodePoints(settings.perspective.points)
            values[longPreferencesKey("$key.perspective.color")] = settings.perspective.color
            values[floatPreferencesKey("$key.perspective.opacity")] = settings.perspective.opacity
            values[floatPreferencesKey("$key.perspective.thickness")] = settings.perspective.thickness
            val anchor = settings.perspective.anchor
            if (anchor == null) {
                values.remove(floatPreferencesKey("$key.perspective.anchor.x"))
                values.remove(floatPreferencesKey("$key.perspective.anchor.y"))
            } else {
                values[floatPreferencesKey("$key.perspective.anchor.x")] = anchor.x
                values[floatPreferencesKey("$key.perspective.anchor.y")] = anchor.y
            }
        }
    }

    override suspend fun reset(uri: String) {
        val prefix = "${photoKey(uri)}."
        dataStore.edit { values ->
            values.asMap().keys.filter { it.name.startsWith(prefix) }.forEach(values::remove)
        }
    }

    private companion object {
        val CURRENT_PHOTO = stringPreferencesKey("current_photo")

        fun photoKey(uri: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
            return "photo_${digest.joinToString("") { "%02x".format(it) }}"
        }

        fun encodePoints(points: List<VanishingPoint>): String =
            points.joinToString(";") { "${it.position.x},${it.position.y},${it.enabled}" }

        fun decodePoints(value: String?): List<VanishingPoint> =
            value.orEmpty().split(';').mapNotNull { encoded ->
                val fields = encoded.split(',')
                if (fields.size != 3) return@mapNotNull null
                val x = fields[0].toFloatOrNull() ?: return@mapNotNull null
                val y = fields[1].toFloatOrNull() ?: return@mapNotNull null
                val enabled = fields[2].toBooleanStrictOrNull() ?: return@mapNotNull null
                VanishingPoint(NormalizedPoint(x, y), enabled)
            }
    }
}
