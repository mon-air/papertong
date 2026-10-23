package com.papertong.app.core.history

/**
 * 翻译历史存储。
 *
 * 刻意做成接口：MVP 用 JSON 文件实现，条目多到影响加载速度时应换成 Room，
 * 届时只需替换实现，界面与 ViewModel 不用动。
 */
interface HistoryStore {

    /** 按时间倒序返回全部记录。 */
    suspend fun loadAll(): List<HistoryEntry>

    /** 追加一条记录；实现方负责按上限裁剪。 */
    suspend fun add(entry: HistoryEntry)

    suspend fun remove(id: String)

    suspend fun clear()
}
