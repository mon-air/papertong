package com.papertong.app.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 本地文件解析的对外契约。
 *
 * 三种格式共用一套结果类型，界面层不需要知道背后是 PdfBox、OOXML 还是纯文本。
 * 解析全程在本机完成，文件字节不会离开设备。
 */
object DocumentParser {

    /** 单次导入允许的最大文件，超过直接拒绝，避免把内存耗光。 */
    const val MAX_FILE_BYTES = 40L * 1024 * 1024

    /** 解析后允许的最大字符数（约 200 页论文），超出截断。 */
    const val MAX_TEXT_CHARS = 400_000

    enum class Kind(val label: String, val extensions: List<String>) {
        PDF("PDF", listOf("pdf")),
        DOCX("Word", listOf("docx")),
        MARKDOWN("Markdown", listOf("md", "markdown")),
        PLAIN("文本", listOf("txt")),
        UNKNOWN("未知", emptyList());

        companion object {
            fun fromFileName(name: String?): Kind {
                val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
                return entries.firstOrNull { ext in it.extensions } ?: UNKNOWN
            }

            /** SAF 里可以按 MIME 兜底判断：部分提供方不返回文件名。 */
            fun fromMimeType(mime: String?): Kind = when {
                mime == "application/pdf" -> PDF
                mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                mime == "text/markdown" -> MARKDOWN
                mime?.startsWith("text/") == true -> PLAIN
                else -> UNKNOWN
            }
        }
    }

    data class Document(
        val kind: Kind,
        /** 解析出的正文，已做基本清洗（去掉多余空行、页眉页脚噪声）。 */
        val text: String,
        val fileName: String?,
        val pageCount: Int?,
        /** 解析过程中值得告知用户的情况，例如"扫描件已改用 OCR"。 */
        val notes: List<String> = emptyList()
    )

    sealed interface Result {
        data class Success(val document: Document) : Result
        data class Failure(val message: String) : Result
    }

    /** .doc（旧格式）不在支持范围，给出明确提示而不是含糊的"解析失败"。 */
    private const val MSG_LEGACY_DOC =
        "不支持旧版 .doc 格式。请在 Word 中另存为 .docx 后再导入。"

    /**
     * 解析入口。
     *
     * @param ocr 扫描版 PDF 的回退通道；不需要时不传，届时会提示用户。
     */
    suspend fun parse(
        context: Context,
        uri: Uri,
        ocr: PdfTextExtractor.OcrFallback? = null
    ): Result {
        val resolved = resolveMeta(context, uri)
        val kind = resolved.kind

        // 体积在解析前拦掉：PdfBox 会按文件大小预留内存，超大文件在低端机上直接 OOM
        if (resolved.sizeBytes != null && resolved.sizeBytes > MAX_FILE_BYTES) {
            return Result.Failure(
                "文件过大（${formatSize(resolved.sizeBytes)}），请拆分后再导入"
            )
        }

        // 各 extractor 不知道文件名（它们只看内容），统一在这里补上
        val parsed = when (kind) {
            Kind.PDF -> PdfTextExtractor.extract(context, uri, ocr)
            Kind.DOCX -> DocxTextExtractor.extract(context, uri)
            Kind.MARKDOWN, Kind.PLAIN -> PlainTextExtractor.extract(context, uri, kind)
            Kind.UNKNOWN -> {
                val ext = resolved.displayName?.substringAfterLast('.', "")?.lowercase()
                Result.Failure(
                    if (ext == "doc") MSG_LEGACY_DOC
                    else "不支持这种文件类型${ext?.let { "（.$it）" } ?: ""}，目前支持 PDF、.docx、.md、.txt"
                )
            }
        }

        // 解析成功时补上文件名，界面要显示给用户确认"导的是哪一份"
        return when (parsed) {
            is Result.Success -> Result.Success(
                parsed.document.copy(fileName = parsed.document.fileName ?: resolved.displayName)
            )

            is Result.Failure -> parsed
        }
    }

    private data class Meta(val displayName: String?, val sizeBytes: Long?, val kind: Kind)

    /**
     * 读文件名与大小。优先用 SAF 的 DISPLAY_NAME，
     * 拿不到时退回 uri 路径的最后一段。
     */
    private fun resolveMeta(context: Context, uri: Uri): Meta {
        var name: String? = null
        var size: Long? = null
        val mime: String? = context.contentResolver.getType(uri)

        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }

        if (name == null) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }

        var kind = Kind.fromFileName(name)
        if (kind == Kind.UNKNOWN && mime != null) {
            kind = Kind.fromMimeType(mime)
        }
        return Meta(name, size, kind)
    }

    /** 供界面在解析前做体积拦截。 */
    fun describeLimit(): String = "单个文件不超过 40 MB"

    internal fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
