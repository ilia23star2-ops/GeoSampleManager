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
 * FIX 5.9-edit-add-sample/2 — findCommonWellPrefix, suggest*, findConflict.
 * FIX 5.9-edit-save-guard — saveSample проверяет № по БД.
 * FIX 5.9-edit-multiselect — режим выделения, selectedIds.
 * FIX 5.9-edit-mass-ops — MassEditFields, applyMassEdit, deleteSelected.
 *
 * FIX 5.9-edit-mass-ops/2 (hotfix):
 *  - applyMassEditToRow синхронизирует status и weightControl:
 *    CONTROL → weightControl = true; NORMAL/BLANK → weightControl = false.
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

    fun findOrder(orderId: Long): EditOrderUi? {
        areas.forEach { area ->
            area.orders.firstOrNull { it.orderId == orderId }?.let { return it }
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

enum class EditFilter(val title: String) {
    FOUND("Найденные"),
    NOT_FOUND("Не найденные"),
    POSTPONED("Отложенные"),
    WEIGHT_CONTROL("Весовой контроль"),
    BLANK("Холостые"),
    ERRORS("Ошибки")
}

data class ShiftedSample(
    val sampleId: String,
    val newSampleNumber: String
)

data class InsertPlan(
    val insertIndex: Int,
    val newNumberInWell: Int,
    val shifted: List<ShiftedSample>
)

data class SampleConflict(
    val existingSampleNumber: String,
    val existingNumberInWell: Int,
    val existingIntervalFrom: String,
    val existingIntervalTo: String
)

data class MassEditFields(
    val characteristic: String? = null,
    val type: SampleType? = null,
    val status: SampleStatus? = null
) {
    val hasAny: Boolean
        get() = characteristic != null || type != null || status != null
}

// ====================================================================
// Чистые функции — тестируются отдельно.
// ====================================================================

internal fun applyMultiselectToggle(
    current: Set<String>,
    sampleId: String
): Set<String> =
    if (sampleId in current) current - sampleId else current + sampleId

internal fun computeSelectionLabel(count: Int): String = when {
    count <= 0 -> ""
    count % 10 == 1 && count % 100 != 11 -> "$count проба"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count пробы"
    else -> "$count проб"
}

/**
 * FIX 5.9-edit-mass-ops/2:
 * Применить mass-edit к одной строке.
 *
 * status и weightControl синхронизированы:
 *  - status = CONTROL → weightControl = true;
 *  - status = NORMAL или BLANK → weightControl = false;
 *  - status = null → weightControl не трогаем.
 *
 * controlWeight (число кг) не трогаем — пользователь вводит вручную
 * в сверке через WeightDialog.
 */
internal fun applyMassEditToRow(
    row: SampleRow,
    fields: MassEditFields
): SampleRow {
    val newWeightControl = when (fields.status) {
        SampleStatus.CONTROL -> true
        SampleStatus.NORMAL, SampleStatus.BLANK -> false
        null -> row.weightControl
    }
    return row.copy(
        characteristic = fields.characteristic ?: row.characteristic,
        type = fields.type ?: row.type,
        status = fields.status ?: row.status,
        weightControl = newWeightControl
    )
}

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
                .sortedWith(
                    compareBy(
                        { it.wellNumber },
                        { extractNumberInWell(it.sampleNumber, it.wellNumber) }
                    )
                )
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

internal fun findCommonWellPrefix(samples: List<SampleRow>): String {
    val wells = samples.map { it.wellNumber }
        .filter { it.isNotBlank() }
        .distinct()
    if (wells.isEmpty()) return ""

    val first = wells.first()
    var matchLen = first.length
    wells.forEach { w ->
        var i = 0
        while (i < matchLen && i < w.length && first[i] == w[i]) i++
        matchLen = i
    }

    var letterEnd = 0
    while (letterEnd < matchLen && first[letterEnd].isLetter()) letterEnd++
    return first.substring(0, letterEnd)
}

internal fun suggestIntervalFrom(
    wellRows: List<SampleRow>,
    newNumberInWell: Int
): String {
    val before = wellRows
        .filter { it.numberInWell < newNumberInWell && !it.isBlank }
        .maxByOrNull { it.numberInWell } ?: return ""
    val to = before.intervalTo
    return if (to == "—" || to.isBlank()) "" else to
}

internal fun suggestNextSampleNumberInWell(wellRows: List<SampleRow>): Int {
    if (wellRows.isEmpty()) return 1
    return (wellRows.maxOf { it.numberInWell }) + 1
}

internal fun findConflict(
    orderSamples: List<SampleRow>,
    candidateSampleNumber: String
): SampleConflict? {
    val hit = orderSamples.firstOrNull {
        it.sampleNumber == candidateSampleNumber
    } ?: return null
    return SampleConflict(
        existingSampleNumber = hit.sampleNumber,
        existingNumberInWell = hit.numberInWell,
        existingIntervalFrom = hit.intervalFrom,
        existingIntervalTo = hit.intervalTo
    )
}

internal fun planInsertPosition(
    wellNumber: String,
    newSampleNumber: String,
    existingSamples: List<SampleRow>
): InsertPlan {
    val sorted = existingSamples.sortedBy { it.numberInWell }

    if (sorted.isEmpty()) {
        val num = parseSuffixNumber(wellNumber, newSampleNumber) ?: 1
        return InsertPlan(insertIndex = 0, newNumberInWell = num, shifted = emptyList())
    }

    val newNum = parseSuffixNumber(wellNumber, newSampleNumber)
    val suffixLen = detectSuffixLength(
        sampleNumber = sorted.first().sampleNumber,
        wellNumber = wellNumber,
        fallbackSampleNumber = newSampleNumber
    )

    if (newNum == null) {
        val maxNum = sorted.maxOf { it.numberInWell }
        return InsertPlan(
            insertIndex = sorted.size,
            newNumberInWell = maxNum + 1,
            shifted = emptyList()
        )
    }

    val existingNums = sorted.map { it.numberInWell }
    val minNum = existingNums.min()
    val maxNum = existingNums.max()

    if (newNum in existingNums) {
        val insertIndex = sorted.indexOfFirst { it.numberInWell == newNum }
        val shifted = sorted.drop(insertIndex).map { s ->
            val newNumberInWell = s.numberInWell + 1
            val newSN = wellNumber + newNumberInWell.toString().padStart(suffixLen, '0')
            ShiftedSample(sampleId = s.id, newSampleNumber = newSN)
        }
        return InsertPlan(insertIndex, newNum, shifted)
    }

    if (newNum > maxNum) {
        return InsertPlan(sorted.size, newNum, emptyList())
    }

    if (newNum < minNum) {
        return InsertPlan(0, newNum, emptyList())
    }

    val insertIndex = sorted.indexOfFirst { it.numberInWell > newNum }
        .let { if (it < 0) sorted.size else it }
    return InsertPlan(insertIndex, newNum, emptyList())
}

internal fun parseSuffixNumber(wellNumber: String, sampleNumber: String): Int? {
    if (wellNumber.isNotEmpty() && sampleNumber.startsWith(wellNumber)) {
        val suffix = sampleNumber.removePrefix(wellNumber)
        return suffix.toIntOrNull()
    }
    val trailing = sampleNumber.takeLastWhile { it.isDigit() }
    return trailing.toIntOrNull()
}

internal fun detectSuffixLength(
    sampleNumber: String,
    wellNumber: String,
    fallbackSampleNumber: String
): Int {
    if (wellNumber.isNotEmpty() && sampleNumber.startsWith(wellNumber)) {
        val suffix = sampleNumber.removePrefix(wellNumber)
        if (suffix.isNotEmpty() && suffix.all { it.isDigit() }) return suffix.length
    }
    if (wellNumber.isNotEmpty() && fallbackSampleNumber.startsWith(wellNumber)) {
        val suffix = fallbackSampleNumber.removePrefix(wellNumber)
        if (suffix.isNotEmpty()) return suffix.length
    }
    return 2
}

// ====================================================================
// ViewModel
// ====================================================================

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    private val _rawTree = MutableStateFlow<EditTreeData?>(null)
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

    private val _multiselectMode = MutableStateFlow(false)
    val multiselectMode: StateFlow<Boolean> = _multiselectMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

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

        if (q.isNotBlank()) {
            val areaIds = filtered.areas.map { it.areaId }.toSet()
            val orderIds = filtered.areas
                .flatMap { area -> area.orders.map { it.orderId } }
                .toSet()
            _expandedAreaIds.value = areaIds
            _expandedOrderIds.value = orderIds
        }

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

    fun setSearchQuery(text: String) {
        _searchQuery.value = text
        _selectedIds.value = emptySet()
        rebuildFilteredTree()
    }

    fun toggleFilter(filter: EditFilter) {
        _activeFilters.value = if (filter in _activeFilters.value)
            _activeFilters.value - filter
        else
            _activeFilters.value + filter
        _selectedIds.value = emptySet()
        rebuildFilteredTree()
    }

    fun clearFilters() {
        _activeFilters.value = emptySet()
        _selectedIds.value = emptySet()
        rebuildFilteredTree()
    }

    fun enterMultiselect(initialId: String? = null) {
        _multiselectMode.value = true
        _selectedSampleId.value = null
        _selectedSample.value = null
        _selectedIds.value = if (initialId != null) setOf(initialId) else emptySet()
    }

    fun exitMultiselect() {
        _multiselectMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(sampleId: String) {
        _selectedIds.value = applyMultiselectToggle(_selectedIds.value, sampleId)
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun applyMassEdit(fields: MassEditFields) {
        val ids = _selectedIds.value
        if (ids.isEmpty() || !fields.hasAny) return

        viewModelScope.launch {
            try {
                val rows = ids.mapNotNull { id -> _rawTree.value?.findSample(id) }
                if (rows.isEmpty()) return@launch

                val updated = rows.map { applyMassEditToRow(it, fields) }

                withContext(Dispatchers.IO) { repo.saveRows(updated) }
                _message.value = "Изменено проб: ${updated.size}"
                exitMultiselect()
            } catch (e: Exception) {
                _message.value = "Ошибка изменения: ${e.message}"
            }
        }
    }

    fun deleteSelected(renumber: Boolean) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return

        viewModelScope.launch {
            var deleted = 0
            var failed = 0
            try {
                for (idStr in ids) {
                    val id = idStr.toLongOrNull() ?: continue
                    try {
                        withContext(Dispatchers.IO) {
                            repo.deleteSampleWithRenumber(id, renumber)
                        }
                        deleted++
                    } catch (_: Exception) {
                        failed++
                    }
                }
                exitMultiselect()
                _message.value = if (failed == 0) {
                    "Удалено проб: $deleted"
                } else {
                    "Удалено: $deleted, ошибок: $failed"
                }
            } catch (e: Exception) {
                _message.value = "Ошибка удаления: ${e.message}"
            }
        }
    }

    fun selectSample(sampleId: String?) {
        _selectedSampleId.value = sampleId
        _selectedSample.value = sampleId?.let { _tree.value?.findSample(it) }
    }

    fun toggleArea(areaId: Long) {
        exitMultiselect()
        _expandedAreaIds.value = if (areaId in _expandedAreaIds.value)
            _expandedAreaIds.value - areaId
        else
            _expandedAreaIds.value + areaId
    }

    fun toggleOrder(orderId: Long) {
        exitMultiselect()
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

    fun saveSample(row: SampleRow) {
        val old = _rawTree.value?.findSample(row.id)
        viewModelScope.launch {
            try {
                val orderId = old?.groupId?.toLongOrNull()
                    ?: _rawTree.value?.areas
                        ?.flatMap { it.orders }
                        ?.firstOrNull { o -> o.samples.any { it.id == row.id } }
                        ?.orderId

                if (orderId != null &&
                    (old == null || row.sampleNumber != old.sampleNumber)
                ) {
                    val conflict = withContext(Dispatchers.IO) {
                        repo.findSampleByOrderAndNumber(orderId, row.sampleNumber)
                    }
                    if (conflict != null && conflict.id.toString() != row.id) {
                        _message.value = "№ ${row.sampleNumber} уже " +
                                "занят другой пробой в этом наряде"
                        return@launch
                    }
                }

                withContext(Dispatchers.IO) { repo.saveRows(listOf(row)) }
                _message.value = "Проба сохранена"
            } catch (e: Exception) {
                _message.value = humanSaveError(e)
            }
        }
    }

    fun findConflictForEdit(rowId: String, sampleNumber: String): Boolean {
        val tree = _rawTree.value ?: return false
        tree.areas.forEach { area ->
            area.orders.forEach { order ->
                order.samples.forEach { s ->
                    if (s.id != rowId && s.sampleNumber == sampleNumber) return true
                }
            }
        }
        return false
    }

    private fun humanSaveError(e: Exception): String {
        val m = e.message ?: return "Ошибка сохранения"
        return if (m.contains("UNIQUE constraint failed", ignoreCase = true)) {
            "Такой № пробы уже есть в этом наряде"
        } else {
            "Ошибка сохранения: $m"
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

    fun wellsInOrder(orderId: Long): List<String> {
        val order = _rawTree.value?.findOrder(orderId) ?: return emptyList()
        return order.samples.map { it.wellNumber }.distinct().sorted()
    }

    fun commonPrefix(orderId: Long): String {
        val order = _rawTree.value?.findOrder(orderId) ?: return ""
        return findCommonWellPrefix(order.samples)
    }

    fun suggestSampleNumber(wellNumber: String, orderId: Long): String {
        val order = _rawTree.value?.findOrder(orderId)
            ?: return if (wellNumber.isBlank()) "" else wellNumber + "01"
        val wellRows = order.samples.filter { it.wellNumber == wellNumber }
        if (wellRows.isEmpty()) {
            return if (wellNumber.isBlank()) "" else wellNumber + "01"
        }
        val next = suggestNextSampleNumberInWell(wellRows)
        val suffixLen = detectSuffixLength(
            sampleNumber = wellRows.first().sampleNumber,
            wellNumber = wellNumber,
            fallbackSampleNumber = wellRows.first().sampleNumber
        )
        return wellNumber + next.toString().padStart(suffixLen, '0')
    }

    fun suggestIntervalFrom(wellNumber: String, orderId: Long): String {
        val order = _rawTree.value?.findOrder(orderId) ?: return ""
        val wellRows = order.samples
            .filter { it.wellNumber == wellNumber && !it.isBlank }
        if (wellRows.isEmpty()) return ""
        val maxN = wellRows.maxOf { it.numberInWell }
        return suggestIntervalFrom(wellRows, newNumberInWell = maxN + 1)
    }

    fun findConflictInOrder(orderId: Long, sampleNumber: String): SampleConflict? {
        val order = _rawTree.value?.findOrder(orderId) ?: return null
        return findConflict(order.samples, sampleNumber)
    }

    fun addSample(
        orderId: Long,
        wellNumber: String,
        sampleNumber: String,
        intervalFrom: Double?,
        intervalTo: Double?,
        weight: Double?,
        characteristic: String,
        type: SampleType,
        status: SampleStatus
    ) {
        if (wellNumber.isBlank() || sampleNumber.isBlank()) {
            _message.value = "Заполните № скважины и № пробы"
            return
        }

        if (status != SampleStatus.BLANK) {
            if (intervalFrom == null || intervalTo == null) {
                _message.value = "Заполните интервал"
                return
            }
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val orderSamples = repo.getSamplesForOrderList(orderId)
                    val wellSamples = orderSamples
                        .filter { it.wellNumber == wellNumber }
                        .sortedBy { it.serialNumber }

                    val wellRows = wellSamples.map { e ->
                        e.toRow(groupId = orderId.toString())
                    }

                    val plan = planInsertPosition(
                        wellNumber = wellNumber,
                        newSampleNumber = sampleNumber,
                        existingSamples = wellRows
                    )

                    val isBlankStatus = status == SampleStatus.BLANK
                    val intervalStep: Double? =
                        if (!isBlankStatus &&
                            intervalFrom != null && intervalTo != null
                        ) {
                            intervalTo - intervalFrom
                        } else null

                    val shiftPlans = buildShiftPlans(plan, wellRows, intervalStep)

                    val maxSerial = orderSamples.maxOfOrNull { it.serialNumber } ?: 0

                    val newSample = SampleEntity(
                        orderId = orderId,
                        serialNumber = maxSerial + 1,
                        sampleNumber = sampleNumber,
                        wellNumber = wellNumber,
                        intervalFrom = if (isBlankStatus) null else intervalFrom,
                        intervalTo = if (isBlankStatus) null else intervalTo,
                        weight = weight,
                        sampleType = type.dbCode,
                        status = status.dbCode,
                        materialDesc = characteristic
                            .takeIf { it.isNotBlank() && it != "—" }
                    )

                    val sampleShiftPlans = shiftPlans.mapNotNull { sp ->
                        val id = sp.sampleId.toLongOrNull()
                            ?: return@mapNotNull null
                        id to sp.newSampleNumber
                    }

                    repo.addSampleWithShift(newSample, sampleShiftPlans)
                }

                val hadShift = planShouldShift(sampleNumber, wellNumber, orderId)
                val shiftWord = if (hadShift) " (номера и интервалы сдвинуты)" else ""
                _message.value = "Проба $sampleNumber добавлена$shiftWord"
            } catch (e: Exception) {
                _message.value = humanSaveError(e)
            }
        }
    }

    private fun planShouldShift(
        sampleNumber: String,
        wellNumber: String,
        orderId: Long
    ): Boolean {
        val order = _rawTree.value?.findOrder(orderId) ?: return false
        val wellSamples = order.samples.filter { it.wellNumber == wellNumber }
        if (wellSamples.isEmpty()) return false
        val newNum = parseSuffixNumber(wellNumber, sampleNumber) ?: return false
        return wellSamples.any { it.numberInWell == newNum }
    }
}

internal fun buildShiftPlans(
    plan: InsertPlan,
    wellRows: List<SampleRow>,
    intervalStep: Double?
): List<ShiftPlan> {
    val rowsById = wellRows.associateBy { it.id }
    return plan.shifted.mapNotNull { s ->
        val old = rowsById[s.sampleId] ?: return@mapNotNull null

        val newFrom: Double?
        val newTo: Double?
        if (intervalStep != null && !old.isBlank) {
            newFrom = old.intervalFrom.toDoubleOrNull()?.plus(intervalStep)
            newTo = old.intervalTo.toDoubleOrNull()?.plus(intervalStep)
        } else {
            newFrom = old.intervalFrom.toDoubleOrNull()
            newTo = old.intervalTo.toDoubleOrNull()
        }

        ShiftPlan(
            sampleId = s.sampleId,
            newSampleNumber = s.newSampleNumber,
            newIntervalFrom = newFrom,
            newIntervalTo = newTo
        )
    }
}

data class ShiftPlan(
    val sampleId: String,
    val newSampleNumber: String,
    val newIntervalFrom: Double?,
    val newIntervalTo: Double?
)