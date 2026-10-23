package com.papertong.app.ui.screens.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.TranslateStyle
import com.papertong.app.core.settings.SettingsRepository
import com.papertong.app.data.translate.TranslationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TranslateUiState(
    val input: String = "",
    val output: String = "",
    val running: Boolean = false,
    /** 已完成块数 / 总块数；总数为 1 时不显示进度。 */
    val chunkDone: Int = 0,
    val chunkTotal: Int = 0,
    val error: String? = null,
    /** 上一次失败是否可重试，决定是否显示「重试」按钮。 */
    val canRetry: Boolean = false
) {
    val canTranslate: Boolean get() = input.isNotBlank() && !running
}

class TranslateViewModel(
    private val translationRepository: TranslationRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    val style: StateFlow<TranslateStyle> = settingsRepository.translateStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslateStyle.Academic)

    val model: StateFlow<ModelOption> = settingsRepository.model
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelOption.Chat)

    private var job: Job? = null

    fun onInputChange(value: String) {
        _state.value = _state.value.copy(input = value, error = null, canRetry = false)
    }

    fun translate() {
        val current = _state.value
        if (current.running || current.input.isBlank()) return

        job?.cancel()
        _state.value = current.copy(
            output = "",
            running = true,
            chunkDone = 0,
            chunkTotal = 0,
            error = null,
            canRetry = false
        )

        val buffer = StringBuilder()

        job = viewModelScope.launch {
            translationRepository.translate(
                text = current.input,
                style = style.value,
                model = model.value
            ).collect { event ->
                when (event) {
                    is TranslationRepository.Event.ChunkStart -> {
                        _state.value = _state.value.copy(
                            chunkTotal = event.total,
                            chunkDone = event.index - 1
                        )
                        // 块之间用空行分隔，保持段落边界
                        if (event.index > 1) {
                            buffer.append("\n\n")
                            _state.value = _state.value.copy(output = buffer.toString())
                        }
                    }

                    is TranslationRepository.Event.Delta -> {
                        buffer.append(event.text)
                        // 每收到一个增量就刷新状态，Compose 会做重绘合并，
                        // 不需要自己节流
                        _state.value = _state.value.copy(output = buffer.toString())
                    }

                    is TranslationRepository.Event.ChunkDone -> {
                        _state.value = _state.value.copy(chunkDone = event.index)
                    }

                    is TranslationRepository.Event.Failed -> {
                        _state.value = _state.value.copy(
                            running = false,
                            error = event.message,
                            canRetry = event.retryable
                        )
                    }
                }
            }

            // 正常结束（非失败）时收尾
            if (_state.value.running) {
                _state.value = _state.value.copy(running = false)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false)
    }

    fun clear() {
        cancel()
        _state.value = TranslateUiState()
    }

    companion object {
        fun factory(
            translationRepository: TranslationRepository,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TranslateViewModel(translationRepository, settingsRepository) as T
        }
    }
}
