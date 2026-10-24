package com.papertong.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.BufferedInputStream
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
            val bitmap = when (val decoded = decodeSafely(context, uri, OCR_MAX_DIMENSION)) {
                is Decoded.Ok -> decoded.bitmap
                is Decoded.Err -> {
                    Log.w(TAG, "decode failed for $uri: ${decoded.message}")
                    return@withContext Result.Failure(decoded.message)
                }
            }
            recognizeBitmap(bitmap)
        }

    /**
     * 直接识别一张已解码的位图。
     * 扫描版 PDF 的回退路径会自己渲染页面，走这个入口。
     *
     * 调用方负责位图的生命周期：本函数不会 recycle 它。
     */
    suspend fun recognizeBitmap(bitmap: Bitmap): Result =
        withContext(Dispatchers.IO) {
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
                Log.e(TAG, "ML Kit process failed", e)
                Result.Failure("识别失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

    /**
     * 生成用于界面展示的小图。
     *
     * 直接拿原图丢给 Image 会重新按全尺寸解码，手机照片 4000px 宽约 50MB，
     * 这里统一降采样到预览尺寸。
     */
    /** 由调用方提供位图的识别入口（扫描件回退用），不接管位图生命周期。 */
    suspend fun preview(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            when (val decoded = decodeSafely(context, uri, PREVIEW_MAX_DIMENSION)) {
                is Decoded.Ok -> decoded.bitmap
                is Decoded.Err -> {
                    Log.w(TAG, "preview decode failed for $uri: ${decoded.message}")
                    null
                }
            }
        }

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

    /** 解码结果。区分失败原因，才能给用户看得懂的提示而不是一句"读不出来"。 */
    private sealed interface Decoded {
        data class Ok(val bitmap: Bitmap) : Decoded
        data class Err(val message: String) : Decoded
    }

    private fun decodeSafely(context: Context, uri: Uri, maxDimension: Int): Decoded =
        try {
            val bitmap = decodeSampled(context, uri, maxDimension)
            if (bitmap != null) {
                Decoded.Ok(bitmap)
            } else {
                Decoded.Err("无法解码这张图片，可能是格式不受支持")
            }
        } catch (e: OutOfMemoryError) {
            Decoded.Err("图片过大，请换一张")
        } catch (e: SecurityException) {
            // Photo Picker 给的 uri 权限是临时的，进程重建后可能已失效
            Decoded.Err("没有读取该图片的权限，请重新选择")
        } catch (e: Exception) {
            Log.w(TAG, "decode threw for $uri", e)
            Decoded.Err("无法读取这张图片：${e.message ?: e.javaClass.simpleName}")
        }

    /**
     * 按目标长边降采样解码。
     *
     * 先用 inJustDecodeBounds 只读尺寸，再按 2 的幂设置 inSampleSize，
     * 避免把原图整幅读进内存。
     */
    private fun decodeSampled(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        // 注意：inJustDecodeBounds 模式下 decodeStream 按契约返回 null，
        // 这里要判断的是"流能否打开"，不能拿 decodeStream 的返回值当判据，
        // 否则永远走 null 分支。
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        // outWidth <= 0 表示字节流不是可解码的图片（或已损坏）
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val fullStream = context.contentResolver.openInputStream(uri) ?: return null
        val decoded = fullStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: return null
        return applyExifRotation(context, uri, decoded)
    }

    /**
     * 应用 EXIF 旋转。
     *
     * BitmapFactory 只解像素、不认 EXIF，相机竖拍的照片实际是以横向存储的，
     * 不旋转的话送进识别器就是横着的版面，行结构会被打乱。
     */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // ExifInterface 需要能 mark/reset 的流，ContentResolver 给的不保证支持
                val orientation = ExifInterface(BufferedInputStream(stream)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            // EXIF 读不出来不是致命问题，按不旋转处理
            0
        }

        if (degrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        // 旋转产生了新位图，旧位图此时还没有交给识别器，可以安全回收
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
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
        const val TAG = "OcrEngine"

        /** 送给识别器的长边上限：再大对精度没有帮助，只会更慢。 */
        const val OCR_MAX_DIMENSION = 2000

        /** 界面预览图的长边上限。 */
        const val PREVIEW_MAX_DIMENSION = 720
    }
}
