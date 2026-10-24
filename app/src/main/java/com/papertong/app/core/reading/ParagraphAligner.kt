package com.papertong.app.core.reading

/**
 * 原文与译文的段落对齐。
 *
 * 为什么按段落而不是按句子：翻译是按块（2500 字）流式生成的，
 * 句级对应关系从未被保留过，硬凑只会给出错误的对应。
 * 段落边界则是可靠的——Prompt 明确要求保留段落结构。
 *
 * 段落数不一致时不猜：按顺序配对，多出来的段落单独显示，
 * 并把这个事实告诉用户。宁可承认对不齐，也不要给错位对照。
 */
object ParagraphAligner {

    data class Pair(
        /** 从 1 开始的段号。 */
        val index: Int,
        /** 原文段落；译文多出的段落这里为 null。 */
        val source: String?,
        /** 译文段落；原文多出的段落这里为 null。 */
        val target: String?
    )

    data class Result(
        val pairs: List<Pair>,
        /** 两侧段落数是否一致。不一致时界面要提示。 */
        val aligned: Boolean,
        val sourceCount: Int,
        val targetCount: Int
    ) {
        val isEmpty: Boolean get() = pairs.isEmpty()
    }

    /** 按空行切段。翻译时块之间插入的 \n\n 也会被当作段落边界——这是刻意的。 */
    fun splitParagraphs(text: String): List<String> =
        text.replace("\r\n", "\n")
            .split(PARAGRAPH_BREAK)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun align(source: String, target: String): Result {
        val sourceParagraphs = splitParagraphs(source)
        val targetParagraphs = splitParagraphs(target)

        val count = maxOf(sourceParagraphs.size, targetParagraphs.size)
        val pairs = (0 until count).map { i ->
            Pair(
                index = i + 1,
                source = sourceParagraphs.getOrNull(i),
                target = targetParagraphs.getOrNull(i)
            )
        }

        return Result(
            pairs = pairs,
            aligned = sourceParagraphs.size == targetParagraphs.size,
            sourceCount = sourceParagraphs.size,
            targetCount = targetParagraphs.size
        )
    }

    private val PARAGRAPH_BREAK = Regex("\n\\s*\n")
}
