package com.papertong.app.data.translate

import com.papertong.app.core.model.TextLanguage
import com.papertong.app.core.model.TranslateStyle

/**
 * 提示词构建。
 *
 * 设计原则：
 *  1. 明确列出"不要翻译什么"（公式、引用、代码），比只写"要翻译什么"有效
 *  2. 禁止添加解释、前言、Markdown 围栏 —— 否则模型爱写「以下是译文：」
 *  3. 风格规则按目标语言分别书写：中文的"学术书面语"和英文的
 *     "academic register" 不是一回事，混用会带出翻译腔
 *  4. 术语一致性靠"同段内保持统一"约束；跨块全局术语表需要二次往返，MVP 不做
 */
object PromptPresets {

    private const val COMMON_RULES = """
你是一名专业的翻译引擎。严格遵守以下规则：
1. 只输出译文本身，不要输出任何解释、说明、译注或"以下是译文"之类的前缀。
2. 不要使用 Markdown 代码围栏包裹译文。
3. 原文中的数学公式（$...$、$$...$$、\(...\)、\[...\]）、变量名、函数名、
   引用编号（如 [12]、(Smith et al., 2020)）、URL、代码标识符必须原样保留，
   不要翻译、不要改写、不要添加空格。
4. 保持原文的段落划分，段落之间用空行分隔。
5. 如果原文已经是目标语言，原样输出，不要改写。
6. 遇到无法识别的 OCR 乱码时，按最合理的学术语境推测，不要输出"无法识别"。
"""

    /**
     * 风格规则 × 目标语言。
     *
     * 英文版本不是中文版本的直译，而是按英文写作惯例重写的：
     * 例如英文拒绝用 "we" 之外的被动堆砌、避免 "in order to" 这类冗余表达。
     */
    private fun styleRules(style: TranslateStyle, target: TextLanguage): String =
        when (target) {
            TextLanguage.CHINESE -> chineseRules(style)
            TextLanguage.ENGLISH -> englishRules(style)
        }

    private fun chineseRules(style: TranslateStyle): String = when (style) {
        TranslateStyle.Academic -> """
翻译风格：中文学术论文。
- 使用规范的中文学术书面语，避免口语化表达。
- 术语采用该领域通用译法；首次出现时可保留英文原词并加括号标注，
  但同一术语全文只标注一次。
- 忠实还原原文逻辑关系（因果、转折、让步），不要为通顺而改变论述强度。
- 被动语态按中文学术惯例处理，不强行逐字对应。
- 长句可适当拆分，但不得合并或删除信息。
- 保持客观、克制的语气，不添加评价性词语。
"""

        TranslateStyle.Idiomatic -> """
翻译风格：地道中文口语。
- 用地道、自然的中文表达，优先传达意思而非字面结构。
- 允许调整语序、合并或拆分句子，读起来要像中文母语者写的。
- 习语、俚语译为对应的中文惯用表达，不要直译。
- 语气贴近原文正式程度，但整体可以更轻松。
"""

        TranslateStyle.Literal -> """
翻译风格：直译。
- 尽量贴近原文句子结构，逐句对应。
- 不增删信息，不做意译。
- 即使略生硬，也优先保证与原文的对应关系清晰。
- 仅当直译导致完全无法理解时，才做最小幅度调整。
"""
    }

    private fun englishRules(style: TranslateStyle): String = when (style) {
        TranslateStyle.Academic -> """
Translation style: academic English.
- Use a formal academic register with precise, discipline-appropriate terminology.
- Prefer active voice where it is clear and idiomatic; avoid gratuitous passive constructions.
- Preserve the logical force of the original (causality, concession, contrast);
  do not soften or strengthen claims.
- Avoid filler and redundancy ("in order to", "it is important to note that")
  unless the original carries that emphasis.
- Keep hedging terms ("may", "suggests", "is likely to") exactly as strong as the source.
- Maintain terminology consistency within the passage.
"""

        TranslateStyle.Idiomatic -> """
Translation style: idiomatic English.
- Write natural, fluent English that reads as if originally written in English.
- Reorder, merge, or split sentences freely to achieve native flow.
- Render idioms and colloquialisms as their English equivalents rather than literally.
- Match the source's level of formality, but favour readability over word-for-word fidelity.
"""

        TranslateStyle.Literal -> """
Translation style: literal.
- Stay close to the source sentence structure and word order.
- Add or omit nothing; do not paraphrase.
- Prefer a clear one-to-one correspondence with the source even if the result
  reads slightly awkwardly.
- Adjust only where a literal rendering would be incomprehensible.
"""
    }

    fun systemPrompt(
        style: TranslateStyle,
        target: TextLanguage
    ): String = buildString {
        appendLine(COMMON_RULES.trim())
        appendLine()
        appendLine(styleRules(style, target).trim())
        appendLine()
        append("目标语言 / Target language: ${target.promptName}。")
    }

    /**
     * @param partIndex 当前块序号（从 1 开始）
     * @param partCount 总块数
     * @param previousTail 上一块末尾原文，用于衔接代词与术语；第一块传 null
     */
    fun userPrompt(
        text: String,
        partIndex: Int = 1,
        partCount: Int = 1,
        previousTail: String? = null
    ): String = buildString {
        if (partCount > 1) {
            appendLine("这是待翻译文档的第 $partIndex / $partCount 部分。")
            if (previousTail != null) {
                appendLine("【上一部分末尾原文，仅供衔接上下文，不要翻译它】")
                appendLine(previousTail.trim())
                appendLine("【上一部分结束】")
            }
            appendLine("只翻译下面【待翻译原文】之间的内容：")
            appendLine("【待翻译原文】")
            append(text)
            appendLine()
            append("【原文结束】")
        } else {
            append(text)
        }
    }
}
