package com.papertong.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.TranslateStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 非敏感配置。API Key 不在这里，见 SecretStore。
 */
interface SettingsRepository {
    val translateStyle: Flow<TranslateStyle>
    val model: Flow<ModelOption>

    suspend fun setTranslateStyle(style: TranslateStyle)
    suspend fun setModel(model: ModelOption)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val translateStyle: Flow<TranslateStyle> =
        dataStore.data.map { prefs ->
            TranslateStyle.fromId(prefs[KEY_STYLE])
        }

    override val model: Flow<ModelOption> =
        dataStore.data.map { prefs ->
            ModelOption.fromId(prefs[KEY_MODEL])
        }

    override suspend fun setTranslateStyle(style: TranslateStyle) {
        dataStore.edit { prefs -> prefs[KEY_STYLE] = style.id }
    }

    override suspend fun setModel(model: ModelOption) {
        dataStore.edit { prefs -> prefs[KEY_MODEL] = model.id }
    }

    private companion object {
        val KEY_STYLE = stringPreferencesKey("translate_style")
        val KEY_MODEL = stringPreferencesKey("model_id")
    }
}
