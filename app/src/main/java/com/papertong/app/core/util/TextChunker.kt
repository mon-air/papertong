package com.papertong.app.core.util

/**
 * 按段落边界切分长文本。
 *
 * 为什么不在句子中间切：论文里一个句子常常横跨多个公式，
 * 从句中切断会让模型失去语法主语，译文前言不搭后语。
 *
 * 策略（从优到劣）：
 *  1. 在空行（段落边界）处切
 *  2. 退化为在句末标点后切
 *  3. 再退化为在空格后切
 *  4. 都没有时硬切
 */
object TextChunker {

    /** 单块目标字符数。DeepSeek 上下文足够大，但单块过长会拖慢首字延迟。 */
    const val DEFAULT_CHUNK_SIZE = 2500

    /** 块之间重叠的字符数，用于让模型接续上下文时不丢失指代关系。 */
    const val DEFAULT_OVERLAP = 120

    data class Chunk(
        val index: Int,
        val text: String,
        /** 上一块的末尾若干字符，供 Prompt 做上下文衔接。 */
        val previousTail: String?
    )

    fun split(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_OVERLAP
    ): List<Chunk> {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= chunkSize) {
            return listOf(Chunk(index = 1, text = normalized, previousTail = null))
        }

        val pieces = mutableListOf<String>()
        var cursor = 0

        while (cursor < normalized.length) {
            val remaining = normalized.length - cursor
            if (remaining <= chunkSize) {
                pieces += normalized.substring(cursor)
                break
            }

            val window = normalized.substring(cursor, cursor + chunkSize)
            val cut = findCutPoint(window)
            pieces += normalized.substring(cursor, cursor + cut)
            cursor += cut
        }

        // 生成衔接用的上一块末尾，但不要和当前块开头重复太多
        return pieces.mapIndexed { i, piece ->
            Chunk(
                index = i + 1,
                text = piece.trim(),
                previousTail = if (i == 0) null else pieces[i - 1].takeLast(overlap).trim()
            )
        }
    }

    /**
     * 在窗口内找一个合适的切点。返回的是相对偏移。
     * 优先段落边界，其次句末，最后空格。
     */
    private fun findCutPoint(window: String): Int {
        val minUseful = window.length / 2  // 避免切出过短的块

        // 1. 段落边界：连续两个换行
        val paragraph = window.lastIndexOf("\n\n")
        if (paragraph > minUseful) return paragraph + 2

        // 2. 句末标点（中英文）
        for (mark in listOf("。", "！", "？", ". ", "! ", "? ")) {
            val idx = window.lastIndexOf(mark)
            if (idx > minUseful) return idx + mark.length
        }

        // 3. 换行
        val newline = window.lastIndexOf('\n')
        if (newline > minUseful) return newline + 1

        // 4. 空格
        val space = window.lastIndexOf(' ')
        if (space > minUseful) return space + 1

        // 5. 硬切
        return window.length
    }
}
