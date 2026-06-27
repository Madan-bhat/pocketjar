package com.mchost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "mchost_configs")

class ConfigRepository(private val context: Context) {
    private val gson = Gson()
    private val configsKey = stringPreferencesKey("configs")

    val configs: Flow<Map<String, ServerConfig>> = context.configDataStore.data.map { prefs ->
        val json = prefs[configsKey] ?: "{}"
        gson.fromJson<Map<String, ServerConfig>>(json, object : TypeToken<Map<String, ServerConfig>>() {}.type)
            ?: emptyMap()
    }

    suspend fun saveConfig(serverId: String, config: ServerConfig) {
        context.configDataStore.edit { prefs ->
            val current: Map<String, ServerConfig> = gson.fromJson(
                prefs[configsKey] ?: "{}",
                object : TypeToken<Map<String, ServerConfig>>() {}.type
            ) ?: emptyMap()
            prefs[configsKey] = gson.toJson(current + (serverId to config))
        }
    }

    suspend fun deleteConfig(serverId: String) {
        context.configDataStore.edit { prefs ->
            val current: Map<String, ServerConfig> = gson.fromJson(
                prefs[configsKey] ?: "{}",
                object : TypeToken<Map<String, ServerConfig>>() {}.type
            ) ?: emptyMap()
            prefs[configsKey] = gson.toJson(current - serverId)
        }
    }
}
