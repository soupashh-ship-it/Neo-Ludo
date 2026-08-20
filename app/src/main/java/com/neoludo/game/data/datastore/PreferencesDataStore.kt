package com.neoludo.game.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neoludo.game.core.model.GameSettings
import com.neoludo.game.core.model.ThemeMode
import com.neoludo.game.core.model.UserProfile
import com.neoludo.game.core.model.UserStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "neoludo_prefs")

class PreferencesDataStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val AUTO_MOVE_SINGLE = booleanPreferencesKey("auto_move_single")
        val TURN_TIMER = intPreferencesKey("turn_timer")
        val PENALTY_3X_SIX = booleanPreferencesKey("penalty_3x_six")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")

        val USER_PROFILE_JSON = stringPreferencesKey("user_profile_json")
        val USER_STATS_JSON = stringPreferencesKey("user_stats_json")
    }

    val settingsFlow: Flow<GameSettings> = context.dataStore.data.map { prefs ->
        GameSettings(
            themeMode = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK_OLED,
            soundVolume = prefs[SOUND_VOLUME] ?: 1.0f,
            soundEnabled = prefs[SOUND_ENABLED] ?: true,
            musicVolume = prefs[MUSIC_VOLUME] ?: 0.7f,
            musicEnabled = prefs[MUSIC_ENABLED] ?: true,
            hapticsEnabled = prefs[HAPTICS_ENABLED] ?: true,
            autoMoveSinglePiece = prefs[AUTO_MOVE_SINGLE] ?: true,
            turnTimerSeconds = prefs[TURN_TIMER] ?: 30,
            penalty3xSix = prefs[PENALTY_3X_SIX] ?: true,
            reducedMotion = prefs[REDUCED_MOTION] ?: false
        )
    }

    suspend fun updateSettings(settings: GameSettings) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = settings.themeMode.name
            prefs[SOUND_VOLUME] = settings.soundVolume
            prefs[SOUND_ENABLED] = settings.soundEnabled
            prefs[MUSIC_VOLUME] = settings.musicVolume
            prefs[MUSIC_ENABLED] = settings.musicEnabled
            prefs[HAPTICS_ENABLED] = settings.hapticsEnabled
            prefs[AUTO_MOVE_SINGLE] = settings.autoMoveSinglePiece
            prefs[TURN_TIMER] = settings.turnTimerSeconds
            prefs[PENALTY_3X_SIX] = settings.penalty3xSix
            prefs[REDUCED_MOTION] = settings.reducedMotion
        }
    }

    val profileFlow: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        prefs[USER_PROFILE_JSON]?.let {
            runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull()
        } ?: UserProfile()
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[USER_PROFILE_JSON] = json.encodeToString(profile)
        }
    }

    val statsFlow: Flow<UserStats> = context.dataStore.data.map { prefs ->
        prefs[USER_STATS_JSON]?.let {
            runCatching { json.decodeFromString<UserStats>(it) }.getOrNull()
        } ?: UserStats()
    }

    suspend fun saveStats(stats: UserStats) {
        context.dataStore.edit { prefs ->
            prefs[USER_STATS_JSON] = json.encodeToString(stats)
        }
    }
}
