package com.papertong.app.core.model

import com.papertong.app.core.util.LanguageDetector

/** 语言。displayName 用于界面，promptName 用于提示词。 */
enum class TextLanguage(
    val displayName: String,
    val shortLabel: String,
    val promptName: String
) {
    ENGLISH("英语", "英", "English"),
    CHINESE("中文", "中", "简体中文");

    val other: TextLanguage
        get() = if (this == ENGLISH) CHINESE else ENGLISH
}

/** 一次翻译实际采用的源语言与目标语言。 */
data class DirectionPair(
    val source: TextLanguage,
    val target: TextLanguage
) {
    /** 界面上的短标签，如「英 → 中」。 */
    val label: String get() = "${source.shortLabel} → ${target.shortLabel}"

    /** 完整描述，如「英语 → 中文」。 */
    val description: String get() = "${source.displayName} → ${target.displayName}"
}

/**
 * 翻译方向。自动模式下按文本内容判断，其余为强制指定。
 */
enum class TranslateDirection(val id: String, val label: String) {
    Auto("auto", "自动"),
    EnToZh("en_zh", "英 → 中"),
    ZhToEn("zh_en", "中 → 英");

    fun resolve(text: String): DirectionPair = when (this) {
        Auto -> LanguageDetector.detect(text).let { DirectionPair(it, it.other) }
        EnToZh -> DirectionPair(TextLanguage.ENGLISH, TextLanguage.CHINESE)
        ZhToEn -> DirectionPair(TextLanguage.CHINESE, TextLanguage.ENGLISH)
    }

    companion object {
        fun fromId(id: String?): TranslateDirection =
            entries.firstOrNull { it.id == id } ?: Auto
    }
}
