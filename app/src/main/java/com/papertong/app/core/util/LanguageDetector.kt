package com.papertong.app.core.util

import com.papertong.app.core.model.TextLanguage

/**
 * 极轻量的语言判断，只区分「中文 / 英语」。
 *
 * 做法是统计 CJK 字符与拉丁字母的比例，不是真正的语言识别模型。
 * 对这个 App 够用：输入要么是英文论文，要么是中文文稿。
 *
 * 已知局限：
 *  - 日文汉字会被判成中文（日文不在支持范围内）
 *  - 纯公式、纯数字、纯符号的文本无法判断，按英语处理
 *  - 中英混排时以占比高的一方为准
 */
object LanguageDetector {

    /** 只取开头这么多字符做判断：判断精度足够，且避免长文本每次输入都全量扫描。 */
    private const val SAMPLE_LIMIT = 2000

    /** CJK 占比超过该阈值即认定为中文。 */
    private const val CJK_THRESHOLD = 0.3

    fun detect(text: String): TextLanguage {
        val sample = if (text.length > SAMPLE_LIMIT) text.substring(0, SAMPLE_LIMIT) else text

        var cjk = 0
        var latin = 0
        for (ch in sample) {
            when {
                ch in '一'..'鿿' -> cjk++
                ch in '㐀'..'䶿' -> cjk++
                ch.isLetter() && ch.code < 128 -> latin++
            }
        }

        if (cjk == 0 && latin == 0) return TextLanguage.ENGLISH
        return if (cjk.toDouble() / (cjk + latin) >= CJK_THRESHOLD) {
            TextLanguage.CHINESE
        } else {
            TextLanguage.ENGLISH
        }
    }
}
