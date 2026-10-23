package com.papertong.app.ui.screens.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.papertong.app.core.history.HistoryEntry
import com.papertong.app.core.history.HistoryStore
import com.papertong.app.core.model.DirectionPair
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.TranslateDirection
import com.papertong.app.core.model.TranslateStyle
import com.papertong.app.core.settings.SettingsRepository
import com.papertong.app.data.translate.TranslationRepository
import java.util.UUID
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
    val chunkDone: Int = 0,
    val chunkTotal: Int = 0,
    val error: String? = null,
    val canRetry: Boolean = false,
    /** 本次翻译实际采用的方向；自动模式下随输入变化重新计算。 */
    val resolvedDirection: DirectionPair? = null
) {
    val canTranslate: Boolean get() = input.isNotBlank() && !running
}

class TranslateViewModel(
    private val translationRepository: TranslationRepository,
    private val historyStore: HistoryStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    val style: StateFlow<TranslateStyle> = settingsRepository.translateStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslateStyle.Academic)

    val model: StateFlow<ModelOption> = settingsRepository.model
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelOption.Chat)

    val direction: StateFlow<TranslateDirection> = settingsRepository.direction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslateDirection.Auto)

    private var job: Job? = null

    fun onInputChange(value: String) {
        // 自动模式下实时显示将要采用的方向，用户需在按下翻译前就知道结果
        val resolved = direction.value.resolve(value)
        _state.value = _state.value.copy(
            input = value,
            error = null,
            canRetry = false,
            resolvedDirection = resolved
        )
    }

    fun setDirection(value: TranslateDirection) {
        viewModelScope.launch {
            settingsRepository.setDirection(value)
            _state.value = _state.value.copy(
                resolvedDirection = value.resolve(_state.value.input)
            )
        }
    }

    fun translate() {
        val current = _state.value
        if (current.running || current.input.isBlank()) return

        job?.cancel()

        val resolved = direction.value.resolve(current.input)
        val styleAtStart = style.value
        val modelAtStart = model.value

        _state.value = current.copy(
            output = "",
            running = true,
            chunkDone = 0,
            chunkTotal = 0,
            error = null,
            canRetry = false,
            resolvedDirection = resolved
        )

        val buffer = StringBuilder()

        job = viewModelScope.launch {
            var succeeded = true

            translationRepository.translate(
                text = current.input,
                style = styleAtStart,
                model = modelAtStart,
                direction = resolved
            ).collect { event ->
                when (event) {
                    is TranslationRepository.Event.Direction -> Unit

                    is TranslationRepository.Event.ChunkStart -> {
                        _state.value = _state.value.copy(
                            chunkTotal = event.total,
                            chunkDone = event.index - 1
                        )
                        if (event.index > 1) {
                            buffer.append("\n\n")
                            _state.value = _state.value.copy(output = buffer.toString())
                        }
                    }

                    is TranslationRepository.Event.Delta -> {
                        buffer.append(event.text)
                        _state.value = _state.value.copy(output = buffer.toString())
                    }

                    is TranslationRepository.Event.ChunkDone -> {
                        _state.value = _state.value.copy(chunkDone = event.index)
                    }

                    is TranslationRepository.Event.Failed -> {
                        succeeded = false
                        _state.value = _state.value.copy(
                            running = false,
                            error = event.message,
                            canRetry = event.retryable
                        )
                    }
                }
            }

            if (!_state.value.running) return@launch

            _state.value = _state.value.copy(running = false)

            // 只要有产出就存档，即使中途失败：部分译文往往仍有价值
            val produced = buffer.toString().trim()
            if (succeeded && produced.isNotEmpty()) {
                historyStore.add(
                    HistoryEntry(
                        id = UUID.randomUUID().toString(),
                        createdAt = System.currentTimeMillis(),
                        sourceText = current.input.trim(),
                        translatedText = produced,
                        styleId = styleAtStart.id,
                        modelId = modelAtStart.id,
                        directionLabel = resolved.label
                    )
                )
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
            historyStore: HistoryStore,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TranslateViewModel(translationRepository, historyStore, settingsRepository) as T
        }
    }
}
