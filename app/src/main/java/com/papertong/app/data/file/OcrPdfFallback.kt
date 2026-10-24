package com.papertong.app.data.file

import android.graphics.Bitmap
import com.papertong.app.data.ocr.OcrEngine

/**
 * 把 OCR 引擎接到 PDF 的扫描件回退路径上。
 *
 * 单独一个类而不是在 PdfTextExtractor 里直接引用 OcrEngine：
 * 解析层不该知道识别层用的是什么引擎，测试或替换也方便。
 */
class OcrPdfFallback(private val engine: OcrEngine) : OcrFallback {

    override suspend fun recognize(bitmap: Bitmap): String? =
        when (val result = engine.recognizeBitmap(bitmap)) {
            is OcrEngine.Result.Success -> result.text.ifBlank { null }
            is OcrEngine.Result.Failure -> null
        }
}
