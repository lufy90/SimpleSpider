package com.simplespider.dy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class TokenStore(private val context: Context) {
    private val keyToken = stringPreferencesKey("access_token")
    private val keyApiServer = stringPreferencesKey("api_server_host_port")
    private val keyVideoListRandom = booleanPreferencesKey("video_list_random")
    private val keyVideoEndAction = stringPreferencesKey("video_end_action")
    private val keyVideoGridColumns = intPreferencesKey("video_grid_columns")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyToken]
    }

    val apiServerFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyApiServer]
    }

    val videoListRandomFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyVideoListRandom] == true
    }

    val videoEndActionFlow: Flow<VideoEndAction> = context.dataStore.data.map { prefs ->
        VideoEndAction.fromStorage(prefs[keyVideoEndAction])
    }

    val videoGridColumnsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[keyVideoGridColumns] ?: 2).coerceIn(2, 4)
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[keyToken] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(keyToken) }
    }

    suspend fun saveApiServerHostPort(hostPort: String) {
        context.dataStore.edit { prefs ->
            val v = hostPort.trim()
            if (v.isEmpty()) {
                prefs.remove(keyApiServer)
            } else {
                prefs[keyApiServer] = v
            }
        }
    }

    suspend fun saveVideoListRandom(enabled: Boolean) {
        context.dataStore.edit { it[keyVideoListRandom] = enabled }
    }

    suspend fun saveVideoEndAction(action: VideoEndAction) {
        context.dataStore.edit { it[keyVideoEndAction] = action.storageValue }
    }

    suspend fun saveVideoGridColumns(columns: Int) {
        val c = columns.coerceIn(2, 4)
        context.dataStore.edit { it[keyVideoGridColumns] = c }
    }
}

enum class VideoEndAction(val storageValue: String) {
    PLAY_NEXT("play_next"),
    REPLAY_CURRENT("replay_current"),
    ;

    companion object {
        fun fromStorage(raw: String?): VideoEndAction =
            entries.firstOrNull { it.storageValue == raw } ?: PLAY_NEXT
    }
}
