package com.termlinux.settings

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "termlinux_settings")

@Serializable
data class AppSettings(
    val selectedDistroId: String = "debian",
    val safTreeUri: String? = null,
    val fontSizeSp: Int = 13,
    val themeId: String = "obsidian",
    val appThemeId: String = "obsidian",
    val scrollbackLines: Int = 10_000,
    val showExtraKeys: Boolean = true,
    val extraKeysPreset: String = "standard",
    val immersiveMode: Boolean = true,
    val bellEnabled: Boolean = false,
    val wakeLockEnabled: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private object Keys {
        val SELECTED_DISTRO = stringPreferencesKey("selected_distro_id")
        val SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val THEME_ID = stringPreferencesKey("theme_id")
        val APP_THEME_ID = stringPreferencesKey("app_theme_id")
        val SCROLLBACK_LINES = intPreferencesKey("scrollback_lines")
        val SHOW_EXTRA_KEYS = booleanPreferencesKey("show_extra_keys")
        val EXTRA_KEYS_PRESET = stringPreferencesKey("extra_keys_preset")
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")
        val BELL_ENABLED = booleanPreferencesKey("bell_enabled")
        val WAKE_LOCK_ENABLED = booleanPreferencesKey("wake_lock_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            selectedDistroId = prefs[Keys.SELECTED_DISTRO] ?: "debian",
            safTreeUri = prefs[Keys.SAF_TREE_URI],
            fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 13).coerceIn(8, 28),
            themeId = prefs[Keys.THEME_ID] ?: "obsidian",
            appThemeId = prefs[Keys.APP_THEME_ID] ?: "obsidian",
            scrollbackLines = (prefs[Keys.SCROLLBACK_LINES] ?: 10_000).coerceIn(1000, 50_000),
            showExtraKeys = prefs[Keys.SHOW_EXTRA_KEYS] ?: true,
            extraKeysPreset = prefs[Keys.EXTRA_KEYS_PRESET] ?: "standard",
            immersiveMode = prefs[Keys.IMMERSIVE_MODE] ?: true,
            bellEnabled = prefs[Keys.BELL_ENABLED] ?: false,
            wakeLockEnabled = prefs[Keys.WAKE_LOCK_ENABLED] ?: false
        )
    }

    suspend fun setSelectedDistro(distroId: String) {
        context.dataStore.edit { it[Keys.SELECTED_DISTRO] = distroId }
    }

    suspend fun setSafTreeUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.SAF_TREE_URI)
            else prefs[Keys.SAF_TREE_URI] = uri
        }
    }

    suspend fun setFontSizeSp(size: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE_SP] = size.coerceIn(8, 28) }
    }

    suspend fun setThemeId(themeId: String) {
        context.dataStore.edit { it[Keys.THEME_ID] = themeId }
    }

    suspend fun setAppThemeId(appThemeId: String) {
        context.dataStore.edit { it[Keys.APP_THEME_ID] = appThemeId }
    }

    suspend fun setScrollbackLines(lines: Int) {
        context.dataStore.edit { it[Keys.SCROLLBACK_LINES] = lines.coerceIn(1000, 50_000) }
    }

    suspend fun setShowExtraKeys(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_EXTRA_KEYS] = show }
    }

    suspend fun setExtraKeysPreset(preset: String) {
        context.dataStore.edit { it[Keys.EXTRA_KEYS_PRESET] = preset }
    }

    suspend fun setImmersiveMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IMMERSIVE_MODE] = enabled }
    }

    suspend fun setBellEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BELL_ENABLED] = enabled }
    }

    suspend fun setWakeLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_LOCK_ENABLED] = enabled }
    }

    fun exportToJson(settings: AppSettings): String = json.encodeToString(settings)

    suspend fun importFromJson(raw: String): Result<AppSettings> = runCatching {
        val parsed = json.decodeFromString<AppSettings>(raw)
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_DISTRO] = parsed.selectedDistroId
            if (parsed.safTreeUri != null) prefs[Keys.SAF_TREE_URI] = parsed.safTreeUri
            else prefs.remove(Keys.SAF_TREE_URI)
            prefs[Keys.FONT_SIZE_SP] = parsed.fontSizeSp.coerceIn(8, 28)
            prefs[Keys.THEME_ID] = parsed.themeId
            prefs[Keys.APP_THEME_ID] = parsed.appThemeId
            prefs[Keys.SCROLLBACK_LINES] = parsed.scrollbackLines.coerceIn(1000, 50_000)
            prefs[Keys.SHOW_EXTRA_KEYS] = parsed.showExtraKeys
            prefs[Keys.EXTRA_KEYS_PRESET] = parsed.extraKeysPreset
            prefs[Keys.IMMERSIVE_MODE] = parsed.immersiveMode
            prefs[Keys.BELL_ENABLED] = parsed.bellEnabled
            prefs[Keys.WAKE_LOCK_ENABLED] = parsed.wakeLockEnabled
        }
        parsed
    }
}
