package com.papertong.app.core.reading

import com.papertong.app.core.history.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把一条翻译记录导出成 Markdown。
 *
 * 结构：YAML front matter（便于 Obsidian 等工具索引）+ 译文 + 原文。
 * 刻意不做逐段交替的"原文/译文"小节——一篇论文动辄几十段，
 * 那样会生成上百个小标题，反而没法读。逐段对照留给 App 内的阅读页。
 *
 * 导出的是两侧各自完整的文本，因此段落数不一致也不影响正确性。
 */
object MarkdownExporter {

    fun build(entry: HistoryEntry, styleLabel: String, modelLabel: String): String {
        val builder = StringBuilder()

        builder.append("---\n")
        builder.append("title: 论文翻译\n")
        builder.append("created: ").append(formatDate(entry.createdAt)).append('\n')
        builder.append("direction: ").append(quote(entry.directionLabel)).append('\n')
        builder.append("style: ").append(quote(styleLabel)).append('\n')
        builder.append("model: ").append(quote(modelLabel)).append('\n')
        builder.append("---\n\n")

        // front matter 之后重复一次元信息：正文被单独复制走时不至于失去上下文
        builder.append("# 论文翻译\n\n")
        builder.append("> ").append(entry.directionLabel)
            .append(" · ").append(styleLabel)
            .append(" · ").append(modelLabel)
            .append(" · ").append(formatDate(entry.createdAt))
            .append("\n\n")

        builder.append("## 译文\n\n")
        builder.append(normalize(entry.translatedText)).append("\n\n")

        builder.append("## 原文\n\n")
        builder.append(normalize(entry.sourceText)).append("\n\n")

        return builder.toString().trimEnd() + "\n"
    }

    /** 建议的文件名。SAF 会把它填进"保存"对话框，用户仍可改。 */
    fun suggestedFileName(entry: HistoryEntry): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
            .format(Date(entry.createdAt))
        return "paper-$stamp.md"
    }

    /**
     * front matter 里的值用双引号包起来：
     * 方向标签含"→"，风格标签含中文标点，裸写容易被 YAML 解析器误判。
     * 双引号内只需转义反斜杠与引号本身。
     */
    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** 统一换行、去掉结尾空白，避免导出文件里混着 \r\n 与多余空行。 */
    private fun normalize(text: String): String =
        text.replace("\r\n", "\n").replace(Regex("\n{3,}"), "\n\n").trim()

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
