package com.papertong.app.data.file

import android.graphics.Bitmap

/**
 * 扫描版 PDF 的回退通道：把渲染出的页面交给本机 OCR。
 *
 * 独立成一个公开接口而不是挂在 PdfTextExtractor 内部，
 * 是因为 DocumentParser.parse 属于对外契约，参数类型不能是 internal。
 */
fun interface OcrFallback {
    /** 返回识别出的文本；失败时返回 null。 */
    suspend fun recognize(bitmap: Bitmap): String?
}
