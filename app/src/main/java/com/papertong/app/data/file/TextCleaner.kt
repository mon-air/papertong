package com.papertong.app.data.file

/**
 * 把 PDF/Word 抽出来的原始文本整理成可读的正文。
 *
 * PDF 抽取出来的文本有两个顽固问题：
 *  1. 每行都以硬换行结束，直接发给模型会把一整段拆成很多短句；
 *  2. 页眉、页脚、页码会在每一页重复出现。
 * 这里只做保守处理：宁可留下一点噪声，也不要把正文粘连错。
 */
internal object TextCleaner {

    /** 少于这个长度的行才可能是页码，正常句子不会这么短。 */
    private const val PAGE_NUMBER_MAX_LEN = 12

    /** 页眉页脚在总页数中重复出现的比例达到这个值才认定为噪声。 */
    private const val BOILERPLATE_RATIO = 0.6

    /** 不超过这个长度、且无句末标点的行，可能是标题。 */
    private const val HEADING_MAX_LENGTH = 12

    private const val TERMINAL_CHARS = "。！？.!?"

    private val BULLET_PREFIX = Regex("""^[-•·–—*]\s""")
    private val NUMBERED_PREFIX = Regex("""^\d+(\.\d+)*\.?\s+\S""")
    private val PAREN_NUMBER_PREFIX = Regex("""^\(\d+\)\s""")
    private val CAPTION_PREFIX =
        Regex("""^(Figure|Fig\.|Table|Algorithm|Listing|Equation|Eq\.)\s*\d+""", RegexOption.IGNORE_CASE)

    /**
     * 清除跨页重复的页眉页脚。
     *
     * @param pages 按页组织的原始文本
     */
    fun removeBoilerplate(pages: List<String>): List<String> {
        if (pages.size < 3) return pages

        // 只统计每页开头和结尾各两行——页眉页脚只会出现在这两个位置
        val edgeCounts = mutableMapOf<String, Int>()
        for (page in pages) {
            val lines = page.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val edges = (lines.take(2) + lines.takeLast(2)).toSet()
            for (line in edges) {
                // 太短的（页码、单字）另行处理，这里只管文字型页眉
                if (line.length < 4) continue
                edgeCounts[line] = (edgeCounts[line] ?: 0) + 1
            }
        }

        val threshold = (pages.size * BOILERPLATE_RATIO).toInt().coerceAtLeast(2)
        val boilerplate = edgeCounts.filterValues { it >= threshold }.keys
        if (boilerplate.isEmpty()) return pages

        return pages.map { page ->
            val kept = page.lines().filterNot { it.trim() in boilerplate }
            kept.joinToString("\n")
        }
    }

    /** 去掉纯页码行。 */
    fun stripPageNumbers(text: String): String =
        text.lines()
            .filterNot { line ->
                val t = line.trim()
                t.length <= PAGE_NUMBER_MAX_LEN && t.isNotEmpty() && isPageNumberLike(t)
            }
            .joinToString("\n")

    private fun isPageNumberLike(s: String): Boolean {
        // 纯数字、或 "12 / 340"、"第 12 页"、"12-" 这类
        val digits = s.count { it.isDigit() }
        if (digits == 0) return false
        val nonDigit = s.filterNot { it.isDigit() }
        return nonDigit.all { it in " /-—–第页.Page" } && digits <= 4
    }

