package com.example.geosamplemanager.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Stable
class ReconciliationState(initialGroups: List<SampleGroup>) {

    private val _groups = mutableStateListOf<SampleGroup>().apply { addAll(initialGroups) }
    val groups: List<SampleGroup> get() = _groups

    var query by mutableStateOf("")
    var selectedArea by mutableStateOf<String?>(null)
    var selectedOrder by mutableStateOf<String?>(null)
    var activeFilters by mutableStateOf(setOf<ResultFilter>())

    var showCharacteristic by mutableStateOf(true)
    var filtersExpanded by mutableStateOf(true)
    var showLegend by mutableStateOf(false)

    var globalBlankWeight by mutableStateOf(BlankWeightSettings())
    private val _blankWeightByOrder = mutableStateMapOf<String, BlankWeightSettings>()

    var globalWeightControlStep by mutableStateOf(5)
    private val _weightControlStepByOrder = mutableStateMapOf<String, Int>()

    /**
     * Состояние раскрытия групп. По умолчанию — раскрыты.
     * Хранится в state, чтобы плоский LazyColumn знал, что показывать.
     */
    private val _expandedGroups = mutableStateMapOf<String, Boolean>()

    private val undoStack = mutableStateListOf<UndoAction>()
    private val redoStack = mutableStateListOf<UndoAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    // ================================================================
    // Производные
    // ================================================================

    val hasSelection: Boolean get() = selectedArea != null || selectedOrder != null

    val availableAreas: List<String>
        get() = groups.map { it.areaTitle }.distinct().sorted()

    val visibleGroups: List<SampleGroup>
        get() {
            val base = when {
                query.isBlank() && selectedOrder != null ->
                    filterByOrder(filterByArea(groups, selectedArea), selectedOrder)
                query.isBlank() -> emptyList()
                else -> groups
            }
            val byQuery = filterByQuery(base, query)
            val byStatus = applyFilters(byQuery, activeFilters)
            return sortGroupsByRelevance(byStatus, selectedArea, selectedOrder)
        }

    val currentStatistics: GroupStats
        get() {
            val groupsToCount = if (selectedOrder != null) {
                groups.filter {
                    it.orderTitle == selectedOrder &&
                            (selectedArea == null || it.areaTitle == selectedArea)
                }
            } else {
                visibleGroups
            }
            return calculateOverallStats(groupsToCount)
        }

    val matchInfo: MatchInfo
        get() = analyzeMatch(query, selectedArea, selectedOrder, groups)

    val availableOrders: List<String>
        get() = ordersWithSamples(selectedArea, groups)

    val currentBlankWeight: BlankWeightSettings
        get() {
            val order = selectedOrder ?: return globalBlankWeight
            return _blankWeightByOrder[order] ?: globalBlankWeight
        }

    fun blankWeightFor(orderTitle: String): BlankWeightSettings =
        _blankWeightByOrder[orderTitle] ?: globalBlankWeight

    fun weightControlStepFor(orderTitle: String): Int =
        _weightControlStepByOrder[orderTitle] ?: globalWeightControlStep

    fun isUsingGlobal(orderTitle: String): Boolean =
        !_blankWeightByOrder.containsKey(orderTitle)
                && !_weightControlStepByOrder.containsKey(orderTitle)

    fun setGroups(newGroups: List<SampleGroup>) {
        _groups.clear()
        _groups.addAll(newGroups)
        undoStack.clear()
        redoStack.clear()
        _expandedGroups.clear()
    }

    /**
     * Быстрый поиск строки по id — без flatMap на каждом вызове.
     */
    fun rowById(rowId: String): SampleRow? {
        var i = 0
        while (i < _groups.size) {
            val rows = _groups[i].rows
            var j = 0
            while (j < rows.size) {
                if (rows[j].id == rowId) return rows[j]
                j++
            }
            i++
        }
        return null
    }

