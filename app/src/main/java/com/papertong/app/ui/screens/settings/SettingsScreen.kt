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
import androidx.compose.material3.TextButton
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
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertong.app.BuildConfig
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.ReadingLayout
import com.papertong.app.core.model.TranslateStyle
import com.papertong.app.core.settings.DataStoreSettingsRepository
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
    val readingFontScale by viewModel.readingFontScale.collectAsStateWithLifecycle()
    val readingLayout by viewModel.readingLayout.collectAsStateWithLifecycle()

    var showKeyDialog by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showLayoutSheet by remember { mutableStateOf(false) }

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
        readingFontScale = readingFontScale,
        readingLayout = readingLayout,
        onFontScaleChange = viewModel::setReadingFontScale,
        onPickLayout = { showLayoutSheet = true },
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

    if (showLayoutSheet) {
        ChoiceSheet(
            title = "对照排布",
            items = ReadingLayout.entries.map {
                ChoiceItem(it, it.label, it.hint)
            },
            selected = readingLayout,
            onSelect = viewModel::setReadingLayout,
            onDismiss = { showLayoutSheet = false }
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
    readingFontScale: Float,
    readingLayout: ReadingLayout,
    onFontScaleChange: (Float) -> Unit,
    onPickLayout: () -> Unit,
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

            GroupLabel("对照阅读")
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(
                    title = "排布方式",
                    value = readingLayout.label,
                    onClick = onPickLayout
                )
                RowDivider()
                FontScaleRow(scale = readingFontScale, onChange = onFontScaleChange)
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
                // 从 BuildConfig 读，避免改了 build.gradle 却忘了改这里
                text = "版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 字号用 ± 按钮而不是滑块：滑块要拖准 1.0 很难，
 * 而这里用户想要的通常是"大一点/小一点"。
 * 到达区间端点时按钮置灰，明确告诉用户没有更多可调。
 */
@Composable
private fun FontScaleRow(scale: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "正文字号",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { onChange(scale - FONT_SCALE_STEP) },
            enabled = scale > DataStoreSettingsRepository.MIN_READING_FONT_SCALE
        ) { Text("A−") }
        TextButton(
            onClick = { onChange(scale + FONT_SCALE_STEP) },
            enabled = scale < DataStoreSettingsRepository.MAX_READING_FONT_SCALE
        ) { Text("A+") }
    }
}

/** 与阅读页保持一致的步长；两处各有一份是刻意的，界面细节不该跨模块共享。 */
private const val FONT_SCALE_STEP = 0.1f

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
            onPickModel = {},
            readingFontScale = DataStoreSettingsRepository.DEFAULT_READING_FONT_SCALE,
            readingLayout = ReadingLayout.Stacked,
            onFontScaleChange = {},
            onPickLayout = {}
        )
    }
}
