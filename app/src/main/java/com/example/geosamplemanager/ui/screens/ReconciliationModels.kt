package com.example.geosamplemanager.ui.screens

import androidx.compose.ui.graphics.Color

/**
 * Модели и вспомогательная логика экрана «Сверка и поиск».
 */

// ====================================================================
// Типы и статусы
// ====================================================================

enum class SampleType(val dbCode: String, val title: String) {
    AUGER("auger", "Шнековая"),
    CHANNEL("channel", "Бороздовая"),
    COBRA("cobra", "Кобра"),
    DUPLICATE("duplicate", "Дубликат");

    companion object {
        fun fromDb(code: String?): SampleType =
            values().firstOrNull { it.dbCode == code } ?: AUGER
    }
}

enum class SampleStatus(val dbCode: String, val title: String) {
    NORMAL("normal", "Обычная"),
    BLANK("blank", "Холостая"),
    CONTROL("control", "Весовой контроль");

    companion object {
        fun fromDb(code: String?): SampleStatus =
            values().firstOrNull { it.dbCode == code } ?: NORMAL
    }
}

fun displayType(type: SampleType, status: SampleStatus): String = when (status) {
    SampleStatus.BLANK -> "Холостая"
    SampleStatus.CONTROL -> "Весовой контроль"
    SampleStatus.NORMAL -> type.title
}

enum class ResultFilter(val title: String) {
    FOUND("Найдены"),
    NOT_FOUND("Не найдены"),
    BLANK("Холостые"),
    CONTROL_WEIGHT("Весовой"),
    POSTPONED("Отложенные"),
    ERRORS("Ошибки")
}

enum class GroupKind {
    CURRENT_ORDER,
    SAME_AREA,
    OTHER_AREA,
    NEUTRAL
}

sealed class MatchInfo {
    data object None : MatchInfo()
    data class Unique(val display: String) : MatchInfo()
    data class Multiple(val display: List<String>) : MatchInfo()
}

enum class BlankWeightMode(val title: String) {
    FIXED("Единый вес на наряд"),
    AVERAGE("Среднее соседних проб"),
    MANUAL("Вручную")
}

data class BlankWeightSettings(
    val mode: BlankWeightMode = BlankWeightMode.MANUAL,
    val fixedValue: Double? = null
)

// ====================================================================
// UI-модели
// ====================================================================

data class StatItem(
    val label: String,
    val value: Int,
    val color: Color? = null
)

data class SampleGroup(
    val id: String,
    val areaTitle: String,
    val orderTitle: String,
    val subtitle: String,
    val rows: List<SampleRow>
)

data class SampleRow(
    val id: String,
    val groupId: String,
    val wellNumber: String,
    val sampleNumber: String,
    val numberInWell: Int,
    val intervalFrom: String,
    val intervalTo: String,
    val weight: Double?,
    val controlWeight: Double?,
    val type: SampleType,
    val status: SampleStatus,
    val characteristic: String,
    val found: Boolean,
    val postponed: Boolean,
    val weightControl: Boolean,
    val hasNote: Boolean,
    val hasPhoto: Boolean,
    val hasImportError: Boolean
) {
    val isBlank: Boolean get() = status == SampleStatus.BLANK
    val isControlWeight: Boolean get() = weightControl
}

// ====================================================================
// Статистика
// ====================================================================

data class GroupStats(
    val total: Int = 0,
    val found: Int = 0,
    val notFound: Int = 0,
    val blanks: Int = 0,
    val weightControls: Int = 0,
    val postponed: Int = 0,
    val errors: Int = 0
)

fun calculateGroupStats(group: SampleGroup): GroupStats {
    var total = 0; var found = 0; var notFound = 0
    var blanks = 0; var weightControls = 0; var postponed = 0; var errors = 0
    group.rows.forEach { row ->
        total++
        if (row.found) found++ else notFound++
        if (row.isBlank) blanks++
        if (row.weightControl) weightControls++
        if (row.postponed) postponed++
        if (row.hasImportError) errors++
    }
    return GroupStats(total, found, notFound, blanks, weightControls, postponed, errors)
}

