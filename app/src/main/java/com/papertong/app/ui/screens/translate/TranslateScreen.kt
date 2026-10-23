package com.papertong.app.ui.screens.translate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertong.app.ui.LocalAppContainer
import com.papertong.app.ui.components.PaperCard
import com.papertong.app.ui.components.SimpleTopBar
import com.papertong.app.ui.theme.PaperTongTheme
import com.papertong.app.ui.theme.ReadingTextStyle

@Composable
fun TranslateScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel: TranslateViewModel = viewModel(
        factory = TranslateViewModel.factory(
            container.translationRepository,
            container.settingsRepository
        )
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val style by viewModel.style.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()

    TranslateScreenContent(
        state = state,
        styleLabel = style.label,
        modelLabel = model.label,
        onInputChange = viewModel::onInputChange,
        onTranslate = viewModel::translate,
        onCancel = viewModel::cancel,
        onClear = viewModel::clear,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

@Composable
private fun TranslateScreenContent(
    state: TranslateUiState,
    styleLabel: String,
    modelLabel: String,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        SimpleTopBar(
            title = "PaperTong",
            actions = {
                if (state.input.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("清空") }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ---- 原文输入 ----
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp),
                placeholder = {
                    Text(
                        text = "粘贴论文段落、摘要或任意文本…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(Modifier.height(10.dp))

            // ---- 风格 / 模型 提示 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$styleLabel · $modelLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${state.input.length} 字",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- 操作按钮 ----
            if (state.running) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text("停止") }
            } else {
                Button(
                    onClick = onTranslate,
                    enabled = state.canTranslate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("翻译") }
            }

            // ---- 进度 ----
            if (state.running && state.chunkTotal > 1) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.chunkDone.toFloat() / state.chunkTotal },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "第 ${state.chunkDone + 1} / ${state.chunkTotal} 段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state.running) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ---- 错误 ----
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (state.canRetry) {
                    TextButton(
                        onClick = onTranslate,
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("重试") }
                }
            }

            // ---- 译文 ----
            if (state.output.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "译文",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(state.output))
                            copied = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制译文",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (copied) {
                        Text(
                            text = "已复制",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.output,
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

@Preview(showBackground = true)
@Composable
private fun TranslateScreenPreview() {
    PaperTongTheme {
        TranslateScreenContent(
            state = TranslateUiState(
                input = "Attention is all you need.",
                output = "注意力机制就是你所需要的全部。"
            ),
            styleLabel = "学术专业",
            modelLabel = "deepseek-chat",
            onInputChange = {},
            onTranslate = {},
            onCancel = {},
            onClear = {},
            onOpenSettings = {}
        )
    }
}
