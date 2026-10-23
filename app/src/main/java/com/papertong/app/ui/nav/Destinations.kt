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

    /** OCR 页把识别结果写回上一个页面的 savedStateHandle，用这个 key。 */
    const val KEY_OCR_RESULT = "ocr_result"
}
