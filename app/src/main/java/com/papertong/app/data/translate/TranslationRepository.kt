package com.papertong.app.data.translate

import com.papertong.app.core.crypto.SecretStore
import com.papertong.app.core.model.DirectionPair
import com.papertong.app.core.model.ModelOption
import com.papertong.app.core.model.TranslateStyle
import com.papertong.app.core.util.TextChunker
import com.papertong.app.data.remote.ChatMessage
import com.papertong.app.data.remote.ChatRequest
import com.papertong.app.data.remote.DeepSeekClient
import com.papertong.app.data.remote.SseParser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 翻译编排层：取 Key → 分块 → 逐块流式请求 → 按序拼接。
 *
 * 为什么逐块串行而不是并发：
 *  - 串行保证译文顺序天然正确，UI 层不需要重排序
 *  - 并发更容易触发 API 限流，且首块未返回时用户什么都看不到
 *  - 长文档的体验关键是"尽快看到开头"，串行正好满足
 */
class TranslationRepository(
    private val secretStore: SecretStore
) {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private val errorJson = Json { ignoreUnknownKeys = true }

    sealed interface Event {
        /** 实际采用的方向，在分块之前确定，供 UI 显示「英 → 中」。 */
        data class Direction(val pair: DirectionPair) : Event
        data class ChunkStart(val index: Int, val total: Int) : Event
        data class Delta(val text: String) : Event
        data class ChunkDone(val index: Int) : Event
        data class Failed(val message: String, val retryable: Boolean) : Event
    }

    fun translate(
        text: String,
        style: TranslateStyle,
        model: ModelOption,
        direction: DirectionPair,
        chunkSize: Int = TextChunker.DEFAULT_CHUNK_SIZE
    ): Flow<Event> = flow {
        val apiKey = secretStore.readApiKey()
        if (apiKey.isNullOrBlank()) {
            emit(Event.Failed("尚未配置 API Key，请先到设置页填写", retryable = false))
            return@flow
        }

        val chunks = TextChunker.split(text, chunkSize = chunkSize)
        if (chunks.isEmpty()) {
            emit(Event.Failed("没有可翻译的内容", retryable = false))
            return@flow
        }

        emit(Event.Direction(direction))
        val systemPrompt = PromptPresets.systemPrompt(style, direction.target)

        for (chunk in chunks) {
            emit(Event.ChunkStart(chunk.index, chunks.size))

            val requestBody = ChatRequest(
                model = model.id,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(
                        role = "user",
                        content = PromptPresets.userPrompt(
                            text = chunk.text,
                            partIndex = chunk.index,
                            partCount = chunks.size,
                            previousTail = chunk.previousTail
                        )
                    )
                ),
                stream = true,
                // reasoner 不接受 temperature，传了会 400
                temperature = if (model == ModelOption.Reasoner) null else 0.3
            )

            val payload = json.encodeToString(ChatRequest.serializer(), requestBody)
                .toRequestBody(DeepSeekClient.jsonMediaType)

            val response = try {
                DeepSeekClient.api.chatCompletions(
                    authorization = "Bearer $apiKey",
                    body = payload
                )
            } catch (e: IOException) {
                emit(Event.Failed("网络异常，请检查连接", retryable = true))
                return@flow
            }

            if (!response.isSuccessful) {
                val code = response.code()
                val detail = runCatching {
                    response.errorBody()?.string()?.let(::extractErrorMessage)
                }.getOrNull()

                val message = when (code) {
                    401 -> "API Key 无效或已过期"
                    402 -> "账户余额不足"
                    429 -> "请求过于频繁，请稍后再试"
                    in 500..599 -> "服务端错误（$code）"
                    else -> buildString {
                        append("请求失败（$code）")
                        if (!detail.isNullOrBlank()) append("：$detail")
                    }
                }
                emit(Event.Failed(message, retryable = code == 429 || code >= 500))
                return@flow
            }

            val body = response.body()
            if (body == null) {
                emit(Event.Failed("响应为空", retryable = true))
                return@flow
            }

            try {
                SseParser.parse(body).collect { delta -> emit(Event.Delta(delta)) }
            } catch (e: IOException) {
                emit(Event.Failed("连接中断，请重试", retryable = true))
                return@flow
            }

            emit(Event.ChunkDone(chunk.index))
        }
    }.flowOn(Dispatchers.IO)

    private fun extractErrorMessage(raw: String): String? = runCatching {
        val root = errorJson.parseToJsonElement(raw) as? JsonObject ?: return null
        val error = root["error"] as? JsonObject ?: return null
        (error["message"] as? JsonPrimitive)?.contentOrNull
    }.getOrNull()
}
