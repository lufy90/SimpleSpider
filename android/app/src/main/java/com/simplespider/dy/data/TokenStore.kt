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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class TokenStore(private val context: Context) {
    private val keyToken = stringPreferencesKey("access_token")
    private val keyApiServer = stringPreferencesKey("api_server_host_port")
    private val keyVideoListRandom = booleanPreferencesKey("video_list_random")
    private val keyVideoEndAction = stringPreferencesKey("video_end_action")
    private val keyVideoGridColumns = intPreferencesKey("video_grid_columns")
    private val keyVideoQueryRate = intPreferencesKey("video_query_rate")
    private val keyVideoQueryRateLegacy = intPreferencesKey("video_query_stars")
    private val keyVideoQueryMinRate = intPreferencesKey("video_query_min_rate")
    private val keyVideoQueryMaxRate = intPreferencesKey("video_query_max_rate")
    private val keyVideoQueryIsLike = stringPreferencesKey("video_query_is_like")
    private val keyVideoQueryIsFavor = stringPreferencesKey("video_query_is_favor")
    private val keyVideoQueryStatus = stringPreferencesKey("video_query_status")
    private val keyAuthorQueryRate = intPreferencesKey("author_query_rate")
    private val keyAuthorQueryStatus = stringPreferencesKey("author_query_status")
    private val keyAuthorQueryIsFavor = stringPreferencesKey("author_query_is_favor")
    private val keyAuthorQueryIsValid = stringPreferencesKey("author_query_is_valid")
    private val keyRememberLogin = booleanPreferencesKey("remember_login")
    private val keySavedUsername = stringPreferencesKey("saved_username")
    private val keySavedPassword = stringPreferencesKey("saved_password")

    data class SavedLoginCredentials(
        val remember: Boolean,
        val username: String,
        val password: String,
    )

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

    val videoListQueryParamsFlow: Flow<VideoListQueryParams> =
        context.dataStore.data
            .map { prefs -> videoListQueryParamsFromPrefs(prefs) }
            .distinctUntilChanged()

    val authorListQueryParamsFlow: Flow<AuthorListQueryParams> =
        context.dataStore.data
            .map { prefs -> authorListQueryParamsFromPrefs(prefs) }
            .distinctUntilChanged()

    suspend fun readVideoListQueryParams(): VideoListQueryParams =
        videoListQueryParamsFromPrefs(context.dataStore.data.first())

    suspend fun readAuthorListQueryParams(): AuthorListQueryParams =
        authorListQueryParamsFromPrefs(context.dataStore.data.first())

    suspend fun saveVideoListQueryParams(params: VideoListQueryParams) {
        context.dataStore.edit { prefs ->
            prefs.remove(keyVideoQueryRateLegacy)
            writeRate(prefs, keyVideoQueryRate, params.rate)
            writeRate(prefs, keyVideoQueryMinRate, params.minRate)
            writeRate(prefs, keyVideoQueryMaxRate, params.maxRate)
            writeTriBool(prefs, keyVideoQueryIsLike, params.isLike)
            writeTriBool(prefs, keyVideoQueryIsFavor, params.isFavor)
            writeStatus(prefs, keyVideoQueryStatus, params.status)
        }
    }

    suspend fun saveAuthorListQueryParams(params: AuthorListQueryParams) {
        context.dataStore.edit { prefs ->
            writeRate(prefs, keyAuthorQueryRate, params.rate)
            writeStatus(prefs, keyAuthorQueryStatus, params.status)
            writeTriBool(prefs, keyAuthorQueryIsFavor, params.isFavor)
            writeTriBool(prefs, keyAuthorQueryIsValid, params.isValid)
        }
    }

    suspend fun saveVideoQueryRate(rateExact: Int?) {
        context.dataStore.edit { prefs ->
            prefs.remove(keyVideoQueryRateLegacy)
            prefs[keyVideoQueryRate] = rateExact?.coerceIn(0, 10) ?: -1
        }
    }

    suspend fun saveVideoQueryMinRate(minRate: Int?) {
        context.dataStore.edit { prefs ->
            prefs[keyVideoQueryMinRate] = minRate?.coerceIn(0, 10) ?: -1
        }
    }

    suspend fun saveVideoQueryMaxRate(maxRate: Int?) {
        context.dataStore.edit { prefs ->
            prefs[keyVideoQueryMaxRate] = maxRate?.coerceIn(0, 10) ?: -1
        }
    }

    suspend fun saveVideoQueryIsLike(value: Boolean?) {
        context.dataStore.edit { prefs ->
            when (value) {
                true -> prefs[keyVideoQueryIsLike] = "true"
                false -> prefs[keyVideoQueryIsLike] = "false"
                null -> prefs.remove(keyVideoQueryIsLike)
            }
        }
    }

    suspend fun saveVideoQueryIsFavor(value: Boolean?) {
        context.dataStore.edit { prefs ->
            when (value) {
                true -> prefs[keyVideoQueryIsFavor] = "true"
                false -> prefs[keyVideoQueryIsFavor] = "false"
                null -> prefs.remove(keyVideoQueryIsFavor)
            }
        }
    }

    suspend fun saveVideoQueryStatus(status: String?) {
        context.dataStore.edit { prefs ->
            val v = status?.trim().orEmpty()
            if (v.isEmpty()) {
                prefs.remove(keyVideoQueryStatus)
            } else {
                prefs[keyVideoQueryStatus] = v
            }
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[keyToken] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(keyToken) }
    }

    suspend fun readSavedLoginCredentials(): SavedLoginCredentials {
        val prefs = context.dataStore.data.first()
        val remember = prefs[keyRememberLogin] == true
        return SavedLoginCredentials(
            remember = remember,
            username = prefs[keySavedUsername].orEmpty(),
            password = prefs[keySavedPassword].orEmpty(),
        )
    }

    suspend fun saveLoginCredentials(username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[keyRememberLogin] = true
            prefs[keySavedUsername] = username.trim()
            prefs[keySavedPassword] = password
        }
    }

    suspend fun clearSavedLoginCredentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyRememberLogin)
            prefs.remove(keySavedUsername)
            prefs.remove(keySavedPassword)
        }
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

    private fun videoListQueryParamsFromPrefs(prefs: Preferences): VideoListQueryParams {
        val rateKey = prefs[keyVideoQueryRate] ?: prefs[keyVideoQueryRateLegacy] ?: -1
        val rate = if (rateKey < 0) null else rateKey.coerceIn(0, 10)
        val minKey = prefs[keyVideoQueryMinRate] ?: -1
        val minRate = if (minKey < 0) null else minKey.coerceIn(0, 10)
        val maxKey = prefs[keyVideoQueryMaxRate] ?: -1
        val maxRate = if (maxKey < 0) null else maxKey.coerceIn(0, 10)
        val isLike = when (prefs[keyVideoQueryIsLike]) {
            "true" -> true
            "false" -> false
            else -> null
        }
        val isFavor = when (prefs[keyVideoQueryIsFavor]) {
            "true" -> true
            "false" -> false
            else -> null
        }
        val statusRaw = prefs[keyVideoQueryStatus]?.trim().orEmpty()
        val status = statusRaw.takeIf { it.isNotEmpty() }
        return VideoListQueryParams(
            rate = rate,
            minRate = minRate,
            maxRate = maxRate,
            isLike = isLike,
            isFavor = isFavor,
            status = status,
        )
    }

    private fun authorListQueryParamsFromPrefs(prefs: Preferences): AuthorListQueryParams {
        val rateKey = prefs[keyAuthorQueryRate] ?: -1
        val rate = if (rateKey < 0) null else rateKey.coerceIn(0, 5)
        return AuthorListQueryParams(
            rate = rate,
            status = prefs[keyAuthorQueryStatus]?.trim()?.takeIf { it.isNotEmpty() },
            isFavor = readTriBool(prefs, keyAuthorQueryIsFavor),
            isValid = readTriBool(prefs, keyAuthorQueryIsValid),
        )
    }

    private fun writeRate(prefs: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<Int>, value: Int?) {
        prefs[key] = value?.coerceIn(0, 10) ?: -1
    }

    private fun writeStatus(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        status: String?,
    ) {
        val v = status?.trim().orEmpty()
        if (v.isEmpty()) prefs.remove(key) else prefs[key] = v
    }

    private fun writeTriBool(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        value: Boolean?,
    ) {
        when (value) {
            true -> prefs[key] = "true"
            false -> prefs[key] = "false"
            null -> prefs.remove(key)
        }
    }

    private fun readTriBool(prefs: Preferences, key: Preferences.Key<String>): Boolean? =
        when (prefs[key]) {
            "true" -> true
            "false" -> false
            else -> null
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
