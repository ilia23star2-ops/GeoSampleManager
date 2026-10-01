package com.example.geosamplemanager.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.9-edit-screen-search (заход 2/6):
 * ViewModel вкладки «Редактирование» с поиском и фильтрами.
 *
 * Функции:
 *  - реактивное дерево участок → наряд → проба;
 *  - выбор одной пробы;
 *  - сохранение правки;
 *  - удаление (с/без пересчёта);
 *  - поиск по номеру пробы, номеру скважины, характеристике;
 *  - фильтры-чипы: найдены / не найдены / отложены / ВК / холостые / ошибки;
 *  - авто-разворот дерева при непустом поиске.
 */

data class EditTreeData(
    val areas: List<EditAreaUi>
) {
    fun findSample(sampleId: String): SampleRow? {
        areas.forEach { area ->
            area.orders.forEach { order ->
                order.samples.firstOrNull { it.id == sampleId }?.let { return it }
            }
        }
        return null
    }
}

data class EditAreaUi(
    val areaId: Long,
    val areaName: String,
    val orders: List<EditOrderUi>
)

data class EditOrderUi(
    val orderId: Long,
    val orderNumber: String,
    val orderTitle: String,
    val samples: List<SampleRow>
)

/**
 * Фильтры-чипы вкладки «Редактирование».
 */
enum class EditFilter(val title: String) {
    FOUND("Найденные"),
    NOT_FOUND("Не найденные"),
    POSTPONED("Отложенные"),
    WEIGHT_CONTROL("Весовой контроль"),
    BLANK("Холостые"),
    ERRORS("Ошибки")
}

// ====================================================================
// Чистые функции — тестируются отдельно.
// ====================================================================

internal fun buildEditTree(
    areas: List<AreaEntity>,
    orders: List<OrderEntity>,
    samples: List<SampleEntity>
): EditTreeData {
    val ordersByArea = orders.groupBy { it.areaId }
    val samplesByOrder = samples.groupBy { it.orderId }

    val areaUis = areas.map { area ->
        val areaOrders = ordersByArea[area.id].orEmpty()
            .sortedBy { it.orderNumber }
        val orderUis = areaOrders.map { order ->
            val orderSamples = samplesByOrder[order.id].orEmpty()
                .sortedBy { it.serialNumber }
            val group = buildSampleGroup(order, area, orderSamples)
            EditOrderUi(
                orderId = order.id,
                orderNumber = order.orderNumber,
                orderTitle = "Наряд №${order.orderNumber}",
                samples = group.rows
            )
        }
        EditAreaUi(
            areaId = area.id,
            areaName = area.areaName,
            orders = orderUis
        )
    }.sortedBy { it.areaName }

    return EditTreeData(areas = areaUis)
}

/**
 * Применить поиск и фильтры к дереву.
 *
 * Поиск: подстрока (содержит) по нормализованному номеру пробы,
 * номеру скважины ИЛИ по характеристике (без регистра).
 *
 * Фильтры: ALL-логика — проба должна удовлетворять ВСЕМ выбранным.
 *
 * Пустые наряды и участки вычищаются.
 */
internal fun applyEditFilters(
    tree: EditTreeData,
    query: String,
    filters: Set<EditFilter>
): EditTreeData {
    val q = query.trim().lowercase()
    val hasQuery = q.isNotEmpty()

    if (!hasQuery && filters.isEmpty()) return tree

    val filteredAreas = tree.areas.mapNotNull { area ->
        val filteredOrders = area.orders.mapNotNull { order ->
            val filteredSamples = order.samples.filter { row ->
                matchesEditQuery(row, q, hasQuery) &&
                        matchesEditFilters(row, filters)
            }
            if (filteredSamples.isEmpty()) null
            else order.copy(samples = filteredSamples)
        }
        if (filteredOrders.isEmpty()) null
        else area.copy(orders = filteredOrders)
    }
    return EditTreeData(areas = filteredAreas)
}

private fun matchesEditQuery(row: SampleRow, q: String, hasQuery: Boolean): Boolean {
    if (!hasQuery) return true
    val norm = normalizeNumber(q)
    if (norm.isNotEmpty()) {
        val sampleDigits = normalizeNumber(row.sampleNumber)
        val wellDigits = normalizeNumber(row.wellNumber)
        if (sampleDigits.contains(norm)) return true
        if (wellDigits.contains(norm)) return true
    }
    // Текстовый поиск по характеристике (без регистра).
    return row.characteristic.lowercase().contains(q)
}

private fun matchesEditFilters(row: SampleRow, filters: Set<EditFilter>): Boolean {
    if (filters.isEmpty()) return true
    return filters.all { f ->
        when (f) {
            EditFilter.FOUND -> row.found
            EditFilter.NOT_FOUND -> !row.found
            EditFilter.POSTPONED -> row.postponed
            EditFilter.WEIGHT_CONTROL -> row.weightControl
            EditFilter.BLANK -> row.isBlank
            EditFilter.ERRORS -> row.hasImportError
        }
    }
}

