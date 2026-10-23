package com.papertong.app.core.history

import android.util.AtomicFile
import android.util.Log
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
 * 用 JSON 文件存历史。
 *
 * 为什么不用 Room：历史是追加写、倒序读的简单列表，
 * 上限 100 条也就几十 KB，引入 Room 的注解处理器与迁移成本不划算。
 * 条目再多、需要搜索时应换 Room —— 接口已经隔离好。
 *
 * 原子性：用 AtomicFile 写入，避免写到一半被系统杀死导致文件损坏。
 */
class FileHistoryStore(
    private val file: File,
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

        return try {
            handle.bufferedReader().use { it.readText() }
                .let { raw ->
                    if (raw.isBlank()) emptyList()
                    else json.decodeFromString(serializer, raw)
                }
        } catch (e: Exception) {
            // 解析失败说明文件损坏，丢弃而不是让 App 起不来
            Log.w(TAG, "历史文件损坏，已重置：${e.javaClass.simpleName}")
            emptyList()
        } finally {
            runCatching { handle.close() }
        }
    }

    private fun writeFile(entries: List<HistoryEntry>) {
        val payload = json.encodeToString(serializer, entries)
        val stream = atomicFile.startWrite()
        try {
            stream.write(payload.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: IOException) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    companion object {
        const val MAX_ENTRIES = 100
        private const val TAG = "FileHistoryStore"
        const val FILE_NAME = "translation_history.json"
    }
}
