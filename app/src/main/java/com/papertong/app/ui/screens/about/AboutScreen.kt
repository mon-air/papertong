package com.papertong.app.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.papertong.app.ui.components.SimpleTopBar
import com.papertong.app.ui.theme.PaperTongTheme

/**
 * 隐私说明。措辞刻意保守，不做无法兑现的承诺：
 * 文件不上传，但翻译所需的文本会发送给 DeepSeek。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SimpleTopBar(title = "隐私与数据", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Section(
                title = "本机完成的部分",
                body = "PDF / Word / Markdown 的解析、图片文字识别（OCR）全部在设备本地完成。" +
                    "原始文件与照片不会被上传，也不会离开本机。"
            )
            Section(
                title = "会发送到服务器的部分",
                body = "为了完成翻译，从文件中提取出的正文文本会被发送至 DeepSeek 接口。" +
                    "发送前会明确提示本次要发送的字符数，你可以取消。文件名、文件路径、图片本身不会发送。"
            )
            Section(
                title = "第三方如何处理数据",
                body = "文本发送给 DeepSeek 后，其处理方式受 DeepSeek 服务条款与隐私政策约束。" +
                    "本应用无法代其作出承诺，也不提供端到端加密。如果你对数据外发有严格要求，请不要使用翻译功能。"
            )
            Section(
                title = "API Key",
                body = "Key 经 Android Keystore 加密后仅保存在本机，不参与系统备份，" +
                    "不会出现在日志中，也不会发往 DeepSeek 以外的任何地址。"
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    PaperTongTheme {
        AboutScreen(onBack = {})
    }
}
