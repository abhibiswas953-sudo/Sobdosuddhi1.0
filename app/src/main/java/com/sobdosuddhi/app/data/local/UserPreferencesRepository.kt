package com.sobdosuddhi.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sobdosuddhi.app.model.ProofreadingMode
import com.sobdosuddhi.app.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sobdosuddhi_prefs")

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val editorFontSizeSp: Float = 17f,
  val defaultProofreadingMode: ProofreadingMode = ProofreadingMode.STANDARD,
  val autoSaveEnabled: Boolean = true,
  val poetryProtectionStrict: Boolean = true,
  val useCacheForUnchangedText: Boolean = true,
  val customProxyUrl: String = ""
)

class UserPreferencesRepository(private val context: Context) {
  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size_sp")
    val DEFAULT_PROOFREADING_MODE = stringPreferencesKey("default_proofreading_mode")
    val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")
    val POETRY_PROTECTION_STRICT = booleanPreferencesKey("poetry_protection_strict")
    val USE_CACHE = booleanPreferencesKey("use_cache_unchanged")
    val CUSTOM_PROXY_URL = stringPreferencesKey("custom_proxy_url")
  }

  val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
    UserPreferences(
      themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
      editorFontSizeSp = (prefs[Keys.EDITOR_FONT_SIZE] ?: 17f).coerceIn(13f, 26f),
      defaultProofreadingMode = ProofreadingMode.fromName(
        prefs[Keys.DEFAULT_PROOFREADING_MODE] ?: ProofreadingMode.STANDARD.name
      ),
      autoSaveEnabled = prefs[Keys.AUTO_SAVE_ENABLED] ?: true,
      poetryProtectionStrict = prefs[Keys.POETRY_PROTECTION_STRICT] ?: true,
      useCacheForUnchangedText = prefs[Keys.USE_CACHE] ?: true,
      customProxyUrl = prefs[Keys.CUSTOM_PROXY_URL].orEmpty().trim()
    )
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setEditorFontSize(sizeSp: Float) {
    context.dataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(13f, 26f) }
  }

  suspend fun setDefaultProofreadingMode(mode: ProofreadingMode) {
    context.dataStore.edit { it[Keys.DEFAULT_PROOFREADING_MODE] = mode.name }
  }

  suspend fun setAutoSaveEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_SAVE_ENABLED] = enabled }
  }

  suspend fun setPoetryProtectionStrict(enabled: Boolean) {
    context.dataStore.edit { it[Keys.POETRY_PROTECTION_STRICT] = enabled }
  }

  suspend fun setUseCacheForUnchangedText(enabled: Boolean) {
    context.dataStore.edit { it[Keys.USE_CACHE] = enabled }
  }

  suspend fun setCustomProxyUrl(url: String) {
    context.dataStore.edit { it[Keys.CUSTOM_PROXY_URL] = url.trim().trimEnd('/') }
  }
}
