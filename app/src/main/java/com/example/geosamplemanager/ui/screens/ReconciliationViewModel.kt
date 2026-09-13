package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import com.example.geosamplemanager.data.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel экрана «Сверка и поиск».
 *
 * Ленивая загрузка (5.9.1):
 *  • При открытии экрана грузим только участки и список нарядов.
 *  • Пробы наряда грузятся при его выборе.
 *  • При поиске по query — SQL-запрос находит order_id с совпадениями,
 *    и их группы загружаются.
 *
 * Поиск с задержкой (5.9.2):
 *  • debounce 450 мс — пока человек печатает, запрос не летит в БД.
 *  • Не больше MAX_SEARCH_ORDERS нарядов за раз — иначе при широком
 *    запросе вроде «KPD» можно подтянуть тысячи проб.
 *  • Если совпадений больше — сообщаем пользователю.
 *
 * Кэш: loadedOrderIds — что уже загружено. Повторно не грузим.
 */
class ReconciliationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    val state = ReconciliationState(emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val loadedOrderIds = mutableSetOf<Long>()

    private var orderInfoById: Map<Long, OrderInfo> = emptyMap()
    private var orderInfoByTitle: Map<String, OrderInfo> = emptyMap()

    private var searchJob: Job? = null

    companion object {
        /** Сколько миллисекунд ждать после последнего нажатия. */
        private const val SEARCH_DEBOUNCE_MS = 450L

        /** Максимум нарядов, которые грузим за один поисковый запрос. */
        private const val MAX_SEARCH_ORDERS = 20
    }

    init {
        loadAreasAndOrders()
    }

    /**
     * Загрузка справочников: участки + список всех непустых нарядов.
     * Пробы НЕ грузим — это делается лениво.
     */
    private fun loadAreasAndOrders() {
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val areas = repo.getAreas()
                    val orderIdsWithSamples = repo.getOrderIdsWithSamples().toHashSet()
                    val result = mutableListOf<OrderInfo>()
                    areas.forEach { area ->
                        val orders = repo.getOrdersForAreaList(area.id)
                        orders.forEach { order ->
                            if (order.id in orderIdsWithSamples) {
                                result.add(
                                    OrderInfo(
                                        areaId = area.id,
                                        orderId = order.id,
                                        areaTitle = area.areaName,
                                        orderTitle = "Наряд №${order.orderNumber}"
                                    )
                                )
                            }
                        }
                    }
                    result
                }
                orderInfoById = info.associateBy { it.orderId }
                orderInfoByTitle = info.associateBy { it.orderTitle }
                state.allOrderTitles = info
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    // ================================================================
    // ЛЕНИВАЯ ЗАГРУЗКА ГРУПП
    // ================================================================

    /** Гарантирует, что группа наряда загружена в state. */
    suspend fun ensureOrderSamplesLoaded(orderId: Long) {
        if (orderId in loadedOrderIds) return
        val info = orderInfoById[orderId]
        if (info == null) return
        try {
            val group = withContext(Dispatchers.IO) {
                val samples = repo.getSamplesForOrderList(orderId)
                if (samples.isEmpty()) return@withContext null
                val order = repo.getAllOrders().firstOrNull { it.id == orderId }
                if (order == null) return@withContext null
                val area = repo.getAreas().firstOrNull { it.id == order.areaId }
                buildSampleGroup(order, area, samples)
            }
            if (group != null) {
                withContext(Dispatchers.Main) {
                    state.addGroup(group)
                    loadedOrderIds.add(orderId)
                }
            } else {
                loadedOrderIds.add(orderId)
            }
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки наряда: ${e.message}"
        }
    }

    /**
     * Загрузка нарядов, в которых есть пробы, подходящие под query.
     * Ограничиваем количество — если совпадений слишком много,
     * просим уточнить запрос.
     */
    private suspend fun loadGroupsForQuery(query: String) {
        try {
            val allIds = withContext(Dispatchers.IO) {
                repo.findOrderIdsByQuery(query)
            }
            if (allIds.isEmpty()) return

            // Уже загруженные исключаем из счётчика «осталось»
            val newIds = allIds.filter { it !in loadedOrderIds }
            val toLoad = newIds.take(MAX_SEARCH_ORDERS)

            toLoad.forEach { ensureOrderSamplesLoaded(it) }

            val remaining = newIds.size - toLoad.size
            if (remaining > 0) {
                withContext(Dispatchers.Main) {
                    _message.value = "Ещё $remaining нарядов — уточните запрос"
                }
            }
        } catch (e: Exception) {
            _message.value = "Ошибка поиска: ${e.message}"
        }
    }

    // ================================================================
    // ПУБЛИЧНЫЕ СЕТТЕРЫ ДЛЯ UI
    // ================================================================

    fun setSelectedArea(area: String?) {
        state.selectedArea = area
        state.selectedOrder = null
    }

    fun setSelectedOrder(orderTitle: String?) {
        state.selectedOrder = orderTitle
        val info = orderTitle?.let { orderInfoByTitle[it] } ?: return
        viewModelScope.launch { ensureOrderSamplesLoaded(info.orderId) }
    }

    /**
     * Реакция на ввод в поле поиска.
     *
     * Каждое новое нажатие отменяет предыдущую задачу и запускает новую
     * с задержкой 450 мс. То есть запрос летит в БД, только когда
     * пользователь перестал печатать.
     */
    fun setQuery(query: String) {
        state.query = query
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadGroupsForQuery(query)
        }
    }

    // ================================================================
    // Одиночные действия — атомарные UPDATE
    // ================================================================

    private fun rowById(rowId: String): SampleRow? = state.rowById(rowId)

    fun toggleFound(rowId: String) {
        state.toggleFound(rowId)
        val id = rowId.toLongOrNull() ?: return
        val found = rowById(rowId)?.found ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setFound(id, found) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setFound(rowId: String, value: Boolean) {
        state.setFound(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setFound(id, value) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setControlWeightAndFound(rowId: String, weight: Double) {
        state.setControlWeightAndFound(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.setControlWeight(id, weight)
                    repo.setFound(id, true)
                }
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setControlWeight(rowId: String, weight: Double) {
        state.setControlWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setControlWeight(id, weight) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setBlankWeightAndMarkFound(rowId: String, weight: Double) {
        state.setBlankWeightAndMarkFound(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.setWeight(id, weight)
                    repo.setFound(id, true)
                }
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setWeight(rowId: String, weight: Double) {
        state.setWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setWeight(id, weight) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setPostponed(rowId: String, value: Boolean) {
        state.setPostponed(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setPostponed(id, value) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun toggleWeightControl(rowId: String): Boolean {
        val ok = state.toggleWeightControl(rowId)
        if (ok) {
            val id = rowId.toLongOrNull() ?: return ok
            val flag = rowById(rowId)?.weightControl ?: return ok
            viewModelScope.launch {
                try { withContext(Dispatchers.IO) { repo.setWeightControl(id, flag) } }
                catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
            }
        }
        return ok
    }

    // ================================================================
    // Массовые действия
    // ================================================================

    fun applyBulkMarkFound(
        groupId: String,
        weights: Map<String, Double>,
        postponedActions: Map<String, Boolean>
    ): Int {
        val marked = state.applyBulkMarkFound(groupId, weights, postponedActions)
        persistGroup(groupId)
        return marked
    }

    fun clearAllFound(groupId: String) {
        state.clearAllFound(groupId)
        persistGroup(groupId)
    }

    // ================================================================
    // Удаление
    // ================================================================

    fun deleteRow(rowId: String, recalc: Boolean) {
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.deleteSampleWithRenumber(id, recalc)
                }
                state.deleteRow(rowId, recalc)
            } catch (e: Exception) {
                _message.value = "Ошибка удаления: ${e.message}"
            }
        }
    }

    // ================================================================
    // Undo / Redo
    // ================================================================

    fun undo() {
        state.undo()
        persistAll()
    }

    fun redo() {
        state.redo()
        persistAll()
    }

    // ================================================================
    // Настройки наряда
    // ================================================================

    fun applyBlankSettingsForOrder(
        orderTitle: String,
        settings: BlankWeightSettings,
        weightControlStep: Int
    ): Int {
        val changed = state.applyBlankSettingsForOrder(orderTitle, settings, weightControlStep)
        persistOrder(orderTitle)
        return changed
    }

    fun resetBlankSettingsToGlobal(orderTitle: String): Int {
        val changed = state.resetBlankSettingsToGlobal(orderTitle)
        persistOrder(orderTitle)
        return changed
    }

    fun resetBlankWeightsForOrder(orderTitle: String): Int {
        val changed = state.resetBlankWeightsForOrder(orderTitle)
        persistOrder(orderTitle)
        return changed
    }

    // ================================================================
    // ЗАМЕТКИ И ФОТО
    // ================================================================

    suspend fun loadNoteWithPhotos(
        sampleId: Long
    ): Pair<SampleNoteEntity?, List<SampleImageEntity>> {
        return try {
            withContext(Dispatchers.IO) { repo.getNoteWithPhotos(sampleId) }
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки заметки: ${e.message}"
            null to emptyList()
        }
    }

    suspend fun saveNoteText(sampleId: Long, text: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    repo.deleteNote(sampleId)
                } else {
                    val existing = repo.getNote(sampleId)
                    val note = SampleNoteEntity(
                        id = existing?.id ?: 0,
                        sampleId = sampleId,
                        noteText = trimmed,
                        createdDate = existing?.createdDate ?: System.currentTimeMillis()
                    )
                    repo.upsertNote(note)
                }
                repo.syncHasNoteAndPhoto(sampleId)
                val s = repo.getSampleById(sampleId)
                if (s != null) {
                    withContext(Dispatchers.Main) {
                        state.updateRowFlags(sampleId.toString(), s.hasNote, s.hasPhoto)
                    }
                }
            }
            true
        } catch (e: Exception) {
            _message.value = "Ошибка сохранения заметки: ${e.message}"
            false
        }
    }

    suspend fun addPhoto(sampleId: Long, sourceUri: Uri): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val path = withContext(Dispatchers.IO) {
                PhotoStorage.compressAndSave(ctx, sourceUri)
            }
            if (path == null) {
                _message.value = "Не удалось обработать фото"
                return false
            }
            withContext(Dispatchers.IO) {
                repo.addPhoto(sampleId, path)
                refreshSampleFlagsInternal(sampleId)
            }
            true
        } catch (e: Exception) {
            _message.value = "Ошибка добавления фото: ${e.message}"
            false
        }
    }

    suspend fun addPhotoFromFile(sampleId: Long, tempFile: File): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val path = withContext(Dispatchers.IO) {
                PhotoStorage.compressAndSaveFromFile(ctx, tempFile)
            }
            if (path == null) {
                _message.value = "Не удалось обработать фото"
                return false
            }
            withContext(Dispatchers.IO) {
                repo.addPhoto(sampleId, path)
                refreshSampleFlagsInternal(sampleId)
            }
            true
        } catch (e: Exception) {
            _message.value = "Ошибка добавления фото: ${e.message}"
            false
        }
    }

    suspend fun deletePhoto(imageId: Long, sampleId: Long): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val ok = repo.deletePhoto(imageId, sampleId)
                if (ok) refreshSampleFlagsInternal(sampleId)
                ok
            }
        } catch (e: Exception) {
            _message.value = "Ошибка удаления фото: ${e.message}"
            false
        }
    }

    private suspend fun refreshSampleFlagsInternal(sampleId: Long) {
        val s = repo.getSampleById(sampleId) ?: return
        withContext(Dispatchers.Main) {
            state.updateRowFlags(sampleId.toString(), s.hasNote, s.hasPhoto)
        }
    }

    // ================================================================
    // Сохранение
    // ================================================================

    private fun persistGroup(groupId: String) {
        val group = state.groups.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.saveRows(group.rows) } }
            catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistOrder(orderTitle: String) {
        val groups = state.groups.filter { it.orderTitle == orderTitle }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    groups.forEach { repo.saveRows(it.rows) }
                }
            } catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    state.groups.forEach { repo.saveRows(it.rows) }
                }
            } catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }
}