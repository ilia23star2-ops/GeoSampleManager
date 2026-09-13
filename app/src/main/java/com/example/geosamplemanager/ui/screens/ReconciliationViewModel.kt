package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import com.example.geosamplemanager.data.settings.ImportSettings
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
 */
class ReconciliationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository
    private val settingsRepo = (application as GeoSampleApp).settingsRepository

    val state = ReconciliationState(emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val loadedOrderIds = mutableSetOf<Long>()

    private var orderInfoById: Map<Long, OrderInfo> = emptyMap()
    private var orderInfoByTitle: Map<String, OrderInfo> = emptyMap()

    private var searchJob: Job? = null

    companion object {
        /**
         * Мини-задержка перед поиском — пока пользователь печатает,
         * поиск не запускается.
         */
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val MAX_SEARCH_ORDERS = 20
        private const val MAX_QUERY_TOKENS = 5
    }

    init {
        loadAreasAndOrders()
    }

    private fun loadAreasAndOrders() {
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val areas = repo.getAreas()
                    val result = mutableListOf<OrderInfo>()
                    areas.forEach { area ->
                        val orders = repo.getOrdersForAreaList(area.id)
                        orders.forEach { order ->
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
                    val areaNames = areas.map { it.areaName }.distinct().sorted()
                    areaNames to result
                }
                val (areaNames, orderInfos) = info
                orderInfoById = orderInfos.associateBy { it.orderId }
                orderInfoByTitle = orderInfos.associateBy { it.orderTitle }
                state.allOrderTitles = orderInfos
                state.allAreaNames = areaNames
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    // ================================================================
    // ЛЕНИВАЯ ЗАГРУЗКА ГРУПП
    // ================================================================

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

    private suspend fun loadGroupsForQuery(query: String) {
        try {
            val allIds = withContext(Dispatchers.IO) {
                repo.findOrderIdsByQuery(query)
            }
            if (allIds.isEmpty()) return
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
    // СЕТТЕРЫ
    // ================================================================

    fun setSelectedArea(area: String?) {
        state.selectedArea = area
        state.selectedOrder = null
        refreshMultiQueryIfNeeded()
    }

    fun setSelectedOrder(orderTitle: String?) {
        state.selectedOrder = orderTitle
        val info = orderTitle?.let { orderInfoByTitle[it] }
        if (info != null) {
            viewModelScope.launch { ensureOrderSamplesLoaded(info.orderId) }
        }
        refreshMultiQueryIfNeeded()
    }

    /**
     * Пересобрать группы мультизапроса — нужно при смене участка/наряда,
     * чтобы корректно пересчитался флаг «другой участок».
     */
    private fun refreshMultiQueryIfNeeded() {
        if (!state.isMultiQuery) return
        val tokens = state.queryTokens
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            buildMultiQueryGroups(tokens)
        }
    }

    /**
     * Реакция на ввод в поле поиска.
     *
     * state.query обновляется сразу — чтобы текст в поле ввода шёл без лагов.
     * Разбор на токены и запуск поиска — только после паузы.
     */
    fun setQuery(query: String) {
        state.query = query

        searchJob?.cancel()

        // Пустой запрос — сбрасываем всё сразу.
        if (query.isBlank()) {
            state.queryTokens = emptyList()
            state.clearQueryGroups()
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)

            val tokens = query.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(MAX_QUERY_TOKENS)

            state.queryTokens = tokens

            when (tokens.size) {
                0 -> {
                    state.clearQueryGroups()
                }
                1 -> {
                    state.clearQueryGroups()
                    loadGroupsForQuery(tokens[0])
                }
                else -> {
                    buildMultiQueryGroups(tokens)
                }
            }
        }
    }

    // ================================================================
    // МНОЖЕСТВЕННЫЙ ПОИСК
    // ================================================================

    private suspend fun buildMultiQueryGroups(tokens: List<String>) {
        try {
            val settings = withContext(Dispatchers.IO) { settingsRepo.load() }
            val queryGroups = mutableListOf<QueryGroup>()

            tokens.forEachIndexed { idx, token ->
                val (prefix, orderIds) = withContext(Dispatchers.IO) {
                    resolveTokenToOrderIds(token, settings)
                }

                orderIds.forEach { ensureOrderSamplesLoaded(it) }

                val variants = orderIds.mapNotNull { orderId ->
                    val info = orderInfoById[orderId] ?: return@mapNotNull null
                    val group = state.groupById(orderId.toString())
                        ?: return@mapNotNull null
                    val found = group.rows.count { it.found }
                    QueryVariant(
                        areaTitle = info.areaTitle,
                        orderTitle = info.orderTitle,
                        groupId = group.id,
                        foundCount = found,
                        totalCount = group.rows.size
                    )
                }

                val selectedArea = state.selectedArea
                val isForeign = selectedArea != null &&
                        variants.isNotEmpty() &&
                        variants.none { it.areaTitle == selectedArea }

                queryGroups.add(
                    QueryGroup(
                        id = "q${idx + 1}",
                        query = token,
                        prefix = prefix,
                        variants = variants,
                        isForeignArea = isForeign
                    )
                )
            }

            withContext(Dispatchers.Main) {
                state.setQueryGroups(queryGroups)
            }
        } catch (e: Exception) {
            _message.value = "Ошибка множественного поиска: ${e.message}"
        }
    }

    private suspend fun resolveTokenToOrderIds(
        token: String,
        settings: ImportSettings
    ): Pair<String?, List<Long>> {
        val prefix = extractLatinPrefix(token)
        val number = if (prefix != null) token.removePrefix(prefix).trim() else token
        val query = number.ifEmpty { token }

        val allIds = repo.findOrderIdsByQuery(query)

        if (prefix == null) {
            return null to allIds
        }

        val matchingAreas = settings.areaPrefixes
            .filterValues { prefixes ->
                prefixes.any { it.equals(prefix, ignoreCase = true) }
            }
            .keys

        val filtered = allIds.filter { orderId ->
            val info = orderInfoById[orderId] ?: return@filter false
            info.areaTitle in matchingAreas
        }

        return prefix to filtered
    }

    private fun extractLatinPrefix(token: String): String? {
        val letters = token.takeWhile { it.isLetter() && it.code < 128 }
        return letters.ifEmpty { null }?.uppercase()
    }

    // ================================================================
    // ДЕЙСТВИЯ НАД ПРОБАМИ
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

    fun undo() {
        state.undo()
        persistAll()
    }

    fun redo() {
        state.redo()
        persistAll()
    }

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
    // СОХРАНЕНИЕ
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