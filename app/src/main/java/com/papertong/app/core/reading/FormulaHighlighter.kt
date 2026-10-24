package com.papertong.app.core.reading

/**
 * 找出文本中的 LaTeX 公式区间，供界面用等宽字体高亮。
 *
 * 只做定位，不做渲染——渲染 LaTeX 需要引入 KaTeX/MathJax 级别的依赖，
 * 与"极简、离线、体积可控"的目标冲突。MVP 里让公式以等宽字体呈现，
 * 至少能保证下划线、上下标标记不被当成普通文字而读错。
 *
 * 支持的定界符（按优先级）：
 *  - `$$...$$`  行间公式
 *  - `\[...\]`  行间公式
 *  - `$...$`    行内公式
 *  - `\(...\)`  行内公式
 *
 * 识别规则刻意保守：
 *  - 定界符必须成对，且内容非空、不含换行（行内公式）
 *  - `$` 后面紧跟数字、且闭合的 `$` 前有空格时，多半是金额，不当作公式
 */
object FormulaHighlighter {

    data class Span(
        /** 在原文中的起止下标，左闭右开。 */
        val start: Int,
        val end: Int,
        /** 是否为行间公式（影响是否独占一行）。 */
        val display: Boolean
    )

    /**
     * 返回按起点排序、互不重叠的公式区间。
     * 匹配从上一种定界符消耗掉的区域不会重复匹配。
     */
    fun findSpans(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var cursor = 0

        while (cursor < text.length) {
            val match = nextMatch(text, cursor) ?: break
            spans += match
            cursor = match.end
        }

        return spans
    }

    /** 从 from 开始找下一个公式；找不到返回 null。 */
    private fun nextMatch(text: String, from: Int): Span? {
        var best: Span? = null

        for (delimiter in DELIMITERS) {
            val span = findDelimiter(text, from, delimiter) ?: continue
            if (best == null || span.start < best.start) {
                best = span
            }
        }

        return best
    }

    private data class Delimiter(
        val open: String,
        val close: String,
        val display: Boolean
    )

    private fun findDelimiter(text: String, from: Int, delimiter: Delimiter): Span? {
        var openIndex = text.indexOf(delimiter.open, from)
        while (openIndex >= 0) {
            val contentStart = openIndex + delimiter.open.length
            val closeIndex = text.indexOf(delimiter.close, contentStart)

            if (closeIndex < 0) return null

            val content = text.substring(contentStart, closeIndex)

            if (isPlausible(content, delimiter)) {
                return Span(
                    start = openIndex,
                    end = closeIndex + delimiter.close.length,
                    display = delimiter.display
                )
            }

            // 这一对不像公式，从下一个开定界符继续找
            openIndex = text.indexOf(delimiter.open, openIndex + delimiter.open.length)
        }
        return null
    }

    private fun isPlausible(content: String, delimiter: Delimiter): Boolean {
        if (content.isEmpty()) return false

        // 行内公式不允许跨行；跨行多半是误匹配（比如两个独立的 $ 符号）
        if (!delimiter.display && content.contains('\n')) return false

        // 金额启发式：$12.50 这种，内容以数字开头且很短、不含 LaTeX 特征
        if (!delimiter.display &&
            content.first().isDigit() &&
            content.length <= 8 &&
            content.none { it in LATEX_HINTS }
        ) {
            return false
        }

        return true
    }

    /** 出现这些字符基本可以确定是 LaTeX，而不是自然语言。 */
    private const val LATEX_HINTS = "\\^_{}[]=+*/<>"

    private val DELIMITERS = listOf(
        Delimiter("$$", "$$", display = true),
        Delimiter("\\[", "\\]", display = true),
        Delimiter("\\(", "\\)", display = false),
        Delimiter("$", "$", display = false)
    )
}
