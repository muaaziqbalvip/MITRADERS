package com.mitv.trademaster.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "mitv_session")

data class SessionState(
    val language: String = "en",
    val profileComplete: Boolean = false,
    val accentTheme: String = "green",
)

class SessionRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val PROFILE_COMPLETE = booleanPreferencesKey("profile_complete")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
    }

    val session: Flow<SessionState> = context.sessionStore.data.map { prefs ->
        SessionState(
            language = prefs[Keys.LANGUAGE] ?: "en",
            profileComplete = prefs[Keys.PROFILE_COMPLETE] ?: false,
            accentTheme = prefs[Keys.ACCENT_THEME] ?: "green",
        )
    }

    suspend fun setLanguage(lang: String) {
        context.sessionStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun setProfileComplete(complete: Boolean) {
        context.sessionStore.edit { it[Keys.PROFILE_COMPLETE] = complete }
    }

    suspend fun setAccentTheme(theme: String) {
        context.sessionStore.edit { it[Keys.ACCENT_THEME] = theme }
    }
}
