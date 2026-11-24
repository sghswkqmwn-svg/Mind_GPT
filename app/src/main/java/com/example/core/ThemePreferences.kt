package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("System Theme"),
    DARK("Dark Mode"),
    LIGHT("Light Mode"),
}

private val Context.mindGptDataStore: DataStore<Preferences> by preferencesDataStore(name = "mindgpt_prefs")

class ThemePreferences(context: Context) {
    private val appContext = context.applicationContext

    private val themeKey = stringPreferencesKey("theme_mode")
    private val autoScrollKey = booleanPreferencesKey("auto_scroll")

    val themeMode: Flow<ThemeMode> = appContext.mindGptDataStore.data.map { prefs ->
        val raw = prefs[themeKey] ?: ThemeMode.DARK.name
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
    }

    val autoScrollEnabled: Flow<Boolean> = appContext.mindGptDataStore.data.map { prefs ->
        prefs[autoScrollKey] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.mindGptDataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setAutoScroll(enabled: Boolean) {
        appContext.mindGptDataStore.edit { prefs ->
            prefs[autoScrollKey] = enabled
        }
    }
}
