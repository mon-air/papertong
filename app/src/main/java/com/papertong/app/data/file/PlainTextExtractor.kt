package com.papertong.app.data.file

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown 与纯文本。
 *
 * 这两类文件本身就是文本，不做任何"美化"（不渲染、不动 Markdown 标记），
 * 因为模型能读懂 `##` 和 `**`，擅自去掉反而会丢失结构信息。
 * 只做编码识别和换行统一。
 */
internal object PlainTextExtractor {

    suspend fun extract(
        context: Context,
        uri: Uri,
        kind: DocumentParser.Kind
    ): DocumentParser.Result = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext DocumentParser.Result.Failure("无法打开文件")

            val raw = decode(bytes)
            if (raw.isBlank()) {
                return@withContext DocumentParser.Result.Failure("文件是空的")
            }

            val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
            val (text, truncated) = TextCleaner.truncateAtParagraph(
                TextCleaner.normalizeBlankLines(normalized),
                DocumentParser.MAX_TEXT_CHARS
            )

            val notes = buildList {
                if (truncated) add("文件较长，只导入了前 $MAX_KILO_CHARS 千字")
            }

            DocumentParser.Result.Success(
                DocumentParser.Document(
                    kind = kind,
                    text = text,
                    fileName = null,
                    pageCount = null,
                    notes = notes
                )
            )
        } catch (e: IOException) {
            DocumentParser.Result.Failure("读取文件失败：${e.message ?: "I/O 错误"}")
        } catch (e: SecurityException) {
            DocumentParser.Result.Failure("没有读取该文件的权限")
        }
    }

    /**
     * 编码识别。
     *
     * 中文用户常见的 txt 可能是 GBK。先看 BOM，再按 UTF-8 严格解码，
     * 失败则回退 GBK——比引一个编码探测库轻得多，也够用。
     */
    private fun decode(bytes: ByteArray): String {
        // UTF-8 BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        // UTF-16 BOM
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }

        return try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            // 不是合法 UTF-8，按 GBK 处理（简体中文环境最常见）
            String(bytes, charset("GBK"))
        }
    }

    private const val MAX_KILO_CHARS = DocumentParser.MAX_TEXT_CHARS / 1000
}
