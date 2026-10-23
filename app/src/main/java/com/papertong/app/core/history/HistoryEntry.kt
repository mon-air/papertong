package com.papertong.app.core.history

import kotlinx.serialization.Serializable

/**
 * 一条翻译记录。
 *
 * 只存展示所需的字段：风格与模型存 id（标签将来可能改），
 * 方向存已解析好的标签（这是记录本身的事实，不会再变）。
 */
@Serializable
data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val sourceText: String,
    val translatedText: String,
    val styleId: String,
    val modelId: String,
    /** 如「英 → 中」。 */
    val directionLabel: String
)