    // ================================================================
    // Раскрытие групп
    // ================================================================

    fun isGroupExpanded(groupId: String): Boolean = _expandedGroups[groupId] ?: true

    fun toggleGroupExpanded(groupId: String) {
        _expandedGroups[groupId] = !isGroupExpanded(groupId)
    }

    // ================================================================
    // Статус-бар
    // ================================================================

    fun describeTopUndoAction(): String {
        val a = undoStack.lastOrNull() ?: return ""
        return describe(a)
    }

    fun describeTopRedoAction(): String {
        val a = redoStack.lastOrNull() ?: return ""
        return describe(a)
    }

    private fun describe(a: UndoAction): String = when (a) {
        is UndoAction.SetFound -> {
            val n = findSampleNumber(a.rowId) ?: "—"
            if (a.newValue) "Отметить: $n" else "Снять отметку: $n"
        }
        is UndoAction.SetWeight -> {
            val n = findSampleNumber(a.rowId) ?: "—"
            val v = a.newValue?.let { "$it кг" } ?: "—"
            "Вес: $v ($n)"
        }
        is UndoAction.SetControlWeight -> {
            val n = findSampleNumber(a.rowId) ?: "—"
            val v = a.newValue?.let { "$it кг" } ?: "—"
            "ВК: $v ($n)"
        }
        is UndoAction.SetPostponed -> {
            val n = findSampleNumber(a.rowId) ?: "—"
            if (a.newValue) "Отложить: $n" else "Снять отложенную: $n"
        }
        is UndoAction.SetWeightControlFlag -> {
            val n = findSampleNumber(a.rowId) ?: "—"
            if (a.newValue) "Поставить ВК: $n" else "Снять ВК: $n"
        }
        is UndoAction.SetAllFound -> {
            val value = a.changes.firstOrNull()?.second ?: true
            val verb = if (value) "Отметить все" else "Снять все отметки"
            "$verb (${a.changes.size})"
        }
        is UndoAction.DeleteRow -> "Удалить: ${a.row.sampleNumber}"
    }

    private fun findSampleNumber(rowId: String): String? {
        _groups.forEach { g ->
            g.rows.firstOrNull { it.id == rowId }?.let { return it.sampleNumber }
        }
        return null
    }

    // ================================================================
    // Настройки
    // ================================================================

    fun applyBlankSettingsForOrder(
        orderTitle: String,
        settings: BlankWeightSettings,
        weightControlStep: Int
    ): Int {
        _blankWeightByOrder[orderTitle] = settings
        _weightControlStepByOrder[orderTitle] = weightControlStep
        return applyBlankWeightsInternal(orderTitle, settings)
    }

    fun resetBlankSettingsToGlobal(orderTitle: String): Int {
        _blankWeightByOrder.remove(orderTitle)
        _weightControlStepByOrder.remove(orderTitle)
        return applyBlankWeightsInternal(orderTitle, globalBlankWeight)
    }

    private fun applyBlankWeightsInternal(
        orderTitle: String,
        settings: BlankWeightSettings
    ): Int {
        if (settings.mode == BlankWeightMode.MANUAL) return 0

        val gi = _groups.indexOfFirst { it.orderTitle == orderTitle }
        if (gi < 0) return 0

        val group = _groups[gi]
        var changed = 0
        val newRows = group.rows.map { row ->
            if (row.isBlank && row.weight == null) {
                val w = when (settings.mode) {
                    BlankWeightMode.FIXED -> settings.fixedValue
                    BlankWeightMode.AVERAGE -> calculateAverageNeighborWeight(group, row.id)
                    BlankWeightMode.MANUAL -> null
                }
                if (w != null && w > 0) {
                    changed++
                    row.copy(weight = w)
                } else row
            } else row
        }
        _groups[gi] = group.copy(rows = newRows)
        return changed
    }

