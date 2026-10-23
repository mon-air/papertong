package com.papertong.app.ui.screens.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.papertong.app.data.ocr.OcrEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OcrUiState(
    /** 已降采样的预览图，供界面直接绘制。 */
    val preview: Bitmap? = null,
    val recognizing: Boolean = false,
    val text: String = "",
    val error: String? = null,
    /** 是否已至少成功识别过一次，用于决定 UI 展示阶段。 */
    val hasResult: Boolean = false
) {
    val canTranslate: Boolean get() = text.isNotBlank() && !recognizing
}

class OcrViewModel(
    private val engine: OcrEngine
) : ViewModel() {

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    private var currentUri: Uri? = null

    fun onImagePicked(context: Context, uri: Uri) {
        currentUri = uri
        // 旧预览图不手动 recycle：Compose 可能在同一帧还在绘制它，
        // 回收会崩在 "trying to use a recycled bitmap"。预览图已降采样，交给 GC。
        _state.value = OcrUiState(recognizing = true)
        load(context, uri)
    }

    fun retry(context: Context) {
        val uri = currentUri ?: return
        _state.value = _state.value.copy(recognizing = true, error = null)
        recognize(context, uri)
    }

    /** 用户手动修正识别结果。 */
    fun onTextChange(value: String) {
        _state.value = _state.value.copy(text = value, error = null)
    }

    private fun load(context: Context, uri: Uri) {
        viewModelScope.launch {
            val preview = engine.preview(context, uri)
            if (preview != null) {
                _state.value = _state.value.copy(preview = preview)
            }
            recognize(context, uri)
        }
    }

    private fun recognize(context: Context, uri: Uri) {
        viewModelScope.launch {
            when (val result = engine.recognize(context, uri)) {
                is OcrEngine.Result.Success -> {
                    _state.value = _state.value.copy(
                        recognizing = false,
                        text = result.text,
                        hasResult = true,
                        error = if (result.text.isBlank()) "没有识别到文字" else null
                    )
                }

                is OcrEngine.Result.Failure -> {
                    _state.value = _state.value.copy(
                        recognizing = false,
                        error = result.message
                    )
                }
            }
        }
    }

    /**
     * 取出识别结果，供跳转到翻译页时传递。
     * 拍照产生的临时照片在这里清理，避免长期占用缓存。
     */
    fun consumeResult(context: Context): String {
        currentUri?.let { CameraCapture.cleanup(context, it) }
        return _state.value.text
    }

    companion object {
        fun factory(engine: OcrEngine): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OcrViewModel(engine) as T
            }
    }
}
