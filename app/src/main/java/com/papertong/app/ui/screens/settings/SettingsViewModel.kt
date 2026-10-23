package com.papertong.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.papertong.app.core.crypto.ApiKeyValidator
import com.papertong.app.core.crypto.SecretStore
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.TranslateStyle
import com.papertong.app.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** API Key 输入界面的状态。 */
data class ApiKeyUiState(
    val input: String = "",
    val visible: Boolean = false,
    val busy: Boolean = false,
    /** 本次会话中刚刚保存成功，用于让对话框自行关闭。 */
    val justSaved: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class SettingsViewModel(
    private val secretStore: SecretStore,
    private val settingsRepository: SettingsRepository,
    private val validator: ApiKeyValidator
) : ViewModel() {

    val style: StateFlow<TranslateStyle> = settingsRepository.translateStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslateStyle.Academic)

    val model: StateFlow<ModelOption> = settingsRepository.model
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelOption.Chat)

    val hasApiKey: StateFlow<Boolean> = secretStore.hasSecret
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _apiKeyState = MutableStateFlow(ApiKeyUiState())
    val apiKeyState: StateFlow<ApiKeyUiState> = _apiKeyState.asStateFlow()

    fun onApiKeyInputChange(value: String) {
        // 只保留可打印字符，去掉换行与首尾空白，避免粘贴时带进看不见的内容
        _apiKeyState.value = _apiKeyState.value.copy(
            input = value.filter { it.isLetterOrDigit() || it in "-_." },
            message = null,
            isError = false
        )
    }

    fun toggleVisibility() {
        _apiKeyState.value = _apiKeyState.value.copy(visible = !_apiKeyState.value.visible)
    }

    fun clearMessage() {
        _apiKeyState.value = _apiKeyState.value.copy(message = null, isError = false)
    }

    /** 打开对话框时清空上一次的校验提示。 */
    fun resetInput() {
        _apiKeyState.value = ApiKeyUiState()
    }

    /**
     * 先联网校验再落盘。校验失败不保存，避免用户以为已配置好、
     * 实际到翻译时才报错。
     */
    fun saveApiKey() {
        val key = _apiKeyState.value.input.trim()
        if (key.isEmpty()) {
            _apiKeyState.value = _apiKeyState.value.copy(
                message = "请先填入 API Key",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            _apiKeyState.value = _apiKeyState.value.copy(busy = true, message = null)
            when (val result = validator.validate(key)) {
                ApiKeyValidator.Result.Valid -> {
                    secretStore.saveApiKey(key)
                    // 输入框立刻清空，明文不在内存里多留一秒
                    _apiKeyState.value = ApiKeyUiState(
                        justSaved = true,
                        message = "已保存并校验通过"
                    )
                }

                ApiKeyValidator.Result.Unauthorized -> {
                    _apiKeyState.value = _apiKeyState.value.copy(
                        busy = false,
                        message = "Key 无效或已失效（401）",
                        isError = true
                    )
                }

                is ApiKeyValidator.Result.Failed -> {
                    _apiKeyState.value = _apiKeyState.value.copy(
                        busy = false,
                        message = result.message,
                        isError = true
                    )
                }
            }
        }
    }

    fun deleteApiKey() {
        viewModelScope.launch {
            secretStore.clear()
            _apiKeyState.value = ApiKeyUiState(message = "已删除本机保存的 Key")
        }
    }

    fun setStyle(value: TranslateStyle) {
        viewModelScope.launch { settingsRepository.setTranslateStyle(value) }
    }

    fun setModel(value: ModelOption) {
        viewModelScope.launch { settingsRepository.setModel(value) }
    }

    companion object {
        fun factory(
            secretStore: SecretStore,
            settingsRepository: SettingsRepository,
            validator: ApiKeyValidator
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(secretStore, settingsRepository, validator) as T
        }
    }
}
