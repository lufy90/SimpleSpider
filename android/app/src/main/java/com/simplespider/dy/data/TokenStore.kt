package com.simplespider.dy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class TokenStore(private val context: Context) {
    private val keyToken = stringPreferencesKey("access_token")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[keyToken]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[keyToken] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(keyToken) }
    }
}