fun calculateOverallStats(groups: List<SampleGroup>): GroupStats {
    var total = 0; var found = 0; var notFound = 0
    var blanks = 0; var weightControls = 0; var postponed = 0; var errors = 0
    groups.forEach { g ->
        g.rows.forEach { row ->
            total++
            if (row.found) found++ else notFound++
            if (row.isBlank) blanks++
            if (row.weightControl) weightControls++
            if (row.postponed) postponed++
            if (row.hasImportError) errors++
        }
    }
    return GroupStats(total, found, notFound, blanks, weightControls, postponed, errors)
}

fun buildGroupSubtitle(group: SampleGroup): String {
    val s = calculateGroupStats(group)
    return "Всего: ${s.total} · Найдено: ${s.found} · Холостых: ${s.blanks} · " +
            "Весовой: ${s.weightControls} · Отложено: ${s.postponed}"
}

fun statsToItems(s: GroupStats): List<StatItem> = listOf(
    StatItem("Всего", s.total),
    StatItem("Найдено", s.found, Color(0xFF2E7D32)),
    StatItem("Не найдено", s.notFound, Color(0xFFC62828)),
    StatItem("Холостые", s.blanks),
    StatItem("Весовой", s.weightControls),
    StatItem("Отложено", s.postponed, Color(0xFF1976D2)),
    StatItem("Ошибки", s.errors, Color(0xFFC62828))
)

// ====================================================================
// Undo-действия
// ====================================================================

/**
 * Снимок одной пробы для восстановления sample_number и numberInWell.
 * Используется в DeleteRow при пересчёте номеров.
 */
data class WellCellSnapshot(
    val rowId: String,
    val sampleNumber: String,
    val numberInWell: Int
)

sealed class UndoAction {
    data class SetFound(val rowId: String, val oldValue: Boolean, val newValue: Boolean) : UndoAction()
    data class SetWeight(val rowId: String, val oldValue: Double?, val newValue: Double?) : UndoAction()
    data class SetControlWeight(val rowId: String, val oldValue: Double?, val newValue: Double?) : UndoAction()
    data class SetPostponed(val rowId: String, val oldValue: Boolean, val newValue: Boolean) : UndoAction()
    data class SetWeightControlFlag(val rowId: String, val oldValue: Boolean, val newValue: Boolean) : UndoAction()
    data class SetAllFound(val groupId: String, val changes: List<Pair<String, Boolean>>) : UndoAction()

    /**
     * Удаление пробы. Полностью восстанавливается при откате:
     *  - сама проба возвращается в группу на исходный индекс,
     *  - поля sample_number / numberInWell всех проб скважины
     *    восстанавливаются из before-снимка.
     */
    data class DeleteRow(
        val groupId: String,
        val rowIndex: Int,
        val row: SampleRow,
        val recalc: Boolean,
        val before: List<WellCellSnapshot>,
        val after: List<WellCellSnapshot>
    ) : UndoAction()
}

// ====================================================================
// Утилиты
// ====================================================================

fun normalizeNumber(s: String): String = s.filter { it.isDigit() }

fun filterByArea(groups: List<SampleGroup>, areaTitle: String?): List<SampleGroup> {
    if (areaTitle == null) return groups
    return groups.filter { it.areaTitle == areaTitle }
}

fun filterByOrder(groups: List<SampleGroup>, orderTitle: String?): List<SampleGroup> {
    if (orderTitle == null) return groups
    return groups.filter { it.orderTitle == orderTitle }
}

fun filterByQuery(groups: List<SampleGroup>, query: String): List<SampleGroup> {
    val q = normalizeNumber(query)
    if (q.isBlank()) return groups
    return groups.mapNotNull { group ->
        val filtered = group.rows.filter { row ->
            val well = normalizeNumber(row.wellNumber)
            val sample = normalizeNumber(row.sampleNumber)
            well == q || sample.startsWith(q)
        }
        if (filtered.isEmpty()) null else group.copy(rows = filtered)
    }
}

