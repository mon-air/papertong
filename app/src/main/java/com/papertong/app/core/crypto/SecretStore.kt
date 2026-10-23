package com.papertong.app.core.crypto

import kotlinx.coroutines.flow.Flow

/**
 * API Key 的存取接口。
 *
 * 抽象成接口有两个原因：
 *  1. 便于将来更换实现（例如加生物识别保护）；
 *  2. 测试时可以换成内存实现，避免依赖真实 Keystore。
 *
 * 约定：实现方不得把明文写入日志、崩溃报告或任何明文存储。
 */
interface SecretStore {

    /** 是否已保存过 Key。用于界面展示，不暴露明文。 */
    val hasSecret: Flow<Boolean>

    /** 保存（覆盖）API Key，内部负责加密。传入需调用方 trim。 */
    suspend fun saveApiKey(raw: String)

    /** 读取明文 Key；未保存或解密失败时返回 null。 */
    suspend fun readApiKey(): String?

    /** 清除已保存的 Key（同时删除密文）。 */
    suspend fun clear()
}
