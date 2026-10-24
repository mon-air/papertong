package com.papertong.app.ui.nav

/**
 * 路由集中声明，避免字符串散落在各处。
 */
object Routes {
    const val TRANSLATE = "translate"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val OCR = "ocr"
    const val FILE_IMPORT = "file_import"

    /**
     * OCR 页 / 文件导入页把结果写回上一个页面的 savedStateHandle，用这个 key。
     * 两者语义相同（都是"待翻译的原文"），共用一个 key。
     */
    const val KEY_IMPORTED_TEXT = "imported_text"
}
