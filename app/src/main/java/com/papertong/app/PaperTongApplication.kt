package com.papertong.app

import android.app.Application
import com.papertong.app.di.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PaperTongApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // PdfBox-Android 必须先初始化资源加载器，否则抽取 PDF 时会找不到字体与资源文件。
        // init() 只是登记 Context，本身不做 I/O，放在主线程没有代价。
        PDFBoxResourceLoader.init(this)
    }
}
