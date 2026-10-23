package com.papertong.app.core.crypto

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 校验 API Key 是否可用。
 *
 * 做法是请求 DeepSeek 的模型列表接口：它开销极低、不产生 token 费用，
 * 且能区分「Key 无效(401)」和「网络不通」，比单纯检查字符串格式有意义。
 */
class ApiKeyValidator(
    private val client: OkHttpClient = defaultClient()
) {

    sealed interface Result {
        data object Valid : Result
        data object Unauthorized : Result
        data class Failed(val message: String) : Result
    }

    suspend fun validate(apiKey: String): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.Valid
                    response.code == 401 || response.code == 403 -> Result.Unauthorized
                    else -> Result.Failed("服务返回 ${response.code}")
                }
            }
        } catch (e: IOException) {
            Result.Failed("无法连接，请检查网络")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.deepseek.com/models"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
