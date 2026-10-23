package com.papertong.app.core.model

/**
 * 翻译风格。id 用于持久化到 DataStore，label 用于界面展示。
 * 具体的 Prompt 文案在 M3 的 PromptPresets 中按 id 映射，
 * 这样以后改 Prompt 不需要动存储层。
 */
enum class TranslateStyle(val id: String, val label: String, val hint: String) {
    Academic("academic", "学术专业", "术语统一、句式严谨，保留原文结构"),
    Idiomatic("idiomatic", "地道口语", "母语者表达，意译优先"),
    Literal("literal", "直译", "逐句对应，尽量不增删");

    companion object {
        fun fromId(id: String?): TranslateStyle =
            entries.firstOrNull { it.id == id } ?: Academic
    }
}
