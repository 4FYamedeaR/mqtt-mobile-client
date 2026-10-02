package com.mqttmobile.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mqttmobile.app.data.model.PayloadFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small persistent store for UI preferences that are shared across workspaces. */
class AppSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun defaultDisplayFormat(): PayloadFormat = legacyDefaultDisplayFormat()

    fun logLimit(): Int = DEFAULT_LOG_LIMIT

    fun defaultDisplayFormatFlow(): Flow<PayloadFormat> = appContext.settingsDataStore.data.map { values ->
        values[DEFAULT_DISPLAY_FORMAT] ?.let { value -> runCatching { PayloadFormat.valueOf(value) }.getOrNull() }
            ?: legacyDefaultDisplayFormat()
    }

    fun logLimitFlow(): Flow<Int> = appContext.settingsDataStore.data.map { values ->
        values[LOG_LIMIT]?.coerceIn(MIN_LOG_LIMIT, MAX_LOG_LIMIT) ?: DEFAULT_LOG_LIMIT
    }

    suspend fun setDefaultDisplayFormat(format: PayloadFormat) {
        appContext.settingsDataStore.edit { values -> values[DEFAULT_DISPLAY_FORMAT] = format.name }
    }

    suspend fun setLogLimit(limit: Int) {
        appContext.settingsDataStore.edit { values ->
            values[LOG_LIMIT] = limit.coerceIn(MIN_LOG_LIMIT, MAX_LOG_LIMIT)
        }
    }

    private fun legacyDefaultDisplayFormat(): PayloadFormat = runCatching {
        PayloadFormat.valueOf(legacyPreferences.getString(LEGACY_DEFAULT_DISPLAY_FORMAT_KEY, PayloadFormat.JSON.name)!!)
    }.getOrDefault(PayloadFormat.JSON)

    private companion object {
        const val LEGACY_PREFERENCES_NAME = "mqtt_app_settings"
        const val LEGACY_DEFAULT_DISPLAY_FORMAT_KEY = "default_display_format"
        const val DEFAULT_LOG_LIMIT = 300
        const val MIN_LOG_LIMIT = 100
        const val MAX_LOG_LIMIT = 1000
        val DEFAULT_DISPLAY_FORMAT = stringPreferencesKey("default_display_format")
        val LOG_LIMIT = intPreferencesKey("log_limit")
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mqtt_app_settings_v2")
