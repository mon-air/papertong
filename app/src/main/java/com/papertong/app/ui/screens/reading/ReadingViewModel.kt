package com.papertong.app.ui.screens.reading

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.papertong.app.core.history.FileHistoryStore
import com.papertong.app.core.history.HistoryEntry
import com.papertong.app.core.model.ReadingLayout
import com.papertong.app.core.model.modelLabel
import com.papertong.app.core.model.styleLabel
import com.papertong.app.core.reading.MarkdownExporter
import com.papertong.app.core.reading.ParagraphAligner
import com.papertong.app.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReadingUiState(
    val loading: Boolean = true,
    val entry: HistoryEntry? = null,
    val paragraphs: List<ParagraphAligner.Pair> = emptyList(),
    /** 原文与译文的段落数是否一致；false 时界面要提示可能错位。 */
    val aligned: Boolean = true,
    val sourceCount: Int = 0,
    val targetCount: Int = 0,
    val fontScale: Float = 1f,
    val layout: ReadingLayout = ReadingLayout.Stacked
) {
    val notFound: Boolean get() = !loading && entry == null
}

/** 已生成好、等待用户选择保存位置的导出内容。 */
data class ExportRequest(val fileName: String, val content: String)

data class ExportUiState(
    /**
     * 需要界面拉起一次"保存"对话框。
     *
     * 这个标记放在 ViewModel 而不是 Compose 的 rememberSaveable 里：
     * 两者生命周期不同步（ViewModel 活过转屏但活不过进程回收），
     * 分开存会错位——转屏后重复弹窗，或进程回收后下一次导出被静默跳过。
     */
    val launchPending: Boolean = false,
    /** 待写入的内容，必须由 ViewModel 持有——转屏时 ActivityResult 会重投。 */
    val request: ExportRequest? = null,
    val writing: Boolean = false,
    val message: String? = null
)

class ReadingViewModel(
    private val historyStore: FileHistoryStore,
    private val settingsRepository: SettingsRepository,
    private val entryId: String
) : ViewModel() {

    /**
     * 首次进入时读一次磁盘：从历史页进来时内存里通常已有数据，
     * 但进程被回收后直接从阅读页恢复（返回栈重建）时没有。
     */
    private val loading = MutableStateFlow(true)

    private val entryFlow = historyStore.entries.map { list ->
        list.firstOrNull { it.id == entryId }
    }

    /**
     * 段落对齐算在 Default 线程：一篇 40 万字的论文按空行切段并不便宜。
     *
     * 单独成一条 flow 而不是塞进 combine：combine 里任何一路变化都会重算，
     * 那样调一次字号就要把整篇文章重新切一遍。分成独立 flow 后，
     * 只有历史列表真的变了才会重算。
     */
    private val alignedFlow = entryFlow.map { entry ->
        if (entry == null) null
        else withContext(Dispatchers.Default) {
            ParagraphAligner.align(entry.sourceText, entry.translatedText)
        }
    }

    val state: StateFlow<ReadingUiState> = combine(
        entryFlow,
        alignedFlow,
        settingsRepository.readingFontScale,
        settingsRepository.readingLayout,
        loading
    ) { entry, aligned, scale, layout, isLoading ->
        ReadingUiState(
            loading = isLoading,
            entry = entry,
            paragraphs = aligned?.pairs ?: emptyList(),
            aligned = aligned?.aligned ?: true,
            sourceCount = aligned?.sourceCount ?: 0,
            targetCount = aligned?.targetCount ?: 0,
            fontScale = scale,
            layout = layout
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingUiState())

    private val _export = MutableStateFlow(ExportUiState())
    val export: StateFlow<ExportUiState> = _export.asStateFlow()

    init {
        viewModelScope.launch {
            historyStore.loadAll()
            loading.value = false
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setReadingFontScale(scale) }
    }

    fun setLayout(layout: ReadingLayout) {
        viewModelScope.launch { settingsRepository.setReadingLayout(layout) }
    }

    /**
     * 先在后台拼好 Markdown，再交给系统"保存"对话框。
     * 不在这里直接写文件：用户可能取消，也可能选一个不能写的位置。
     */
    fun prepareExport() {
        val entry = state.value.entry ?: return
        viewModelScope.launch {
            val content = withContext(Dispatchers.Default) {
                MarkdownExporter.build(entry, styleLabel(entry.styleId), modelLabel(entry.modelId))
            }
            _export.value = _export.value.copy(
                launchPending = true,
                request = ExportRequest(
                    fileName = MarkdownExporter.suggestedFileName(entry),
                    content = content
                ),
                message = null
            )
        }
    }

    /**
     * 界面已经拉起过对话框，把标记清掉。
     * 必须在 launch() 之前调用：ActivityResult 回来时是新的重组帧，
     * 留到那时候再清会让对话框弹两次。
     */
    fun onLauncherShown() {
        _export.value = _export.value.copy(launchPending = false)
    }

    fun writeExport(context: Context, uri: Uri) {
        val request = _export.value.request
        if (request == null) {
            // 进程在"保存"对话框打开期间被系统回收过：待写的内容已随 ViewModel 消失。
            // 明确告诉用户，而不是静默什么都不做、让人以为存好了。
            _export.value = _export.value.copy(
                writing = false,
                message = "导出内容已失效，请重新导出"
            )
            return
        }
        _export.value = _export.value.copy(writing = true)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("无法打开目标文件")
                    stream.use { it.write(request.content.toByteArray(Charsets.UTF_8)) }
                }
            }

            _export.value = result.fold(
                onSuccess = {
                    _export.value.copy(
                        writing = false,
                        message = "已导出 ${request.fileName}"
                    )
                },
                onFailure = {
                    _export.value.copy(
                        writing = false,
                        message = "导出失败：${it.message ?: "未知错误"}"
                    )
                }
            )
        }
    }

    fun cancelExport() {
        // 用户取消了"保存"对话框，内容没用了就丢掉，没必要留在内存里
        _export.value = _export.value.copy(request = null, writing = false)
    }

    fun consumeMessage() {
        _export.value = _export.value.copy(message = null)
    }

    companion object {
        fun factory(
            store: FileHistoryStore,
            settingsRepository: SettingsRepository,
            entryId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReadingViewModel(store, settingsRepository, entryId) as T
        }
    }
}
