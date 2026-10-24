package com.papertong.app.core.model

/**
 * 对照阅读的排布。
 *
 * SideBySide 只在足够宽的屏幕上才有意义——手机上硬分两列，
 * 每列只剩十来个字宽，反而更难读。所以宁可让用户手动选，
 * 也不做"自动切换但说不清规则"的隐式行为。
 */
enum class ReadingLayout(val id: String, val label: String, val hint: String) {
    /** 原文在上、译文在下，适合手机竖屏。 */
    Stacked("stacked", "上下对照", "原文在上、译文在下，适合手机"),

    /** 左右两列，适合平板或横屏。 */
    SideBySide("side_by_side", "左右对照", "左右两列，建议横屏或平板");

    companion object {
        fun fromId(id: String?): ReadingLayout =
            entries.firstOrNull { it.id == id } ?: Stacked
    }
}
