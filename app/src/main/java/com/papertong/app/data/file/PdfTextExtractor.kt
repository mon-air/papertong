package com.papertong.app.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF 正文抽取。
 *
 * 两级策略：
 *  1. 用 PdfBox 直接抽文字层（绝大多数电子版论文都有）；
 *  2. 若整份文档几乎抽不出字，说明是扫描件，改用系统自带 PdfRenderer
 *     把前若干页渲染成位图，再交给本地 OCR。
 *
 * 全部在本机完成，PDF 字节不上传。
 */
internal object PdfTextExtractor {

    private const val TAG = "PdfTextExtractor"

    /** 扫描件最多识别多少页，避免用户干等十分钟。 */
    private const val MAX_OCR_PAGES = 10

    /** 送进 OCR 的页面渲染宽度（像素）。再高对识别没有帮助。 */
    private const val OCR_RENDER_WIDTH = 1600

    /** 每页抽出的字符数低于这个值，认为该页没有文字层（扫描页）。 */
    private const val EMPTY_PAGE_THRESHOLD = 30

    suspend fun extract(
        context: Context,
        uri: Uri,
        ocr: OcrFallback?
    ): DocumentParser.Result = withContext(Dispatchers.IO) {
        val pages = try {
            extractTextLayer(context, uri)
        } catch (e: IOException) {
            return@withContext DocumentParser.Result.Failure(
                "无法打开这个 PDF：${e.message ?: "文件可能已损坏"}"
            )
        } catch (e: SecurityException) {
            return@withContext DocumentParser.Result.Failure("没有读取该文件的权限")
        } catch (e: OutOfMemoryError) {
            return@withContext DocumentParser.Result.Failure("PDF 过大，内存不足，请拆分后再导入")
        } catch (e: Exception) {
            Log.w(TAG, "text layer extraction failed", e)
            // PdfBox 对某些非标准 PDF 会抛非 IO 异常；这时仍可尝试渲染路径
            emptyList()
        }

        val meaningfulPages = pages.count { it.trim().length >= EMPTY_PAGE_THRESHOLD }

        // 文字层可用：走正常路径
        if (hasTextLayer(meaningfulPages, pages.size)) {
            val cleaned = clean(pages)
            val (text, truncated) = TextCleaner.truncateAtParagraph(
                cleaned, DocumentParser.MAX_TEXT_CHARS
            )
            return@withContext DocumentParser.Result.Success(
                DocumentParser.Document(
                    kind = DocumentParser.Kind.PDF,
                    text = text,
                    fileName = null,
                    pageCount = pages.size,
                    notes = buildList {
                        if (truncated) {
                            add("文档较长，只导入了前 ${DocumentParser.MAX_TEXT_CHARS / 1000} 千字")
                        }
                    }
                )
            )
        }

        // 没有文字层 → 扫描件
        if (ocr == null) {
            return@withContext DocumentParser.Result.Failure(
                "这个 PDF 没有文字层（可能是扫描件），暂时无法提取文字"
            )
        }

        ocrScannedPages(context, uri, ocr)
    }

    private fun hasTextLayer(meaningfulPages: Int, totalPages: Int): Boolean {
        if (totalPages == 0) return false
        // 至少一半的页有文字，才算有文字层
        return meaningfulPages * 2 >= totalPages && meaningfulPages > 0
    }

    /**
     * 逐页抽取文字层。
     *
     * 用 setStartPage/setEndPage 分页抽取而不是一次拿全文，
     * 是为了能按页统计"这页有没有字"，扫描件判断需要这个粒度。
     */
    private fun extractTextLayer(context: Context, uri: Uri): List<String> {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开输入流")

        return input.use { stream ->
            PDDocument.load(stream).use { document ->
                val total = document.numberOfPages
                if (total == 0) return@use emptyList<String>()

                val stripper = PDFTextStripper().apply {
                    // 双栏论文按视觉顺序读，否则会左右栏交错
                    setSortByPosition(true)
                    setLineSeparator("\n")
                }

                (1..total).map { pageNo ->
                    stripper.setStartPage(pageNo)
                    stripper.setEndPage(pageNo)
                    try {
                        stripper.getText(document)
                    } catch (e: Exception) {
                        // 单页失败不该让整份文档失败
                        Log.w(TAG, "page $pageNo failed", e)
                        ""
                    }
                }
            }
        }
    }

    /** 清理页眉页脚、页码与硬换行。 */
    private fun clean(pages: List<String>): String {
        val withoutBoilerplate = TextCleaner.removeBoilerplate(pages)
        val joined = withoutBoilerplate.joinToString("\n\n")
        val noPageNumbers = TextCleaner.stripPageNumbers(joined)
        val rejoined = TextCleaner.joinSoftLineBreaks(noPageNumbers)
        return TextCleaner.normalizeBlankLines(rejoined)
    }

    /**
     * 扫描件：用 PdfRenderer 渲染页面再 OCR。
     *
     * PdfRenderer 是系统组件（API 21+），比 PdfBox 渲染快且不吃内存，
     * 但它只能从文件描述符打开，所以先用 SAF 拿 ParcelFileDescriptor。
     */
    private suspend fun ocrScannedPages(
        context: Context,
        uri: Uri,
        ocr: OcrFallback
    ): DocumentParser.Result = withContext(Dispatchers.IO) {
        val pfd: ParcelFileDescriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext DocumentParser.Result.Failure("无法打开 PDF")
        } catch (e: Exception) {
            return@withContext DocumentParser.Result.Failure("无法打开 PDF：${e.message}")
        }

        val pageTexts = mutableListOf<String>()
        var rendered = 0

        try {
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                val limit = minOf(total, MAX_OCR_PAGES)

                for (index in 0 until limit) {
                    val pageText = try {
                        renderer.openPage(index).use { page ->
                            val bitmap = renderPage(page)
                            val result = ocr.recognize(bitmap)
                            // 渲染出来的位图只有这里在用，识别完成即可回收
                            if (!bitmap.isRecycled) bitmap.recycle()
                            result
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "render page $index failed", e)
                        null
                    }

                    if (!pageText.isNullOrBlank()) pageTexts += pageText
                    rendered++
                }

                val truncatedPages = total > limit
                if (pageTexts.isEmpty()) {
                    return@withContext DocumentParser.Result.Failure(
                        "没能从这个 PDF 里识别出文字，请换一份或调高扫描质量"
                    )
                }

                val cleaned = TextCleaner.normalizeBlankLines(pageTexts.joinToString("\n\n"))
                val (text, truncatedChars) = TextCleaner.truncateAtParagraph(
                    cleaned, DocumentParser.MAX_TEXT_CHARS
                )

                DocumentParser.Result.Success(
                    DocumentParser.Document(
                        kind = DocumentParser.Kind.PDF,
                        text = text,
                        fileName = null,
                        pageCount = total,
                        notes = buildList {
                            add("这份 PDF 没有文字层，已用本机 OCR 识别 $rendered 页")
                            if (truncatedPages) {
                                add("仅处理了前 $MAX_OCR_PAGES 页，共 $total 页")
                            }
                            if (truncatedChars) {
                                add("识别文本较长，只导入了前 ${DocumentParser.MAX_TEXT_CHARS / 1000} 千字")
                            }
                            add("OCR 结果可能有识别误差，建议翻译前检查关键公式与符号")
                        }
                    )
                )
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    /** 按固定宽度渲染页面，保持长宽比。 */
    private fun renderPage(page: PdfRenderer.Page): Bitmap {
        val width = OCR_RENDER_WIDTH
        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // 白底：PdfRenderer 默认透明背景，透明区域送进 OCR 会变成黑色
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}
