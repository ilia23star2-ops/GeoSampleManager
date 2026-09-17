package com.example.geosamplemanager.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.geosamplemanager.data.voice.AnswerReason
import com.example.geosamplemanager.data.voice.AnswerState

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

/**
 * FIX 5.8.9e-2-fix-2: что именно совпало при поиске.
 *   • WELL   — нашли скважину (совпал wellNumber).
 *   • SAMPLE — нашли пробу (совпал sampleNumber или суффикс).
 *   • NONE   — не определено.
 *
 * Нужно, чтобы индикатор показывал то, что искал пользователь,
 * а не всегда пробу.
 */
enum class MatchedKind {
    NONE,
    WELL,
    SAMPLE
}

data class MatchInfo(
    val reason: AnswerReason,
    val display: String = "",
    val areaTitles: List<String> = emptyList(),
    val orderTitles: List<String> = emptyList(),
    /** FIX 5.8.9e-2-fix-2: что совпало — скважина или проба. */
    val matchedKind: MatchedKind = MatchedKind.NONE,
    /** Номер совпавшей сущности (wellNumber или sampleNumber). */
    val matchedValue: String? = null
) {
    val state: AnswerState get() = reason.state
    val isIdle: Boolean get() = reason == AnswerReason.IDLE_WAITING
    val isUnique: Boolean get() = reason == AnswerReason.OK_SINGLE
    val isAttention: Boolean get() = state == AnswerState.ATTENTION
    val isError: Boolean get() = state == AnswerState.ERROR
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
    val serialNumber: Int = 0,
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

    data class DeleteRow(
        val groupId: String,
        val rowIndex: Int,
        val row: SampleRow,
        val recalc: Boolean,
        val before: List<WellCellSnapshot>,
        val after: List<WellCellSnapshot>
    ) : UndoAction()

    data class BulkRowsChange(
        val groupId: String,
        val before: List<SampleRow>,
        val after: List<SampleRow>,
        val label: String
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

private fun matchesQuery(row: SampleRow, q: String, filterMode: Boolean): Boolean {
    val well = normalizeNumber(row.wellNumber)
    val sample = normalizeNumber(row.sampleNumber)
    if (well == q || sample == q) return true
    if (filterMode && sample.startsWith(q)) return true
    return false
}

fun filterByQuery(
    groups: List<SampleGroup>,
    query: String,
    filterMode: Boolean = false
): List<SampleGroup> {
    val q = normalizeNumber(query)
    if (q.isBlank()) return groups
    return groups.mapNotNull { group ->
        val filtered = group.rows.filter { row -> matchesQuery(row, q, filterMode) }
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

/**
 * FIX 5.8.9e-2-fix-2: определить, что совпало при поиске.
 *
 * Приоритет: точное совпадение wellNumber → WELL. Точное совпадение
 * sampleNumber → SAMPLE. Ничего не совпало (суффикс/fuzzy) → SAMPLE
 * по умолчанию (показываем первую найденную пробу).
 *
 * @return matchedKind + оригинальное значение (wellNumber или sampleNumber).
 */
private fun detectMatch(
    q: String,
    matching: List<SampleGroup>
): Pair<MatchedKind, String?> {
    var wellMatch: String? = null
    var sampleMatch: String? = null

    matching.forEach { g ->
        g.rows.forEach { row ->
            val wellDigits = normalizeNumber(row.wellNumber)
            val sampleDigits = normalizeNumber(row.sampleNumber)
            if (wellMatch == null && wellDigits == q) {
                wellMatch = row.wellNumber
            }
            if (sampleMatch == null && sampleDigits == q) {
                sampleMatch = row.sampleNumber
            }
        }
    }

    return when {
        wellMatch != null -> MatchedKind.WELL to wellMatch
        sampleMatch != null -> MatchedKind.SAMPLE to sampleMatch
        else -> {
            val firstSample = matching.firstOrNull()?.rows?.firstOrNull()?.sampleNumber
            MatchedKind.SAMPLE to firstSample
        }
    }
}

fun analyzeMatch(
    query: String, selectedArea: String?, selectedOrder: String?,
    allGroups: List<SampleGroup>
): MatchInfo {
    val q = normalizeNumber(query)
    if (q.isBlank()) return MatchInfo(AnswerReason.IDLE_WAITING)

    val filterMode = selectedArea != null && selectedOrder != null

    val matching = allGroups.filter { g ->
        g.rows.any { row -> matchesQuery(row, q, filterMode) }
    }
    if (matching.isEmpty()) return MatchInfo(AnswerReason.NOT_FOUND)

    val (kind, value) = detectMatch(q, matching)

    if (matching.size == 1) {
        val g = matching.first()
        val display = "${g.areaTitle} / ${g.orderTitle}"
        val reason = when {
            selectedArea != null && g.areaTitle != selectedArea ->
                AnswerReason.FOUND_OTHER_AREA
            selectedOrder != null && g.orderTitle != selectedOrder ->
                AnswerReason.FOUND_OTHER_ORDER
            else -> AnswerReason.OK_SINGLE
        }
        return MatchInfo(
            reason = reason,
            display = display,
            areaTitles = listOf(g.areaTitle),
            orderTitles = listOf(g.orderTitle),
            matchedKind = kind,
            matchedValue = value
        )
    }

    val areaTitles = matching.map { it.areaTitle }.distinct()
    val orderTitles = matching.map { "${it.areaTitle} / ${it.orderTitle}" }.distinct()
    val display = orderTitles.joinToString(", ")

    val reason = if (areaTitles.size > 1) {
        AnswerReason.FOUND_MULTIPLE_AREA
    } else {
        AnswerReason.FOUND_MULTIPLE
    }

    return MatchInfo(
        reason = reason,
        display = display,
        areaTitles = areaTitles,
        orderTitles = orderTitles,
        matchedKind = kind,
        matchedValue = value
    )
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

// ====================================================================
// Множественный поиск (5.11)
// ====================================================================

data class QueryGroup(
    val id: String,
    val query: String,
    val prefix: String?,
    val variants: List<QueryVariant>,
    val isForeignArea: Boolean
) {
    val variantCount: Int get() = variants.size
    val isUnique: Boolean get() = variants.size == 1
    val uniqueVariant: QueryVariant? get() = variants.singleOrNull()
    val isFound: Boolean get() = variants.isNotEmpty()

    val orderNumbersLabel: String
        get() = variants.map { it.orderTitle.removePrefix("Наряд №").trim() }
            .distinct()
            .joinToString(", ")

    val areaTitlesLabel: String
        get() = variants.map { it.areaTitle }.distinct().joinToString(", ")
}

data class QueryVariant(
    val areaTitle: String,
    val orderTitle: String,
    val groupId: String,
    val foundCount: Int,
    val totalCount: Int
)

/**
 * FIX 5.8.9e-2-fix-2: строка индикатора для одного запроса мультипоиска.
 *
 * Изменения против 5.8.9e-2:
 *   • Поле [sampleNumber] заменено на [answerValue] + [answerKind].
 *   • Теперь в строке показываем то, что искали: скважину или пробу.
 *
 * @property index          — номер запроса (1..5).
 * @property query          — исходный токен из поля поиска.
 * @property orderTitle     — наряд, если ответ единственный.
 * @property areaTitle      — участок, если ответ единственный.
 * @property answerKind     — WELL / SAMPLE / NONE.
 * @property answerValue    — оригинальный номер (wellNumber или sampleNumber).
 * @property isMultiple     — найдено ≥2 нарядов с этим номером.
 * @property isForeignArea  — единственный ответ — в другом участке.
 */
data class QuickAnswer(
    val index: Int,
    val query: String,
    val orderTitle: String?,
    val areaTitle: String? = null,
    val answerKind: MatchedKind = MatchedKind.NONE,
    val answerValue: String? = null,
    val isMultiple: Boolean,
    val isForeignArea: Boolean = false
)