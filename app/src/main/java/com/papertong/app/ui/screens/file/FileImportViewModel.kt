package com.papertong.app.ui.screens.file

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.papertong.app.data.file.DocumentParser
import com.papertong.app.data.file.OcrFallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FileImportUiState(
    val fileName: String? = null,
    val kind: DocumentParser.Kind? = null,
    val parsing: Boolean = false,
    val text: String = "",
    val charCount: Int = 0,
    val pageCount: Int? = null,
    val notes: List<String> = emptyList(),
    val error: String? = null,
    val hasResult: Boolean = false
) {
    val canUse: Boolean get() = text.isNotBlank() && !parsing
}

/**
 * 文件导入页的状态。
 *
 * 关键约束：解析完成后**不能自动跳到翻译**。
 * 隐私说明里承诺了"发送前会明确提示字符数，你可以取消"，
 * 所以这里停在预览态，由用户按"拿去翻译"才继续。
 */
class FileImportViewModel(
    private val ocrFallback: OcrFallback?
) : ViewModel() {

    private val _state = MutableStateFlow(FileImportUiState())
    val state: StateFlow<FileImportUiState> = _state.asStateFlow()

    fun onFilePicked(context: Context, uri: Uri) {
        _state.value = FileImportUiState(parsing = true)

        viewModelScope.launch {
            when (val result = DocumentParser.parse(context, uri, ocrFallback)) {
                is DocumentParser.Result.Success -> {
                    val doc = result.document
                    _state.value = FileImportUiState(
                        fileName = doc.fileName,
                        kind = doc.kind,
                        parsing = false,
                        text = doc.text,
                        charCount = doc.text.length,
                        pageCount = doc.pageCount,
                        notes = doc.notes,
                        hasResult = true
                    )
                }

                is DocumentParser.Result.Failure -> {
                    _state.value = FileImportUiState(parsing = false, error = result.message)
                }
            }
        }
    }

    fun cancel() {
        // ViewModel 作用域内的子任务会随页面销毁取消；
        // 这里只把界面切回可操作的初始态。
        _state.value = FileImportUiState()
    }

    companion object {
        fun factory(ocrFallback: OcrFallback?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FileImportViewModel(ocrFallback) as T
            }
    }
}
