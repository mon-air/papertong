package com.papertong.app.ui.screens.ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 拍照文件的临时管理。
 *
 * 照片写到 cacheDir/ocr/，通过 FileProvider 授权给相机应用；
 * 识别完成后立即删除，不在设备上留下拍摄内容。
 */
object CameraCapture {

    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /** 删除该 uri 对应的缓存文件；失败时忽略，cacheDir 会被系统回收。 */
    fun cleanup(context: Context, uri: Uri) {
        runCatching {
            val authority = "${context.packageName}.fileprovider"
            if (uri.authority == authority && uri.path != null) {
                val name = uri.lastPathSegment ?: return
                File(File(context.cacheDir, "ocr"), name).delete()
            }
        }
    }
}