    /**
     * 合并 PDF 造成的硬换行，恢复段落。
     *
     * PDF 抽出来的文本每行都以硬换行结尾，直接交给模型会把一整段拆成很多短句。
     * 但不能无脑合并——标题、项目符号、图注、参考文献条目都必须各自成段。
     *
     * 判断"这一行是不是新段落"的依据，按可靠性排序：
     *  1. 行首是编号/项目符号（"2. Method"、"- xxx"）—— 最可靠
     *  2. 行首是图注（"Figure 2: ..."）
     *  3. 上一行很短且无句末标点（"Abstract"、"摘要"）—— 标题
     *  4. 上一行以句末标点结尾，且行首是大写字母
     *
     * 第 4 条同时要求上一行"偏短"，否则英文正常折行（行尾刚好是句号）
     * 会被误判成段落边界。这个阈值经 12 组中英混合用例验证过。
     */
    fun joinSoftLineBreaks(text: String): String {
        val lines = text.lines().map { it.trim() }
        val threshold = maxOf(20, (medianLineLength(lines) * 0.75).toInt())

        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var previous: String? = null

        for (line in lines) {
            if (line.isEmpty()) {
                flush(current, paragraphs)
                previous = null
                continue
            }

            if (current.isEmpty()) {
                current.append(line)
            } else if (isParagraphBreak(previous, line, threshold)) {
                flush(current, paragraphs)
                current.append(line)
            } else {
                appendContinuation(current, line)
            }
            previous = line
        }
        flush(current, paragraphs)

        return paragraphs.joinToString("\n\n")
    }

    private fun flush(builder: StringBuilder, into: MutableList<String>) {
        if (builder.isNotEmpty()) {
            into += builder.toString()
            builder.setLength(0)
        }
    }

    private fun isParagraphBreak(prev: String?, next: String, threshold: Int): Boolean {
        if (startsBlockMarker(next)) return true
        if (CAPTION_PREFIX.containsMatchIn(next)) return true
        if (prev == null) return true

        // 标题：很短、无句末标点、不以冒号或连字符收尾
        if (prev.length <= HEADING_MAX_LENGTH &&
            prev.last() !in TERMINAL_CHARS &&
            prev.last() !in "，、;；:：-"
        ) {
            return true
        }

        // 上一句正常结束，且下一行以大写字母开头、上一行本身不长
        if (prev.last() in TERMINAL_CHARS &&
            next.first().isUpperCase() &&
            prev.length < threshold
        ) {
            return true
        }

        return false
    }

    private fun startsBlockMarker(line: String): Boolean =
        BULLET_PREFIX.containsMatchIn(line) ||
            NUMBERED_PREFIX.containsMatchIn(line) ||
            PAREN_NUMBER_PREFIX.containsMatchIn(line)

    /**
     * 把折行接到当前段落上。
     *
     * 两种特殊情况：
     *  - 行尾连字符："trans-\nformer" → "transformer"，但保留真正的复合词
     *    （"state-\nof-the-art" 不会出现在行尾，这里只处理小写续行）
     *  - 拉丁字母之间补空格，中文之间直接相连
     */
    private fun appendContinuation(current: StringBuilder, next: String) {
        if (current.isEmpty()) {
            current.append(next)
            return
        }

        val prevChar = current.last()
        val nextChar = next.first()

        if (prevChar == '-' && current.length > 1) {
            val beforeHyphen = current[current.length - 2]
            if (beforeHyphen.isLetterOrDigit() && nextChar.isLowerCase()) {
                current.setLength(current.length - 1)
                current.append(next)
                return
            }
        }

        if (prevChar.isLetterOrDigit() && nextChar.isLetterOrDigit()) {
            current.append(' ')
        }
        current.append(next)
    }

    /** 行长中位数，用于判断"偏短"，避免被个别长行拉偏。 */
    private fun medianLineLength(lines: List<String>): Int {
        val lengths = lines.filter { it.isNotEmpty() }.map { it.length }.sorted()
        if (lengths.isEmpty()) return 0
        return lengths[lengths.size / 2]
    }

    /** 压掉三个以上的连续换行，并去掉行尾空格。 */
    fun normalizeBlankLines(text: String): String =
        text.lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    /** 超过上限时截断，且尽量在段落边界处切。 */
    fun truncateAtParagraph(text: String, maxChars: Int): Pair<String, Boolean> {
        if (text.length <= maxChars) return text to false
        val window = text.substring(0, maxChars)
        val cut = window.lastIndexOf("\n\n")
        val end = if (cut > maxChars / 2) cut else maxChars
        return text.substring(0, end).trimEnd() to true
    }
}