fun applyFilters(groups: List<SampleGroup>, filters: Set<ResultFilter>): List<SampleGroup> {
    if (filters.isEmpty()) return groups
    return groups.mapNotNull { group ->
        val filtered = group.rows.filter { row ->
            filters.all { f ->
                when (f) {
                    ResultFilter.FOUND          -> row.found
                    ResultFilter.NOT_FOUND      -> !row.found
                    ResultFilter.BLANK          -> row.isBlank
                    ResultFilter.CONTROL_WEIGHT -> row.isControlWeight
                    ResultFilter.POSTPONED      -> row.postponed
                    ResultFilter.ERRORS         -> row.hasImportError
                }
            }
        }
        if (filtered.isEmpty()) null else group.copy(rows = filtered)
    }
}

fun determineGroupKind(
    group: SampleGroup, selectedArea: String?, selectedOrder: String?
): GroupKind = when {
    selectedOrder != null && group.orderTitle == selectedOrder -> GroupKind.CURRENT_ORDER
    selectedArea != null && group.areaTitle == selectedArea -> GroupKind.SAME_AREA
    selectedArea != null -> GroupKind.OTHER_AREA
    else -> GroupKind.NEUTRAL
}

fun sortGroupsByRelevance(
    groups: List<SampleGroup>, selectedArea: String?, selectedOrder: String?
): List<SampleGroup> {
    if (selectedArea == null && selectedOrder == null) return groups
    return groups.sortedWith(
        compareBy(
            { g ->
                when {
                    selectedOrder != null && g.orderTitle == selectedOrder -> 0
                    selectedArea != null && g.areaTitle == selectedArea -> 1
                    else -> 2
                }
            },
            { it.areaTitle },
            { it.orderTitle }
        )
    )
}

fun analyzeMatch(
    query: String, selectedArea: String?, selectedOrder: String?,
    allGroups: List<SampleGroup>
): MatchInfo {
    val q = normalizeNumber(query)
    if (q.isBlank()) return MatchInfo.None

    val matching = allGroups.filter { g ->
        g.rows.any { row ->
            val well = normalizeNumber(row.wellNumber)
            val sample = normalizeNumber(row.sampleNumber)
            well == q || sample.startsWith(q)
        }
    }
    if (matching.isEmpty()) return MatchInfo.None

    if (matching.size == 1) {
        val g = matching.first()
        val inOrder = selectedOrder != null && g.orderTitle == selectedOrder
        val inArea = selectedOrder == null && selectedArea != null && g.areaTitle == selectedArea
        val noSel = selectedArea == null && selectedOrder == null
        return if (inOrder || inArea || noSel) {
            MatchInfo.Unique("${g.areaTitle} / ${g.orderTitle}")
        } else {
            MatchInfo.Multiple(matching.map { "${it.areaTitle} / ${it.orderTitle}" })
        }
    }
    return MatchInfo.Multiple(matching.map { "${it.areaTitle} / ${it.orderTitle}" })
}

fun ordersWithSamples(area: String?, groups: List<SampleGroup>): List<String> =
    groups
        .filter { area == null || it.areaTitle == area }
        .map { it.orderTitle }
        .distinct()
        .sorted()

fun groupHasChannel(group: SampleGroup): Boolean =
    group.rows.any { it.type == SampleType.CHANNEL }

fun calculateAverageNeighborWeight(group: SampleGroup, blankRowId: String): Double? {
    val idx = group.rows.indexOfFirst { it.id == blankRowId }
    if (idx < 0) return null

    val before = group.rows.take(idx)
        .lastOrNull { !it.isBlank && it.weight != null && !it.hasImportError }
    val after = group.rows.drop(idx + 1)
        .firstOrNull { !it.isBlank && it.weight != null && !it.hasImportError }

    val weights = listOfNotNull(before?.weight, after?.weight)
    return if (weights.isEmpty()) null else weights.average()
}