    fun resetBlankWeightsForOrder(orderTitle: String): Int {
        val gi = _groups.indexOfFirst { it.orderTitle == orderTitle }
        if (gi < 0) return 0

        val group = _groups[gi]
        var changed = 0
        val newRows = group.rows.map { row ->
            if (row.isBlank && (row.weight != null || row.found)) {
                changed++
                row.copy(weight = null, found = false)
            } else row
        }
        _groups[gi] = group.copy(rows = newRows)
        return changed
    }

    // ================================================================
    // Действия над пробами
    // ================================================================

    fun toggleFound(rowId: String) {
        val (gi, ri) = findRow(rowId) ?: return
        val row = _groups[gi].rows[ri]
        val newValue = !row.found
        replaceRow(gi, ri, row.copy(found = newValue))
        pushUndo(UndoAction.SetFound(rowId, row.found, newValue))
    }

    fun setFound(rowId: String, value: Boolean) {
        val (gi, ri) = findRow(rowId) ?: return
        val row = _groups[gi].rows[ri]
        if (row.found == value) return
        replaceRow(gi, ri, row.copy(found = value))
        pushUndo(UndoAction.SetFound(rowId, row.found, value))
    }

    fun setControlWeightAndFound(rowId: String, weight: Double) {
        val (gi, ri) = findRow(rowId) ?: return
        val row = _groups[gi].rows[ri]
        val oldCW = row.controlWeight
        val oldFound = row.found
        replaceRow(gi, ri, row.copy(controlWeight = weight, found = true))
        pushUndo(UndoAction.SetControlWeight(rowId, oldCW, weight))
        if (!oldFound) pushUndo(UndoAction.SetFound(rowId, oldFound, true))
    }

    fun setControlWeight(rowId: String, weight: Double) {
        val (gi, ri) = findRow(rowId) ?: return
        val row = _groups[gi].rows[ri]
        val old = row.controlWeight
        replaceRow(gi, ri, row.copy(controlWeight = weight))
        pushUndo(UndoAction.SetControlWeight(rowId, old, weight))
    }

    fun setBlankWeightAndMarkFound(rowId: String, weight: Double) {
        val (gi, ri) = findRow(rowId) ?: return
        val row = _groups[gi].rows[ri]
        val oldW = row.weight
        val oldFound = row.found
        replaceRow(gi, ri, row.copy(weight = weight, found = true))
        pushUndo(UndoAction.SetWeight(rowId, oldW, weight))
        if (!oldFound) pushUndo(UndoAction.SetFound(rowId, oldFound, true))
    }

    fun setWeight(rowId: String, weight: Double) {
        val (gi, ri) = findRow(rowId) ?: return
        val row = _groups[gi].rows[ri]
        val old = row.weight
        replaceRow(gi, ri, row.copy(weight = weight))
        pushUndo(UndoAction.SetWeight(rowId, old, weight))
    }

    fun setPostponed(rowId: String, value: Boolean) {
        val (gi, ri) = findRow(rowId) ?: return
        val row = _groups[gi].rows[ri]
        if (row.postponed == value) return
        replaceRow(gi, ri, row.copy(postponed = value))
        pushUndo(UndoAction.SetPostponed(rowId, row.postponed, value))
    }

    fun toggleWeightControl(rowId: String): Boolean {
        val (gi, ri) = findRow(rowId) ?: return false
        val row = _groups[gi].rows[ri]
        if (row.isBlank) return false

        val newFlag = !row.weightControl
        val oldFlag = row.weightControl
        val oldFound = row.found

        if (newFlag) {
            replaceRow(gi, ri, row.copy(weightControl = true, found = false, controlWeight = null))
            pushUndo(UndoAction.SetWeightControlFlag(rowId, oldFlag, true))
            if (oldFound) pushUndo(UndoAction.SetFound(rowId, oldFound, false))
        } else {
            replaceRow(gi, ri, row.copy(weightControl = false))
            pushUndo(UndoAction.SetWeightControlFlag(rowId, oldFlag, false))
        }
        return true
    }

