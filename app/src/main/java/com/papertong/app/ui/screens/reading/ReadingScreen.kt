package com.papertong.app.ui.screens.reading

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertong.app.core.history.HistoryEntry
import com.papertong.app.core.model.ReadingLayout
import com.papertong.app.core.model.modelLabel
import com.papertong.app.core.model.styleLabel
import com.papertong.app.core.reading.FormulaHighlighter
import com.papertong.app.core.reading.ParagraphAligner
import com.papertong.app.core.settings.DataStoreSettingsRepository
import com.papertong.app.ui.LocalAppContainer
import com.papertong.app.ui.components.ChoiceItem
import com.papertong.app.ui.components.ChoiceSheet
import com.papertong.app.ui.components.SimpleTopBar
import com.papertong.app.ui.theme.PaperTongTheme
import com.papertong.app.ui.theme.ReadingTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReadingScreen(
    entryId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val viewModel: ReadingViewModel = viewModel(
        factory = ReadingViewModel.factory(
            container.historyStore,
            container.settingsRepository,
            entryId
        )
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by viewModel.export.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // SAF 的"新建文档"：由用户挑位置，不需要存储权限，也不碰应用私有目录以外的东西
    val saveDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) viewModel.writeExport(context, uri) else viewModel.cancelExport()
    }

    /*
     * launchPending 由 ViewModel 维护，拉起一次就清掉。
     * 不在 Composable 里另存一份"已处理过"的标记：Compose 的状态与
     * ViewModel 的生命周期不同步，分成两处必然错位。
     */
    LaunchedEffect(exportState.launchPending) {
        val request = exportState.request ?: return@LaunchedEffect
        if (exportState.launchPending) {
            viewModel.onLauncherShown()
            // 设备上没有任何能接收 text/markdown 的应用时 launch 会抛异常。
            // 不接住的话 LaunchedEffect 会把整个界面带崩，而这里只是"存不了"
            runCatching { saveDocument.launch(request.fileName) }
                .onFailure { viewModel.reportExportError("没有可用的保存位置") }
        }
    }

    LaunchedEffect(exportState.message) {
        val message = exportState.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        ReadingScreenContent(
            state = state,
            exporting = exportState.writing,
            onBack = onBack,
            onExport = viewModel::prepareExport,
            onFontScaleChange = viewModel::setFontScale,
            onLayoutChange = viewModel::setLayout
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ReadingScreenContent(
    state: ReadingUiState,
    exporting: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onLayoutChange: (ReadingLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLayoutSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SimpleTopBar(
            title = "对照阅读",
            onBack = onBack,
            actions = {
                if (state.entry != null) {
                    TextButton(onClick = onExport, enabled = !exporting) {
                        Text(if (exporting) "导出中…" else "导出")
                    }
                }
            }
        )

        when {
            state.loading -> Centered { CircularProgressIndicator(strokeWidth = 2.dp) }

            state.notFound -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "找不到这条记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "它可能已被删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp
                )
            ) {
                item(key = "header") {
                    ReadingHeader(
                        state = state,
                        onFontScaleChange = onFontScaleChange,
                        onPickLayout = { showLayoutSheet = true }
                    )
                }

                itemsIndexed(state.paragraphs, key = { _, pair -> pair.index }) { _, pair ->
                    ParagraphPairBlock(pair = pair, state = state)
                }
            }
        }
    }

    if (showLayoutSheet) {
        ChoiceSheet(
            title = "对照排布",
            items = LAYOUT_ITEMS,
            selected = state.layout,
            onSelect = onLayoutChange,
            onDismiss = { showLayoutSheet = false }
        )
    }
}

private val LAYOUT_ITEMS = ReadingLayout.entries.map { ChoiceItem(it, it.label, it.hint) }

