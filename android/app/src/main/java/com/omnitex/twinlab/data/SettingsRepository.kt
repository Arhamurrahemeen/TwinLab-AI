package com.omnitex.twinlab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("twinlab_settings")
private val BASE_URL = stringPreferencesKey("base_url")

class SettingsRepository(private val context: Context) {
    /** null until the user has set a backend URL. */
    val baseUrl: Flow<String?> = context.dataStore.data.map { it[BASE_URL] }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[BASE_URL] = url.trim().trimEnd('/') }
    }
}
