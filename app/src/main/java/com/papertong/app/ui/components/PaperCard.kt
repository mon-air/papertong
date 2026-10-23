package com.papertong.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** 全局统一的卡片圆角，避免各页面各自写数字。 */
val PaperCardShape = RoundedCornerShape(14.dp)

/**
 * 纸感卡片，所有分组内容的统一容器。onClick 为 null 时不可点击。
 *
 * 点击修饰符放在 Surface 外侧的 modifier 上（先 clip 再 clickable），
 * 这样涟漪会被裁在圆角内；组内多行各自的 clickable 也不会互相干扰。
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier
            .clip(PaperCardShape)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        shape = PaperCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.then(clickModifier)
    ) {
        Column(content = content)
    }
}
