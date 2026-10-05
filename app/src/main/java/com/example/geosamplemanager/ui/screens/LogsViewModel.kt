package com.example.geosamplemanager.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.data.logs.LogEntry
import com.example.geosamplemanager.data.logs.LogsDatabase
import com.example.geosamplemanager.data.logs.LogsFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.9-logs-5:
 * ViewModel для экрана «Журнал событий». Читает записи
 * из LogsDatabase, фильтрует по категории, поддерживает
 * очистку.
 *
 * Записи — от свежих к старым. Максимум 500 на экран.
 */
class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = LogsDatabase.getInstance(application).logDao()

    private val _filter = MutableStateFlow(LogsFilter.ALL)
    val filter: StateFlow<LogsFilter> = _filter.asStateFlow()

    private val _records = MutableStateFlow<List<LogEntry>>(emptyList())
    val records: StateFlow<List<LogEntry>> = _records.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        reload()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setFilter(f: LogsFilter) {
        _filter.value = f
        reload()
    }

    fun reload() {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val code = _filter.value.code
                    if (code == null) dao.getLatest(MAX_RECORDS)
                    else dao.getByCategory(code, MAX_RECORDS)
                }
                _records.value = list
            } catch (e: Exception) {
                _message.value = "Ошибка чтения журнала: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { dao.deleteAll() }
                _records.value = emptyList()
            } catch (e: Exception) {
                _message.value = "Ошибка очистки: ${e.message}"
            }
        }
    }

    companion object {
        private const val MAX_RECORDS = 500
    }
}