package com.papertong.app.ui.screens.file

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertong.app.data.file.DocumentParser
import com.papertong.app.ui.LocalAppContainer
import com.papertong.app.ui.components.PaperCard
import com.papertong.app.ui.components.SimpleTopBar
import com.papertong.app.ui.theme.PaperTongTheme
import com.papertong.app.ui.theme.ReadingTextStyle

@Composable
fun FileImportScreen(
    onBack: () -> Unit,
    onUseText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val viewModel: FileImportViewModel = viewModel(
        factory = FileImportViewModel.factory(container.pdfOcrFallback)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // SAF(OpenDocument)：不需要任何存储权限，用户只授予所选文件
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onFilePicked(context, uri)
    }

    FileImportScreenContent(
        state = state,
        onBack = onBack,
        onPickFile = { pickFile.launch(SUPPORTED_MIME_TYPES) },
        onCancel = viewModel::cancel,
        onUseText = { onUseText(state.text) },
        modifier = modifier
    )
}

@Composable
private fun FileImportScreenContent(
    state: FileImportUiState,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onCancel: () -> Unit,
    onUseText: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SimpleTopBar(title = "文件导入", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "文件在本机解析，原始文件不会上传。支持 PDF、.docx、.md、.txt，" +
                    DocumentParser.describeLimit() + "。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onPickFile,
                enabled = !state.parsing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(if (state.hasResult) "换个文件" else "选择文件")
            }

            if (state.parsing) {
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text(
                        text = if (state.kind == DocumentParser.Kind.PDF) {
                            "正在解析…扫描件会用本机 OCR 逐页识别，可能需要一会儿"
                        } else {
                            "正在解析…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.error?.let { message ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.hasResult) {
                Spacer(Modifier.height(20.dp))

                // 关键：发送前把字符数摆出来，兑现隐私页的承诺
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = state.fileName ?: "准备导入",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${state.kind?.label ?: "文件"} · ${state.charCount} 字" +
                                (state.pageCount?.let { " · $it 页" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "接下来会把这 ${state.charCount} 字的正文发送给 DeepSeek 翻译。" +
                                "文件本身和文件名不会发送。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    state.notes.forEach { note ->
                        Text(
                            text = "· $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("取消") }
                    Button(
                        onClick = onUseText,
                        enabled = state.canUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("拿去翻译") }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "内容预览",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.text.take(PREVIEW_CHARS) +
                            if (state.text.length > PREVIEW_CHARS) "\n\n…（后面还有 ${
                                state.text.length - PREVIEW_CHARS
                            } 字）" else "",
                        style = ReadingTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// 显式列出 MIME：只给通配符会让用户在系统选择器里看到一堆打不开的文件。
// 部分设备把 .md 认成 text/plain，两种情况都覆盖。
private val SUPPORTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/markdown",
    "text/plain",
    "text/x-markdown"
)

/** 预览只取前若干字，避免长论文一次性把这段文本全部排版。 */
private const val PREVIEW_CHARS = 1200

@Preview(showBackground = true)
@Composable
private fun FileImportScreenPreview() {
    PaperTongTheme {
        FileImportScreenContent(
            state = FileImportUiState(),
            onBack = {},
            onPickFile = {},
            onCancel = {},
            onUseText = {}
        )
    }
}
