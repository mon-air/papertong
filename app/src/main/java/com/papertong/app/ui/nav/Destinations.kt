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

    /** 对照阅读页；参数是一条历史记录的 id。 */
    const val READING = "reading"
    const val ARG_ENTRY_ID = "entryId"

    /** 带记录的完整路由。id 是 UUID，不含需要转义的字符，直接拼接即可。 */
    fun reading(entryId: String): String = "$READING/$entryId"

    /** 在 NavHost 里注册用的模式串。 */
    const val READING_PATTERN = "$READING/{$ARG_ENTRY_ID}"

    /**
     * OCR 页 / 文件导入页把结果写回上一个页面的 savedStateHandle，用这个 key。
     * 两者语义相同（都是"待翻译的原文"），共用一个 key。
     */
    const val KEY_IMPORTED_TEXT = "imported_text"
}