@Composable
private fun ReadingHeader(
    state: ReadingUiState,
    onFontScaleChange: (Float) -> Unit,
    onPickLayout: () -> Unit
) {
    val entry = state.entry ?: return

    Column {
        Spacer(Modifier.height(4.dp))

        Text(
            text = "${entry.directionLabel} · ${styleLabel(entry.styleId)} · ${modelLabel(entry.modelId)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatFullTime(entry.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${(state.fontScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = { onFontScaleChange(state.fontScale - FONT_STEP) },
                enabled = state.fontScale > DataStoreSettingsRepository.MIN_READING_FONT_SCALE
            ) { Text("A−") }
            TextButton(
                onClick = { onFontScaleChange(state.fontScale + FONT_STEP) },
                enabled = state.fontScale < DataStoreSettingsRepository.MAX_READING_FONT_SCALE
            ) { Text("A+") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onPickLayout) { Text(state.layout.label) }
        }

        if (!state.aligned) {
            Spacer(Modifier.height(6.dp))
            Notice(
                text = "原文 ${state.sourceCount} 段、译文 ${state.targetCount} 段，" +
                    "段数不一致，按顺序对照仅供参考。"
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ParagraphPairBlock(
    pair: ParagraphAligner.Pair,
    state: ReadingUiState
) {
    val baseStyle = ReadingTextStyle.copy(
        fontSize = ReadingTextStyle.fontSize * state.fontScale,
        lineHeight = ReadingTextStyle.lineHeight * state.fontScale
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "§${pair.index}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(8.dp))
            // 必须给 weight：HorizontalDivider 自带 fillMaxWidth，
            // 放在 Row 里会按整行宽度排，把段号挤出屏幕
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
        }
        Spacer(Modifier.height(10.dp))

        when (state.layout) {
            ReadingLayout.Stacked -> Column {
                pair.source?.let { source ->
                    SectionLabel("原文")
                    FormulaText(
                        text = source,
                        style = baseStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                pair.target?.let { target ->
                    SectionLabel("译文")
                    FormulaText(
                        text = target,
                        style = baseStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            ReadingLayout.SideBySide -> Row(Modifier.height(IntrinsicSize.Min)) {
                Column(Modifier.weight(1f)) {
                    pair.source?.let { source ->
                        SectionLabel("原文")
                        FormulaText(
                            text = source,
                            style = baseStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 分隔线要撑满一行的高度：Row 的高度由内容决定，
                // 所以必须用 MinIntrinsic 量一次，fillMaxHeight 才有参照
                VerticalDivider(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Column(Modifier.weight(1f)) {
                    pair.target?.let { target ->
                        SectionLabel("译文")
                        FormulaText(
                            text = target,
                            style = baseStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * 正文渲染。公式区间改用等宽字体——不渲染 LaTeX，只保证
 * 上下标标记不被当成普通文字误读。
 */
@Composable
private fun FormulaText(
    text: String,
    style: TextStyle,
    color: Color
) {
    val annotated = remember(text) { highlightFormulas(text) }
    Text(text = annotated, style = style, color = color)
}

private fun highlightFormulas(text: String): AnnotatedString {
    val spans = FormulaHighlighter.findSpans(text)
    if (spans.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            addStyle(
                style = SpanStyle(fontFamily = FontFamily.Monospace),
                start = span.start,
                end = span.end
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

/** 每次点击改变的倍率。0.1 是肉眼可辨、又不至于一步跳太远的量。 */
private const val FONT_STEP = 0.1f

private fun formatFullTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ReadingScreenPreview() {
    val entry = HistoryEntry(
        id = "1",
        createdAt = 1_700_000_000_000,
        sourceText = "Attention is all you need.",
        translatedText = "注意力机制就是你所需要的全部。",
        styleId = "academic",
        modelId = "deepseek-chat",
        directionLabel = "英 → 中"
    )
    PaperTongTheme {
        ReadingScreenContent(
            state = ReadingUiState(
                loading = false,
                entry = entry,
                paragraphs = ParagraphAligner.align(entry.sourceText, entry.translatedText).pairs,
                aligned = true,
                sourceCount = 1,
                targetCount = 1,
                fontScale = DataStoreSettingsRepository.DEFAULT_READING_FONT_SCALE,
                layout = ReadingLayout.Stacked
            ),
            exporting = false,
            onBack = {},
            onExport = {},
            onFontScaleChange = {},
            onLayoutChange = {}
        )
    }
}