    // ================================================================
    // Массовая отметка
    // ================================================================

    sealed class BulkDecision {
        data class WeightControlNeedsWeight(val row: SampleRow) : BulkDecision()
        data class PostponedNeedsAction(val row: SampleRow) : BulkDecision()
    }

    fun collectBulkDecisions(groupId: String): List<BulkDecision> {
        val group = _groups.firstOrNull { it.id == groupId } ?: return emptyList()
        val result = mutableListOf<BulkDecision>()
        group.rows.forEach { row ->
            when {
                row.hasImportError -> Unit
                row.weightControl && row.controlWeight == null ->
                    result.add(BulkDecision.WeightControlNeedsWeight(row))
                row.postponed ->
                    result.add(BulkDecision.PostponedNeedsAction(row))
            }
        }
        return result
    }

    fun applyBulkMarkFound(
        groupId: String,
        weights: Map<String, Double>,
        postponedActions: Map<String, Boolean>
    ): Int {
        val gi = _groups.indexOfFirst { it.id == groupId }
        if (gi < 0) return 0

        val group = _groups[gi]
        val settings = blankWeightFor(group.orderTitle)
        var marked = 0

        val changes = group.rows.map { it.id to it.found }

        val newRows = group.rows.map { row ->
            if (row.hasImportError) return@map row
            if (row.found) return@map row

            if (row.weightControl && row.controlWeight == null) {
                val w = weights[row.id]
                if (w != null && w > 0) {
                    marked++
                    row.copy(controlWeight = w, found = true)
                } else row
            } else if (row.postponed) {
                val doMark = postponedActions[row.id] ?: false
                if (doMark) {
                    marked++
                    row.copy(found = true)
                } else row
            } else if (row.isBlank) {
                if (row.weight != null) {
                    marked++
                    row.copy(found = true)
                } else {
                    val w = when (settings.mode) {
                        BlankWeightMode.FIXED -> settings.fixedValue
                        BlankWeightMode.AVERAGE -> calculateAverageNeighborWeight(group, row.id)
                        BlankWeightMode.MANUAL -> null
                    }
                    if (w != null && w > 0) {
                        marked++
                        row.copy(weight = w, found = true)
                    } else row
                }
            } else {
                marked++
                row.copy(found = true)
            }
        }

        _groups[gi] = group.copy(rows = newRows)
        pushUndo(UndoAction.SetAllFound(groupId, changes))
        return marked
    }

    fun clearAllFound(groupId: String) {
        val gi = _groups.indexOfFirst { it.id == groupId }
        if (gi < 0) return
        val group = _groups[gi]
        val changes = group.rows.map { it.id to it.found }
        val newRows = group.rows.map { it.copy(found = false) }
        _groups[gi] = group.copy(rows = newRows)
        pushUndo(UndoAction.SetAllFound(groupId, changes))
    }

    // ================================================================
    // Удаление
    // ================================================================

    fun deleteRow(rowId: String, recalc: Boolean) {
        val (gi, ri) = findRow(rowId) ?: return
        val group = _groups[gi]
        val row = group.rows[ri]

        val before = snapshotWellCells(group.rows, row.wellNumber)

        val newRows = group.rows.toMutableList().apply { removeAt(ri) }
        val finalRows = if (recalc) renumberWell(newRows, row.wellNumber)
        else recalcNumberInWellOnly(newRows, row.wellNumber)

        _groups[gi] = group.copy(rows = finalRows)

        val after = snapshotWellCells(finalRows, row.wellNumber)

        pushUndo(
            UndoAction.DeleteRow(
                groupId = group.id,
                rowIndex = ri,
                row = row,
                recalc = recalc,
                before = before,
                after = after
            )
        )
    }

