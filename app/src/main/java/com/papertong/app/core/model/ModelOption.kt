package com.papertong.app.core.model

/**
 * 可选模型。DeepSeek 目前提供两类：
 *  - chat：通用对话，速度快，适合日常翻译
 *  - reasoner：带推理链，长难句更准，但更慢更贵
 */
enum class ModelOption(
    val id: String,
    val label: String,
    val hint: String
) {
    Chat("deepseek-chat", "deepseek-chat", "速度快，日常翻译够用"),
    Reasoner("deepseek-reasoner", "deepseek-reasoner", "带推理，长难句更准，较慢");

    companion object {
        fun fromId(id: String?): ModelOption =
            entries.firstOrNull { it.id == id } ?: Chat
    }
}
