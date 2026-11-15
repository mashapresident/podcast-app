package com.podcastapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val access = stringPreferencesKey("access")
    private val refresh = stringPreferencesKey("refresh")
    private val theme = stringPreferencesKey("theme")

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[refresh] != null }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun accessToken(): String? = context.dataStore.data.first()[access]
    suspend fun refreshToken(): String? = context.dataStore.data.first()[refresh]

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { it[access] = accessToken; it[refresh] = refreshToken }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.remove(access); it.remove(refresh) }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[theme] = mode.name }
    }
}
