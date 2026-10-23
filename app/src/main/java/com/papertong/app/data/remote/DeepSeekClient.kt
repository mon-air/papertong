package com.papertong.app.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * DeepSeek 客户端。
 *
 * 超时策略：
 *  - connect 15s：移动网络下握手慢是常态
 *  - read 120s：流式响应期间两个数据块之间可能停顿较久，
 *    实测长段落首 token 延迟可到十几秒，给足余量
 *  - callTimeout 不设：长文本翻译整体可能超过 10 分钟，
 *    整体超时交给调用方的协程取消
 */
object DeepSeekClient {

    private const val BASE_URL = "https://api.deepseek.com/"

    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val api: DeepSeekApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .build()
        .create(DeepSeekApi::class.java)
}
