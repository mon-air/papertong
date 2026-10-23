package com.papertong.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.papertong.app.core.history.FileHistoryStore
import com.papertong.app.core.history.HistoryEntry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val store: FileHistoryStore
) : ViewModel() {

    val entries: StateFlow<List<HistoryEntry>> = store.entries

    init {
        // 首次进入时从磁盘读一次，之后靠内存 StateFlow 推送
        viewModelScope.launch { store.loadAll() }
    }

    fun remove(id: String) {
        viewModelScope.launch { store.remove(id) }
    }

    fun clearAll() {
        viewModelScope.launch { store.clear() }
    }

    companion object {
        fun factory(store: FileHistoryStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HistoryViewModel(store) as T
            }
    }
}
