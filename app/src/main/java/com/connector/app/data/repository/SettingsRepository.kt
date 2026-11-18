package com.connector.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.connector.app.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connector_settings")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = stringPreferencesKey("settings_json")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY]
        if (raw.isNullOrBlank()) AppSettings()
        else runCatching { json.decodeFromString<AppSettings>(raw) }.getOrDefault(AppSettings())
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY]?.let {
                runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull()
            } ?: AppSettings()
            prefs[KEY] = json.encodeToString(transform(current))
        }
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(settings)
        }
    }
}
