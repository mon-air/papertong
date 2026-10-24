package com.papertong.app.data.file

import android.content.Context
import android.net.Uri
import android.util.Xml
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * .docx 正文抽取。
 *
 * .docx 就是一个 zip，正文在 word/document.xml。
 * 这里直接解 XML 而不是引入 Apache POI：POI 在 Android 上体积和兼容性都是坑，
 * 而我们只需要段落文字，不需要样式、表格合并之类的排版还原。
 *
 * 换行规则（对应 Word 的段落语义）：
 *  - `w:p`  → 段落，前后各一个换行
 *  - `w:br`、`w:cr` → 段内换行
 *  - `w:tab` → 制表符
 *  - `w:tbl` → 表格，按行拼接、单元格之间用制表符分隔
 */
internal object DocxTextExtractor {

    private const val ENTRY_DOCUMENT = "word/document.xml"

    /** XML 里可能出现的最大深度，超过视为异常文件。 */
    private const val MAX_DEPTH = 256

    private const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    suspend fun extract(context: Context, uri: Uri): DocumentParser.Result =
        withContext(Dispatchers.IO) {
            try {
                val xml = readDocumentXml(context, uri)
                    ?: return@withContext DocumentParser.Result.Failure(
                        "这不是有效的 .docx 文件（缺少 word/document.xml）"
                    )

                val paragraphs = xml.use { parseParagraphs(it) }
                if (paragraphs.isEmpty()) {
                    return@withContext DocumentParser.Result.Failure(
                        "没有从这份文档里读到文字，可能是纯图片内容"
                    )
                }

                val raw = paragraphs.joinToString("\n")
                val cleaned = TextCleaner.normalizeBlankLines(raw)
                val (text, truncated) = TextCleaner.truncateAtParagraph(
                    cleaned, DocumentParser.MAX_TEXT_CHARS
                )

                DocumentParser.Result.Success(
                    DocumentParser.Document(
                        kind = DocumentParser.Kind.DOCX,
                        text = text,
                        fileName = null,
                        pageCount = null,
                        notes = buildList {
                            if (truncated) add("文档较长，只导入了前 ${DocumentParser.MAX_TEXT_CHARS / 1000} 千字")
                            if (cleaned.contains('\t')) add("表格内容已按行列展开为文字")
                        }
                    )
                )
            } catch (e: IOException) {
                DocumentParser.Result.Failure("读取文件失败：${e.message ?: "I/O 错误"}")
            } catch (e: SecurityException) {
                DocumentParser.Result.Failure("没有读取该文件的权限")
            } catch (e: Exception) {
                DocumentParser.Result.Failure("解析 .docx 失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

    /**
     * 从 zip 里取出 word/document.xml。
     *
     * 必须边读边判断条目名再解析，不能先把整个 zip 解压到磁盘——
     * 有些 docx 里嵌着几十 MB 的图片，解压既慢又占空间。
     */
    private fun readDocumentXml(context: Context, uri: Uri): InputStream? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val stream = DocumentXmlStream(ZipInputStream(input))
        // 找不到 document.xml 时要把 zip 关掉，否则文件描述符泄漏
        if (!stream.hasDocument) {
            stream.close()
            return null
        }
        return stream
    }

    /**
     * 依次解析段落。返回每段的文字（不含换行）。
     */
    private fun parseParagraphs(xml: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(xml, null)

        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inParagraph = false
        var inText = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (depth > MAX_DEPTH) throw IOException("XML 嵌套过深")

                    when {
                        parser.isW("p") -> {
                            inParagraph = true
                            current.setLength(0)
                        }
                        parser.isW("t") -> inText = true
                        parser.isW("br") || parser.isW("cr") -> if (inParagraph) current.append('\n')
                        parser.isW("tab") -> if (inParagraph) current.append('\t')
                    }
                }

                XmlPullParser.TEXT -> if (inText && inParagraph) current.append(parser.text)

                XmlPullParser.END_TAG -> {
                    depth--
                    when {
                        parser.isW("t") -> inText = false
                        parser.isW("p") -> {
                            val line = current.toString()
                            if (line.isNotBlank()) paragraphs += line
                            inParagraph = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }

    /** 带命名空间判断；Word 的正文元素都在 w: 命名空间下。 */
    private fun XmlPullParser.isW(name: String): Boolean =
        this.name == name && (namespace ?: NS_W) == NS_W

    /**
     * 顺序扫描 zip 条目，直到找到 document.xml。
     * 找不到就返回 null（由调用方转成用户可见的错误）。
     */
    private class DocumentXmlStream(private val zip: ZipInputStream) : InputStream() {
        private val found: Boolean = advanceToDocument()

        val hasDocument: Boolean get() = found

        private fun advanceToDocument(): Boolean {
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == ENTRY_DOCUMENT) return true
                entry = zip.nextEntry
            }
            return false
        }

        override fun read(): Int = if (found) zip.read() else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            if (found) zip.read(b, off, len) else -1

        override fun close() {
            runCatching { zip.close() }
        }
    }
}
