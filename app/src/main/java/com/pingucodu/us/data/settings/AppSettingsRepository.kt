package com.pingucodu.us.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val MASK_NAMES_ENABLED = booleanPreferencesKey("mask_names_enabled")
        val MASK_LABEL_PINGU = stringPreferencesKey("mask_label_pingu")
        val MASK_LABEL_CODU = stringPreferencesKey("mask_label_codu")
    }

    val fontScaleLevel: Flow<FontScaleLevel> = dataStore.data.map {
        FontScaleLevel.fromOrdinal(it[Keys.FONT_SCALE_LEVEL] ?: FontScaleLevel.Default.ordinal)
    }

    suspend fun setFontScaleLevel(level: FontScaleLevel) {
        dataStore.edit { it[Keys.FONT_SCALE_LEVEL] = level.ordinal }
    }

    val maskNamesEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.MASK_NAMES_ENABLED] ?: false }

    suspend fun setMaskNamesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MASK_NAMES_ENABLED] = enabled }
    }

    val maskLabelPingu: Flow<String> = dataStore.data.map { it[Keys.MASK_LABEL_PINGU] ?: "A" }

    suspend fun setMaskLabelPingu(label: String) {
        dataStore.edit { it[Keys.MASK_LABEL_PINGU] = label }
    }

    val maskLabelCodu: Flow<String> = dataStore.data.map { it[Keys.MASK_LABEL_CODU] ?: "B" }

    suspend fun setMaskLabelCodu(label: String) {
        dataStore.edit { it[Keys.MASK_LABEL_CODU] = label }
    }
}
