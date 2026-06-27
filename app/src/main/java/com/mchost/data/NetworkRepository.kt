package com.mchost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.networkDataStore: DataStore<Preferences> by preferencesDataStore(name = "mchost_network")

class NetworkRepository(private val context: Context) {
    private val gson = Gson()
    private val prefsKey = stringPreferencesKey("network_prefs")

    val networkPrefs: Flow<NetworkPrefs> = context.networkDataStore.data.map { store ->
        val json = store[prefsKey] ?: return@map NetworkPrefs()
        try {
            gson.fromJson(json, NetworkPrefs::class.java) ?: NetworkPrefs()
        } catch (_: Exception) {
            // Migrate old PLAYIT enum value
            if (json.contains("PLAYIT")) {
                gson.fromJson(json.replace("PLAYIT", "LOCALXPOSE"), NetworkPrefs::class.java)
                    ?: NetworkPrefs()
            } else {
                NetworkPrefs()
            }
        }
    }

    suspend fun saveNetworkPrefs(prefs: NetworkPrefs) {
        context.networkDataStore.edit { store ->
            store[prefsKey] = gson.toJson(prefs)
        }
    }
}