    private fun snapshotWellCells(
        rows: List<SampleRow>,
        wellNumber: String
    ): List<WellCellSnapshot> {
        return rows.filter { it.wellNumber == wellNumber }
            .map { WellCellSnapshot(it.id, it.sampleNumber, it.numberInWell) }
    }

    private fun recalcNumberInWellOnly(
        rows: List<SampleRow>, wellNumber: String
    ): List<SampleRow> {
        val indices = rows.indices
            .filter { rows[it].wellNumber == wellNumber }
            .sortedBy { rows[it].numberInWell }

        val newRows = rows.toMutableList()
        indices.forEachIndexed { newIndex, rowIndex ->
            newRows[rowIndex] = newRows[rowIndex].copy(numberInWell = newIndex + 1)
        }
        return newRows
    }

    private fun renumberWell(rows: List<SampleRow>, wellNumber: String): List<SampleRow> {
        val indices = rows.indices
            .filter { rows[it].wellNumber == wellNumber }
            .sortedBy { rows[it].numberInWell }

        if (indices.isEmpty()) return rows

        val firstSample = rows[indices.first()].sampleNumber
        val suffixLen = detectSuffixLength(firstSample, wellNumber)

        val newRows = rows.toMutableList()
        indices.forEachIndexed { newIndex, rowIndex ->
            val newNumberInWell = newIndex + 1
            val newSampleNumber = wellNumber +
                    newNumberInWell.toString().padStart(suffixLen, '0')
            newRows[rowIndex] = newRows[rowIndex].copy(
                sampleNumber = newSampleNumber,
                numberInWell = newNumberInWell
            )
        }
        return newRows
    }

    private fun detectSuffixLength(sampleNumber: String, wellNumber: String): Int {
        if (sampleNumber.startsWith(wellNumber)) {
            val len = sampleNumber.length - wellNumber.length
            if (len > 0) return len
        }
        var i = sampleNumber.length
        while (i > 0 && sampleNumber[i - 1].isDigit()) i--
        return (sampleNumber.length - i).coerceAtLeast(1)
    }

