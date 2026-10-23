package com.papertong.app.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

interface DeepSeekApi {

    /**
     * 流式对话补全。
     *
     * @Streaming 是关键：不加它 Retrofit 会把整个响应读进内存，
     * 流式就退化成一次性返回。
     * Authorization 显式传参，避免用 Interceptor 从加密存储里同步取值。
     */
    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "text/event-stream",
        @Body body: RequestBody
    ): Response<ResponseBody>
}
