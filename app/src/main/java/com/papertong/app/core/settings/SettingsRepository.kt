package com.papertong.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.ReadingLayout
import com.papertong.app.core.model.TranslateDirection
import com.papertong.app.core.model.TranslateStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 非敏感配置。API Key 不在这里，见 SecretStore。
 */
interface SettingsRepository {
    val translateStyle: Flow<TranslateStyle>
    val model: Flow<ModelOption>
    val direction: Flow<TranslateDirection>

    /** 对照阅读的正文字号倍率。 */
    val readingFontScale: Flow<Float>

    /** 对照阅读的排布方式。 */
    val readingLayout: Flow<ReadingLayout>

    suspend fun setTranslateStyle(style: TranslateStyle)
    suspend fun setModel(model: ModelOption)
    suspend fun setDirection(direction: TranslateDirection)
    suspend fun setReadingFontScale(scale: Float)
    suspend fun setReadingLayout(layout: ReadingLayout)
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

    override val direction: Flow<TranslateDirection> =
        dataStore.data.map { prefs ->
            TranslateDirection.fromId(prefs[KEY_DIRECTION])
        }

    override val readingFontScale: Flow<Float> =
        dataStore.data.map { prefs ->
            prefs[KEY_READING_FONT_SCALE] ?: DEFAULT_READING_FONT_SCALE
        }

    override val readingLayout: Flow<ReadingLayout> =
        dataStore.data.map { prefs ->
            ReadingLayout.fromId(prefs[KEY_READING_LAYOUT])
        }

    override suspend fun setReadingFontScale(scale: Float) {
        // 夹在允许区间内，避免历史数据或异常输入把界面撑坏
        val clamped = scale.coerceIn(MIN_READING_FONT_SCALE, MAX_READING_FONT_SCALE)
        dataStore.edit { prefs -> prefs[KEY_READING_FONT_SCALE] = clamped }
    }

    override suspend fun setReadingLayout(layout: ReadingLayout) {
        dataStore.edit { prefs -> prefs[KEY_READING_LAYOUT] = layout.id }
    }

    override suspend fun setTranslateStyle(style: TranslateStyle) {
        dataStore.edit { prefs -> prefs[KEY_STYLE] = style.id }
    }

    override suspend fun setModel(model: ModelOption) {
        dataStore.edit { prefs -> prefs[KEY_MODEL] = model.id }
    }

    override suspend fun setDirection(direction: TranslateDirection) {
        dataStore.edit { prefs -> prefs[KEY_DIRECTION] = direction.id }
    }

    companion object {
        /** 阅读页字号倍率区间，同时供设置界面显示。 */
        const val MIN_READING_FONT_SCALE = 0.85f
        const val MAX_READING_FONT_SCALE = 1.6f
        const val DEFAULT_READING_FONT_SCALE = 1.0f

        private val KEY_STYLE = stringPreferencesKey("translate_style")
        private val KEY_MODEL = stringPreferencesKey("model_id")
        private val KEY_DIRECTION = stringPreferencesKey("translate_direction")
        private val KEY_READING_FONT_SCALE = floatPreferencesKey("reading_font_scale")
        private val KEY_READING_LAYOUT = stringPreferencesKey("reading_layout")
    }
}