    // ================================================================
    // Undo / Redo
    // ================================================================

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        applyActionReverse(action)
        redoStack.add(action)
        while (redoStack.size > 20) redoStack.removeAt(0)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        applyActionForward(action)
        undoStack.add(action)
        while (undoStack.size > 20) undoStack.removeAt(0)
    }

    private fun applyActionForward(action: UndoAction) {
        when (action) {
            is UndoAction.SetFound -> applyFound(action.rowId, action.newValue)
            is UndoAction.SetWeight -> applyWeight(action.rowId, action.newValue)
            is UndoAction.SetControlWeight -> applyControlWeight(action.rowId, action.newValue)
            is UndoAction.SetPostponed -> applyPostponed(action.rowId, action.newValue)
            is UndoAction.SetWeightControlFlag -> applyWeightControlFlag(action.rowId, action.newValue)
            is UndoAction.SetAllFound -> {
                val gi = _groups.indexOfFirst { it.id == action.groupId }
                if (gi < 0) return
                val oldUniform = action.changes.map { it.second }.distinct()
                val newValue = if (oldUniform.size == 1) !oldUniform.first() else true
                val group = _groups[gi]
                val newRows = group.rows.map { row ->
                    if (action.changes.any { it.first == row.id }) row.copy(found = newValue)
                    else row
                }
                _groups[gi] = group.copy(rows = newRows)
            }
            is UndoAction.DeleteRow -> {
                val gi = _groups.indexOfFirst { it.id == action.groupId }
                if (gi < 0) return
                val group = _groups[gi]
                val newRows = group.rows.toMutableList()
                val idx = newRows.indexOfFirst { it.id == action.row.id }
                if (idx >= 0) newRows.removeAt(idx)
                val afterMap = action.after.associateBy { it.rowId }
                val updated = newRows.map { r ->
                    val a = afterMap[r.id] ?: return@map r
                    r.copy(sampleNumber = a.sampleNumber, numberInWell = a.numberInWell)
                }
                _groups[gi] = group.copy(rows = updated)
            }
        }
    }

    private fun applyActionReverse(action: UndoAction) {
        when (action) {
            is UndoAction.SetFound -> applyFound(action.rowId, action.oldValue)
            is UndoAction.SetWeight -> applyWeight(action.rowId, action.oldValue)
            is UndoAction.SetControlWeight -> applyControlWeight(action.rowId, action.oldValue)
            is UndoAction.SetPostponed -> applyPostponed(action.rowId, action.oldValue)
            is UndoAction.SetWeightControlFlag -> applyWeightControlFlag(action.rowId, action.oldValue)
            is UndoAction.SetAllFound -> {
                val gi = _groups.indexOfFirst { it.id == action.groupId }
                if (gi < 0) return
                val map = action.changes.toMap()
                val group = _groups[gi]
                val newRows = group.rows.map { row ->
                    val old = map[row.id]
                    if (old != null) row.copy(found = old) else row
                }
                _groups[gi] = group.copy(rows = newRows)
            }
            is UndoAction.DeleteRow -> {
                val gi = _groups.indexOfFirst { it.id == action.groupId }
                if (gi < 0) return
                val group = _groups[gi]
                val newRows = group.rows.toMutableList()
                val insertAt = action.rowIndex.coerceIn(0, newRows.size)
                newRows.add(insertAt, action.row)
                val beforeMap = action.before.associateBy { it.rowId }
                val updated = newRows.map { r ->
                    val b = beforeMap[r.id] ?: return@map r
                    r.copy(sampleNumber = b.sampleNumber, numberInWell = b.numberInWell)
                }
                _groups[gi] = group.copy(rows = updated)
            }
        }
    }

    // ================================================================
    // Помощники
    // ================================================================

    private fun findRow(rowId: String): Pair<Int, Int>? {
        _groups.forEachIndexed { gi, g ->
            g.rows.forEachIndexed { ri, r ->
                if (r.id == rowId) return gi to ri
            }
        }
        return null
    }

    private fun replaceRow(groupIndex: Int, rowIndex: Int, newRow: SampleRow) {
        val group = _groups[groupIndex]
        val newRows = group.rows.toMutableList().also { it[rowIndex] = newRow }
        _groups[groupIndex] = group.copy(rows = newRows)
    }

    private fun pushUndo(action: UndoAction) {
        undoStack.add(action)
        while (undoStack.size > 20) undoStack.removeAt(0)
        redoStack.clear()
    }

    private fun applyFound(rowId: String, value: Boolean) {
        val (gi, ri) = findRow(rowId) ?: return
        replaceRow(gi, ri, _groups[gi].rows[ri].copy(found = value))
    }
    private fun applyWeight(rowId: String, value: Double?) {
        val (gi, ri) = findRow(rowId) ?: return
        replaceRow(gi, ri, _groups[gi].rows[ri].copy(weight = value))
    }
    private fun applyControlWeight(rowId: String, value: Double?) {
        val (gi, ri) = findRow(rowId) ?: return
        replaceRow(gi, ri, _groups[gi].rows[ri].copy(controlWeight = value))
    }
    private fun applyPostponed(rowId: String, value: Boolean) {
        val (gi, ri) = findRow(rowId) ?: return
        replaceRow(gi, ri, _groups[gi].rows[ri].copy(postponed = value))
    }
    private fun applyWeightControlFlag(rowId: String, value: Boolean) {
        val (gi, ri) = findRow(rowId) ?: return
        replaceRow(gi, ri, _groups[gi].rows[ri].copy(weightControl = value))
    }
}

// ====================================================================
// Фейковые данные (не используются после подключения Room)
// ====================================================================

fun fakeAreas(): List<String> = listOf("Северный", "Южный", "Западный")

fun initialSampleGroups(): List<SampleGroup> = emptyList()