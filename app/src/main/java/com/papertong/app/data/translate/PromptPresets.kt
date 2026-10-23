package com.papertong.app.data.translate

import com.papertong.app.core.model.TranslateStyle

/**
 * 提示词构建。
 *
 * 设计原则：
 *  1. 系统提示词里写清"不要翻译什么"（公式、引用、代码段），比只写"要翻译什么"更有效
 *  2. 明确禁止添加解释、前言、Markdown 围栏 —— 否则模型爱加「以下是译文：」
 *  3. 分段占位符 [[SEG_n]] 用于让模型保持段落边界，合并时可按序拼接
 *  4. 术语一致性通过"同一术语在同一段落内保持统一译法"来约束，
 *     跨块的全局术语表需要二次往返，MVP 不做
 */
object PromptPresets {

    /** 所有风格共用的硬性约束。 */
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

    private fun styleRules(style: TranslateStyle): String = when (style) {
        TranslateStyle.Academic -> """
翻译风格：学术论文。
- 使用规范的中文学术书面语，避免口语化表达。
- 术语采用该领域通用译法；首次出现时可保留英文原词并用括号标注，
  但同一术语全文只标注一次。
- 忠实还原原文逻辑关系（因果、转折、让步），不要为了通顺而改变论述强度。
- 被动语态按中文学术惯例处理，不强行逐字对应。
- 长句可适当拆分，但不得合并或删除信息。
- 保持客观、克制的语气，不要添加评价性词语。
"""

        TranslateStyle.Idiomatic -> """
翻译风格：地道口语。
- 用地道、自然的表达，优先传达意思而非字面结构。
- 允许调整语序、合并或拆分句子，使读起来像母语者写的。
- 习语、俚语译为对应的目标语言惯用表达，不要直译。
- 语气贴近原文的正式程度，但整体可以更轻松。
"""

        TranslateStyle.Literal -> """
翻译风格：直译。
- 尽量贴近原文的句子结构和语序，逐句对应。
- 不增删信息，不做意译。
- 即使译文略显生硬，也优先保证与原文的对应关系清晰。
- 仅当直译会导致完全无法理解时，才做最小幅度的调整。
"""
    }

    /**
     * 构建 system prompt。
     * @param style 用户选择的风格
     * @param targetLanguage 目标语言，默认简体中文
     */
    fun systemPrompt(
        style: TranslateStyle,
        targetLanguage: String = "简体中文"
    ): String = buildString {
        appendLine(COMMON_RULES.trim())
        appendLine()
        appendLine(styleRules(style).trim())
        appendLine()
        append("目标语言：$targetLanguage。")
    }

    /**
     * 构建 user prompt。
     *
     * @param text 待翻译正文
     * @param partIndex 当前块序号（从 1 开始），多块时用于让模型知道上下文位置
     * @param partCount 总块数
     * @param previousTail 上一块的末尾原文，用于衔接代词与术语；
     *                     第一块传 null。只传尾部而不是整块，避免浪费 token。
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
