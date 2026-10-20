package com.shutixing.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shutixing.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val targetKey = intPreferencesKey("daily_target")
    private val themeKey = stringPreferencesKey("theme_mode")

    val dailyTarget: Flow<Int> = context.settingsDataStore.data.map { it[targetKey] ?: DEFAULT_DAILY_TARGET }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setDailyTarget(value: Int) {
        context.settingsDataStore.edit { it[targetKey] = value.coerceIn(1, 500) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    companion object {
        const val DEFAULT_DAILY_TARGET = 30
    }
}
