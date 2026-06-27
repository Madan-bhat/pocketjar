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

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "mchost_servers")

class ServerRepository(private val context: Context) {
    private val gson = Gson()
    private val serversKey = stringPreferencesKey("servers")
    private val activeKey = stringPreferencesKey("active_server_id")

    val servers: Flow<List<Server>> = context.serverDataStore.data.map { prefs ->
        val json = prefs[serversKey] ?: "[]"
        gson.fromJson(json, object : TypeToken<List<Server>>() {}.type) ?: emptyList()
    }

    val activeServerId: Flow<String?> = context.serverDataStore.data.map { it[activeKey] }

    suspend fun saveServers(servers: List<Server>) {
        context.serverDataStore.edit { prefs ->
            prefs[serversKey] = gson.toJson(servers)
        }
    }

    suspend fun setActiveServerId(id: String?) {
        context.serverDataStore.edit { prefs ->
            if (id == null) prefs.remove(activeKey) else prefs[activeKey] = id
        }
    }

    suspend fun upsertServer(server: Server) {
        context.serverDataStore.edit { prefs ->
            val current: List<Server> = gson.fromJson(
                prefs[serversKey] ?: "[]",
                object : TypeToken<List<Server>>() {}.type
            ) ?: emptyList()
            val updated = current.filterNot { it.id == server.id } + server
            prefs[serversKey] = gson.toJson(updated)
        }
    }

    suspend fun deleteServer(id: String) {
        context.serverDataStore.edit { prefs ->
            val current: List<Server> = gson.fromJson(
                prefs[serversKey] ?: "[]",
                object : TypeToken<List<Server>>() {}.type
            ) ?: emptyList()
            prefs[serversKey] = gson.toJson(current.filterNot { it.id == id })
            if (prefs[activeKey] == id) prefs.remove(activeKey)
        }
    }
}
