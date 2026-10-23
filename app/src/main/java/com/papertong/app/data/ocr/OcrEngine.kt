package com.papertong.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 本机文字识别。
 *
 * 用中文模型而非拉丁模型：它同时支持拉丁字母，能一次覆盖中英混排，
 * 代价是纯英文精度略低于专门的 Latin 识别器。考虑到论文里常混有中文批注，
 * 单模型换取一条代码路径是值得的。
 *
 * 全程离线：bundled 模型的权重随 APK 分发，不走 Play Services 下载，
 * 图片不会离开设备。
 */
class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    sealed interface Result {
        data class Success(
            /** 按段落拼回的纯文本，保留原始换行结构。 */
            val text: String,
            val lineCount: Int
        ) : Result

        data class Failure(val message: String) : Result
    }

    suspend fun recognize(context: Context, uri: Uri): Result =
        withContext(Dispatchers.IO) {
            val bitmap = decodeSafely(context, uri, OCR_MAX_DIMENSION)
                ?: return@withContext Result.Failure("无法读取这张图片，请换一张")

            // 刻意不调用 bitmap.recycle()：
            // 取消协程时 ML Kit 的内部线程可能仍在读这张位图，
            // 主动回收会直接触发 "trying to use a recycled bitmap" 崩溃。
            // 位图长边已限制在 2000px 以内（约 16MB 上限），交给 GC 即可。
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                // 必须显式给出 <Result>：否则 lambda 的类型参数会被推导成 Unit，
                // 整个 try 表达式就变成 Unit 与 Result 的公共父类型 Any。
                suspendCancellableCoroutine<Result> { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            continuation.resume(
                                Result.Success(
                                    text = assemble(visionText),
                                    lineCount = visionText.textBlocks.sumOf { it.lines.size }
                                )
                            )
                        }
                        .addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Failure("识别失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

    /**
     * 生成用于界面展示的小图。
     *
     * 直接拿原图丢给 Image 会重新按全尺寸解码，手机照片 4000px 宽约 50MB，
     * 这里统一降采样到预览尺寸。
     */
    suspend fun preview(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) { decodeSafely(context, uri, PREVIEW_MAX_DIMENSION) }

    /**
     * 把识别结果还原成带段落结构的文本。
     *
     * ML Kit 给的是 block → line 两层结构。block 通常对应视觉上的段落，
     * 所以 block 之间插空行、行内直接换行拼接。
     */
    private fun assemble(text: Text): String {
        val blocks = text.textBlocks
            .map { block -> block.lines.joinToString("\n") { it.text } }
            .filter { it.isNotBlank() }
        return blocks.joinToString("\n\n")
    }

    /** 解码失败一律当作"读不出来"，不让异常冒到协程外。 */
    private fun decodeSafely(context: Context, uri: Uri, maxDimension: Int): Bitmap? =
        try {
            decodeSampled(context, uri, maxDimension)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }

    /**
     * 按目标长边降采样解码。
     *
     * 先用 inJustDecodeBounds 只读尺寸，再按 2 的幂设置 inSampleSize，
     * 避免把原图整幅读进内存。
     */
    private fun decodeSampled(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        /** 送给识别器的长边上限：再大对精度没有帮助，只会更慢。 */
        const val OCR_MAX_DIMENSION = 2000

        /** 界面预览图的长边上限。 */
        const val PREVIEW_MAX_DIMENSION = 720
    }
}