// ====================================================================
// ViewModel
// ====================================================================

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    /** Сырое дерево — прямо из БД, без фильтров. */
    private val _rawTree = MutableStateFlow<EditTreeData?>(null)

    /** Отфильтрованное дерево — то, что видит UI. */
    private val _tree = MutableStateFlow<EditTreeData?>(null)
    val tree: StateFlow<EditTreeData?> = _tree.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilters = MutableStateFlow<Set<EditFilter>>(emptySet())
    val activeFilters: StateFlow<Set<EditFilter>> = _activeFilters.asStateFlow()

    private val _selectedSampleId = MutableStateFlow<String?>(null)
    val selectedSampleId: StateFlow<String?> = _selectedSampleId.asStateFlow()

    private val _selectedSample = MutableStateFlow<SampleRow?>(null)
    val selectedSample: StateFlow<SampleRow?> = _selectedSample.asStateFlow()

    private val _expandedAreaIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedAreaIds: StateFlow<Set<Long>> = _expandedAreaIds.asStateFlow()

    private val _expandedOrderIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedOrderIds: StateFlow<Set<Long>> = _expandedOrderIds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        subscribeToDb()
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun subscribeToDb() {
        viewModelScope.launch {
            combine(
                repo.getAreasFlow(),
                repo.getAllOrdersFlow(),
                repo.getAllSamplesFlow()
            ) { areas, orders, samples ->
                withContext(Dispatchers.Default) {
                    buildEditTree(areas, orders, samples)
                }
            }.collect { newRawTree ->
                _rawTree.value = newRawTree
                rebuildFilteredTree()
                pruneExpanded(newRawTree)
            }
        }
    }

    private fun rebuildFilteredTree() {
        val raw = _rawTree.value ?: return
        val q = _searchQuery.value
        val filters = _activeFilters.value

        val filtered = if (q.isBlank() && filters.isEmpty()) {
            raw
        } else {
            applyEditFilters(raw, q, filters)
        }
        _tree.value = filtered

        // Авто-разворот при непустом поиске.
        if (q.isNotBlank()) {
            val areaIds = filtered.areas.map { it.areaId }.toSet()
            val orderIds = filtered.areas
                .flatMap { area -> area.orders.map { it.orderId } }
                .toSet()
            _expandedAreaIds.value = areaIds
            _expandedOrderIds.value = orderIds
        }

        // Обновить/снять выбор, если проба пропала из отфильтрованного дерева.
        val selId = _selectedSampleId.value
        if (selId != null) {
            val updated = filtered.findSample(selId)
            if (updated == null) {
                _selectedSampleId.value = null
                _selectedSample.value = null
            } else {
                _selectedSample.value = updated
            }
        }
    }

    private fun pruneExpanded(tree: EditTreeData) {
        val areaIds = tree.areas.map { it.areaId }.toSet()
        val orderIds = tree.areas
            .flatMap { area -> area.orders.map { it.orderId } }
            .toSet()
        _expandedAreaIds.value = _expandedAreaIds.value intersect areaIds
        _expandedOrderIds.value = _expandedOrderIds.value intersect orderIds
    }

    // ================================================================
    // Поиск и фильтры
    // ================================================================

    fun setSearchQuery(text: String) {
        _searchQuery.value = text
        rebuildFilteredTree()
    }

    fun toggleFilter(filter: EditFilter) {
        _activeFilters.value = if (filter in _activeFilters.value)
            _activeFilters.value - filter
        else
            _activeFilters.value + filter
        rebuildFilteredTree()
    }

    fun clearFilters() {
        _activeFilters.value = emptySet()
        rebuildFilteredTree()
    }

    // ================================================================
    // Выбор пробы
    // ================================================================

    fun selectSample(sampleId: String?) {
        _selectedSampleId.value = sampleId
        _selectedSample.value = sampleId?.let { _tree.value?.findSample(it) }
    }

    // ================================================================
    // Развёрнутость дерева
    // ================================================================

    fun toggleArea(areaId: Long) {
        _expandedAreaIds.value = if (areaId in _expandedAreaIds.value)
            _expandedAreaIds.value - areaId
        else
            _expandedAreaIds.value + areaId
    }

    fun toggleOrder(orderId: Long) {
        _expandedOrderIds.value = if (orderId in _expandedOrderIds.value)
            _expandedOrderIds.value - orderId
        else
            _expandedOrderIds.value + orderId
    }

    fun expandAll() {
        val t = _tree.value ?: return
        _expandedAreaIds.value = t.areas.map { it.areaId }.toSet()
        _expandedOrderIds.value = t.areas
            .flatMap { area -> area.orders.map { it.orderId } }
            .toSet()
    }

    fun collapseAll() {
        _expandedAreaIds.value = emptySet()
        _expandedOrderIds.value = emptySet()
    }

    // ================================================================
    // Сохранение / удаление
    // ================================================================

    fun saveSample(row: SampleRow) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.saveRows(listOf(row))
                }
                _message.value = "Проба сохранена"
            } catch (e: Exception) {
                _message.value = "Ошибка сохранения: ${e.message}"
            }
        }
    }

    fun deleteSample(sampleId: String, renumber: Boolean) {
        val id = sampleId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.deleteSampleWithRenumber(id, renumber)
                }
                if (_selectedSampleId.value == sampleId) {
                    _selectedSampleId.value = null
                    _selectedSample.value = null
                }
                _message.value = if (renumber) {
                    "Проба удалена, номера пересчитаны"
                } else {
                    "Проба удалена"
                }
            } catch (e: Exception) {
                _message.value = "Ошибка удаления: ${e.message}"
            }
        }
    }
}