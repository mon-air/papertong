package com.papertong.app.core.history

import android.util.AtomicFile
import android.util.Log
import com.papertong.app.core.crypto.KeystoreCipher
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 用 JSON 文件存历史，**落盘前整体加密**。
 *
 * 为什么不用 Room：历史是追加写、倒序读的简单列表，
 * 上限 100 条也就几十 KB，引入 Room 的注解处理器与迁移成本不划算。
 * 条目再多、需要搜索时应换 Room —— 接口已经隔离好。
 *
 * 加密：历史里存着用户论文的原文与译文，是这台设备上最敏感的数据之一。
 * 用 Android Keystore 的 AES-256-GCM 整体加密（密钥不可导出）。
 * 代价是密钥丢失（清数据/换机/恢复出厂）后历史**永久无法恢复**，
 * 这与"本应用不上传任何数据"是一致的选择：没有云端副本可以找回。
 *
 * 旧版本留下的明文文件会被自动识别并加密迁移，见 [readFile]。
 *
 * 原子性：用 AtomicFile 写入，避免写到一半被系统杀死导致文件损坏。
 */
class FileHistoryStore(
    private val file: File,
    private val cipher: KeystoreCipher,
    private val maxEntries: Int = MAX_ENTRIES
) : HistoryStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serializer = ListSerializer(HistoryEntry.serializer())

    /** 内存缓存，界面直接订阅它，避免每次进页面都读磁盘。 */
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private val mutex = Mutex()
    private val atomicFile = AtomicFile(file)

    override suspend fun loadAll(): List<HistoryEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = readFile()
            _entries.value = loaded
            loaded
        }
    }

    override suspend fun add(entry: HistoryEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = (listOf(entry) + _entries.value)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
                .take(maxEntries)
            writeFile(updated)
            _entries.value = updated
        }
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _entries.value.filterNot { it.id == id }
            writeFile(updated)
            _entries.value = updated
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeFile(emptyList())
            _entries.value = emptyList()
        }
    }

    private fun readFile(): List<HistoryEntry> {
        val handle = try {
            atomicFile.openRead()
        } catch (e: IOException) {
            // 文件不存在是正常情况（首次使用）
            return emptyList()
        }

        val bytes = try {
            handle.use { it.readBytes() }
        } catch (e: IOException) {
            Log.w(TAG, "历史文件读取失败：${e.javaClass.simpleName}")
            return emptyList()
        }

        if (bytes.isEmpty()) return emptyList()

        // 解密失败有两种可能：旧版本留下的明文（要迁移），或密钥已失效（只能丢弃）。
        // 用"内容是否像 JSON 数组"区分——明文的第一个非空字符必然是 '['。
        var needsMigration = false
        val plaintext = try {
            String(cipher.decrypt(bytes), Charsets.UTF_8)
        } catch (e: Exception) {
            val legacy = String(bytes, Charsets.UTF_8)
            if (legacy.trimStart().startsWith("[")) {
                needsMigration = true
                legacy
            } else {
                // 密文解不开就是永久解不开，留着只会每次启动都失败一次
                Log.w(TAG, "历史解密失败，密钥可能已失效：${e.javaClass.simpleName}")
                return emptyList()
            }
        }

        val entries = try {
            if (plaintext.isBlank()) emptyList()
            else json.decodeFromString(serializer, plaintext)
        } catch (e: Exception) {
            // 解析失败说明文件损坏，丢弃而不是让 App 起不来
            Log.w(TAG, "历史文件损坏，已重置：${e.javaClass.simpleName}")
            return emptyList()
        }

        // 解析成功后才回写密文：坏数据不该被"加密迁移"成一份看起来正常的坏数据
        if (needsMigration && entries.isNotEmpty()) {
            runCatching { writeFile(entries) }
                .onSuccess { Log.i(TAG, "历史文件已加密迁移") }
                .onFailure { Log.w(TAG, "历史加密迁移失败：${it.javaClass.simpleName}") }
        }

        return entries
    }

    private fun writeFile(entries: List<HistoryEntry>) {
        val payload = json.encodeToString(serializer, entries)
        val encrypted = cipher.encrypt(payload.toByteArray(Charsets.UTF_8))

        val stream = atomicFile.startWrite()
        try {
            stream.write(encrypted)
            atomicFile.finishWrite(stream)
        } catch (e: IOException) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    companion object {
        const val MAX_ENTRIES = 100

        /** 文件名保留旧名，避免升级后历史丢失；内容已不是 JSON，见类注释。 */
        const val FILE_NAME = "translation_history.json"

        /** 历史加密专用密钥别名，与 API Key 的分开。 */
        const val KEY_ALIAS = "papertong_history_v1"
        private const val TAG = "FileHistoryStore"
    }
}
