package com.papertong.app.data.remote

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.ResponseBody

/**
 * 服务端发送事件（SSE）解析。
 *
 * DeepSeek 的事件格式：
 *   data: {"choices":[{"delta":{"content":"你"}}]}
 *   data: [DONE]
 *
 * 要点：
 *  - 用 source.readUtf8Line() 逐行读；readText 会等整个响应结束，流式就没了
 *  - 以 data: 开头的行才是事件，其余（空行、注释）跳过
 *  - 收到 [DONE] 立刻停，继续读会挂到 readTimeout
 *  - 每次循环检查协程是否被取消，否则取消后仍会阻塞在 readUtf8Line
 */
object SseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(body: ResponseBody): Flow<String> = flow {
        try {
            val source = body.source()

            while (true) {
                currentCoroutineContext().ensureActive()

                val line = source.readUtf8Line() ?: break
                if (line.isEmpty() || line.startsWith(":")) continue
                if (!line.startsWith(DATA_PREFIX)) continue

                val payload = line.removePrefix(DATA_PREFIX).trim()
                if (payload == DONE_SENTINEL) break
                if (payload.isEmpty()) continue

                val text = extractDelta(payload)
                if (!text.isNullOrEmpty()) {
                    emit(text)
                }
            }
        } catch (e: IOException) {
            // 主动取消时底层 socket 被关闭，属于正常路径；
            // 若协程仍活跃，说明是真的网络中断，向上抛出。
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            runCatching { body.close() }
        }
    }

    /** 从一条 data 中取 delta.content；结构不符或解析失败时返回 null。 */
    internal fun extractDelta(payload: String): String? {
        val obj = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
            ?: return null

        val choices = obj["choices"] as? JsonArray ?: return null
        if (choices.isEmpty()) return null

        val first = choices.first() as? JsonObject ?: return null
        val delta = first["delta"] as? JsonObject ?: return null

        // 刻意不读 reasoning_content：那是思维链，混进译文会毁掉结果
        return (delta["content"] as? JsonPrimitive)?.contentOrNull
    }

    private const val DATA_PREFIX = "data:"
    private const val DONE_SENTINEL = "[DONE]"
}
