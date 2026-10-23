package com.papertong.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.TranslateStyle
import com.papertong.app.ui.LocalAppContainer
import com.papertong.app.ui.components.ChoiceItem
import com.papertong.app.ui.components.ChoiceSheet
import com.papertong.app.ui.components.PaperCard
import com.papertong.app.ui.components.SimpleTopBar
import com.papertong.app.ui.theme.PaperTongTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            container.secretStore,
            container.settingsRepository,
            container.apiKeyValidator
        )
    )

    val style by viewModel.style.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val apiKeyState by viewModel.apiKeyState.collectAsStateWithLifecycle()

    var showKeyDialog by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    // 校验通过后自动收起对话框，提示留在页面上
    LaunchedEffect(apiKeyState.justSaved) {
        if (apiKeyState.justSaved) {
            showKeyDialog = false
        }
    }

    SettingsScreenContent(
        style = style,
        model = model,
        hasApiKey = hasApiKey,
        apiKeyState = apiKeyState,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        onEditApiKey = {
            viewModel.resetInput()
            showKeyDialog = true
        },
        onDeleteApiKey = viewModel::deleteApiKey,
        onPickStyle = { showStyleSheet = true },
        onPickModel = { showModelSheet = true },
        modifier = modifier
    )

    if (showKeyDialog) {
        ApiKeyDialog(
            state = apiKeyState,
            onInputChange = viewModel::onApiKeyInputChange,
            onToggleVisibility = viewModel::toggleVisibility,
            onConfirm = viewModel::saveApiKey,
            onDismiss = {
                viewModel.clearMessage()
                showKeyDialog = false
            }
        )
    }

    if (showStyleSheet) {
        ChoiceSheet(
            title = "翻译风格",
            items = TranslateStyle.entries.map {
                ChoiceItem(it, it.label, it.hint)
            },
            selected = style,
            onSelect = viewModel::setStyle,
            onDismiss = { showStyleSheet = false }
        )
    }

    if (showModelSheet) {
        ChoiceSheet(
            title = "模型",
            items = ModelOption.entries.map {
                ChoiceItem(it, it.label, it.hint)
            },
            selected = model,
            onSelect = viewModel::setModel,
            onDismiss = { showModelSheet = false }
        )
    }
}

@Composable
private fun SettingsScreenContent(
    style: TranslateStyle,
    model: ModelOption,
    hasApiKey: Boolean,
    apiKeyState: ApiKeyUiState,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onEditApiKey: () -> Unit,
    onDeleteApiKey: () -> Unit,
    onPickStyle: () -> Unit,
    onPickModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SimpleTopBar(title = "设置", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            GroupLabel("接口")
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    title = "DeepSeek API Key",
                    value = if (hasApiKey) "已配置" else "未配置",
                    onClick = onEditApiKey
                )
                RowDivider()
                SettingsRow(
                    title = "模型",
                    value = model.label,
                    onClick = onPickModel
                )
            }

            if (hasApiKey) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "删除本机保存的 Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable(onClick = onDeleteApiKey)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            if (apiKeyState.message != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = apiKeyState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (apiKeyState.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            GroupLabel("翻译")
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    title = "默认风格",
                    value = style.label,
                    onClick = onPickStyle
                )
            }

            GroupLabel("关于")
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    title = "隐私与数据",
                    value = "",
                    onClick = onOpenAbout
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "版本 0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}

/**
 * 行本身不画背景（交给外层 PaperCard），只负责点击与排版。
 * 之前用 Surface 包裹会在按压后留下灰色残留，改用裸 Row + clickable。
 */
@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PaperTongTheme {
        SettingsScreenContent(
            style = TranslateStyle.Academic,
            model = ModelOption.Chat,
            hasApiKey = false,
            apiKeyState = ApiKeyUiState(),
            onBack = {},
            onOpenAbout = {},
            onEditApiKey = {},
            onDeleteApiKey = {},
            onPickStyle = {},
            onPickModel = {}
        )
    }
}
