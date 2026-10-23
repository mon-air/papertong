package com.papertong.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    /**
     * reasoner 模型不接受 temperature，调用方按模型置空。
     */
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

/** 流式返回的单个数据块。只关心 delta.content，reasoning_content 一律忽略。 */
@Serializable
data class ChatStreamChunk(
    val choices: List<StreamChoice> = emptyList()
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta = StreamDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamDelta(
    val content: String? = null,
    /** deepseek-reasoner 的思维链，不能混进译文。 */
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

/** 非 2xx 时的错误体。 */
@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody? = null
)

@Serializable
data class ApiErrorBody(
    val message: String? = null,
    val type: String? = null
)
