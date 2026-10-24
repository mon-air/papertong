package com.papertong.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.papertong.app.core.history.HistoryEntry
import com.papertong.app.core.model.modelLabel
import com.papertong.app.core.model.styleLabel
import com.papertong.app.ui.LocalAppContainer
import com.papertong.app.ui.components.PaperCard
import com.papertong.app.ui.components.SimpleTopBar
import com.papertong.app.ui.theme.PaperTongTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenReading: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(container.historyStore)
    )
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    HistoryScreenContent(
        entries = entries,
        onBack = onBack,
        onRemove = viewModel::remove,
        onClearAll = viewModel::clearAll,
        onOpenReading = onOpenReading,
        modifier = modifier
    )
}

@Composable
private fun HistoryScreenContent(
    entries: List<HistoryEntry>,
    onBack: () -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onOpenReading: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingClear by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SimpleTopBar(
            title = "翻译历史",
            onBack = onBack,
            actions = {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { pendingClear = true }) { Text("清空") }
                }
            }
        )

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "还没有翻译记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "完成的翻译会自动保存在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                HistoryCard(
                    entry = entry,
                    expanded = expandedId == entry.id,
                    onToggle = {
                        expandedId = if (expandedId == entry.id) null else entry.id
                    },
                    onOpenReading = { onOpenReading(entry.id) },
                    onRemove = { onRemove(entry.id) }
                )
            }
        }
    }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("清空全部历史？") },
            text = { Text("本机保存的 ${entries.size} 条记录将被删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        pendingClear = false
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenReading: () -> Unit,
    onRemove: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        // 点卡片进对照阅读：这是用户看到一条记录时最可能想做的事。
        // 想看全文又不想离页的，用下面的"展开/收起"。
        onClick = onOpenReading
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.directionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = formatTime(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除这条",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.sourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = entry.translatedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${styleLabel(entry.styleId)} · ${modelLabel(entry.modelId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "收起" else "展开")
                }
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(entry.translatedText)) }
                ) { Text("复制译文") }
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMillis))
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    PaperTongTheme {
        HistoryScreenContent(
            entries = listOf(
                HistoryEntry(
                    id = "1",
                    createdAt = System.currentTimeMillis(),
                    sourceText = "Attention is all you need.",
                    translatedText = "注意力机制就是你所需要的全部。",
                    styleId = "academic",
                    modelId = "deepseek-chat",
                    directionLabel = "英 → 中"
                )
            ),
            onBack = {},
            onRemove = {},
            onClearAll = {},
            onOpenReading = {}
        )
    }
}
