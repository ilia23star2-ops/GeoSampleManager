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
 * FIX 5.9-edit-viewmodel (заход 1/6):
 * ViewModel вкладки «Редактирование».
 *
 * Функции захода:
 *  - реактивная загрузка дерева участок → наряд → проба (Flow из Room);
 *  - выбор одной пробы для редактирования;
 *  - сохранение правки одной пробы;
 *  - удаление пробы (с/без пересчёта номеров в скважине);
 *  - развёрнутость дерева (участки/наряды).
 *
 * Массовые операции, добавление пробы — в следующих заходах.
 */

data class EditTreeData(
    val areas: List<EditAreaUi>
) {
    /**
     * Найти пробу по id во всём дереве. Возвращает null, если нет.
     * Чистая функция — тестируется в EditViewModelTest.
     */
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
 * Построение дерева для редактора.
 *
 * Чистая функция (использует только mapper buildSampleGroup).
 * Тестируется отдельно в EditViewModelTest.
 *
 * Сортировка:
 *  - участки — по названию;
 *  - наряды — по номеру (лексикографически);
 *  - пробы — по serialNumber.
 *
 * Наряды без участка и пробы без наряда игнорируются (обычно такого
 * нет — FK, но на всякий).
 */
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

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    private val _tree = MutableStateFlow<EditTreeData?>(null)
    val tree: StateFlow<EditTreeData?> = _tree.asStateFlow()

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
            }.collect { newTree ->
                _tree.value = newTree
                refreshSelectedSample(newTree)
                pruneExpanded(newTree)
            }
        }
    }

    /**
     * Если выбранная проба изменилась в БД (правка/удаление) —
     * пересобрать selectedSample. Если проба пропала — снять выбор.
     */
    private fun refreshSelectedSample(tree: EditTreeData) {
        val selId = _selectedSampleId.value ?: return
        val updated = tree.findSample(selId)
        _selectedSample.value = updated
        if (updated == null) {
            _selectedSampleId.value = null
        }
    }

    /**
     * После изменения БД убрать из развёрнутости id участков и нарядов,
     * которых больше нет.
     */
    private fun pruneExpanded(tree: EditTreeData) {
        val areaIds = tree.areas.map { it.areaId }.toSet()
        val orderIds = tree.areas
            .flatMap { area -> area.orders.map { it.orderId } }
            .toSet()
        _expandedAreaIds.value = _expandedAreaIds.value intersect areaIds
        _expandedOrderIds.value = _expandedOrderIds.value intersect orderIds
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
    // Сохранение правки одной пробы
    // ================================================================

    /**
     * Сохранить правку одной пробы.
     *
     * Использует repo.saveRows(listOf(row)) — там внутри транзакция,
     * copy полей поверх существующей сущности, updateAll.
     *
     * После записи Room сам эмиттит новый Flow — дерево и выбранная
     * проба обновятся через subscribeToDb без ручного вмешательства.
     */
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

    // ================================================================
    // Удаление
    // ================================================================

    /**
     * Удалить пробу.
     *
     * @param renumber true — пересчитать номера оставшихся проб в
     *                 скважине удалённой; false — оставить как есть.
     */
    fun deleteSample(sampleId: String, renumber: Boolean) {
        val id = sampleId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.deleteSampleWithRenumber(id, renumber)
                }
                // Проба исчезнет из Flow, refreshSelectedSample снимет
                // выбор. Но подстрахуемся — сразу снимем выделение, если
                // удалили выбранную.
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