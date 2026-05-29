package com.bluemes.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bluemes_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        private val KEY_USER_NAME   = stringPreferencesKey("user_name")
        private val KEY_SETUP_DONE  = booleanPreferencesKey("setup_done")
        private val KEY_DARK_MODE   = booleanPreferencesKey("dark_mode")
    }

    val userName:    Flow<String?>  = context.dataStore.data.map { it[KEY_USER_NAME] }
    val isSetupDone: Flow<Boolean>  = context.dataStore.data.map { it[KEY_SETUP_DONE] ?: false }
    val isDarkMode:  Flow<Boolean>  = context.dataStore.data.map { it[KEY_DARK_MODE]  ?: false }

    suspend fun setUserName(name: String)   { context.dataStore.edit { it[KEY_USER_NAME]  = name } }
    suspend fun setSetupDone(done: Boolean) { context.dataStore.edit { it[KEY_SETUP_DONE] = done } }
    suspend fun setDarkMode(on: Boolean)    { context.dataStore.edit { it[KEY_DARK_MODE]  = on   } }
}
