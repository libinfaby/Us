package com.pingucodu.us.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pingucodu.us.data.local.SettingsDataStore
import com.pingucodu.us.ui.theme.FontScaleLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists app-level display preferences, e.g. the in-app text size. */
@Singleton
class AppSettingsRepository @Inject constructor(@SettingsDataStore private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val FONT_SCALE_LEVEL = intPreferencesKey("font_scale_level")
    }

    val fontScaleLevel: Flow<FontScaleLevel> = dataStore.data.map {
        FontScaleLevel.fromOrdinal(it[Keys.FONT_SCALE_LEVEL] ?: FontScaleLevel.Default.ordinal)
    }

    suspend fun setFontScaleLevel(level: FontScaleLevel) {
        dataStore.edit { it[Keys.FONT_SCALE_LEVEL] = level.ordinal }
    }
}
