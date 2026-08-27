package com.hermes.companion.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { System, Light, Dark }

private val Context.themeStore by preferencesDataStore(name = "appearance")

/** Persisted appearance choices: theme mode + Material You dynamic colour. */
@Singleton
class ThemePrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val MODE = stringPreferencesKey("theme_mode")
    private val DYNAMIC = booleanPreferencesKey("dynamic_color")

    val mode: Flow<ThemeMode> = context.themeStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[MODE] ?: ThemeMode.System.name) }.getOrDefault(ThemeMode.System)
    }
    val dynamic: Flow<Boolean> = context.themeStore.data.map { it[DYNAMIC] ?: false }

    suspend fun setMode(m: ThemeMode) { context.themeStore.edit { it[MODE] = m.name } }
    suspend fun setDynamic(on: Boolean) { context.themeStore.edit { it[DYNAMIC] = on } }
}
