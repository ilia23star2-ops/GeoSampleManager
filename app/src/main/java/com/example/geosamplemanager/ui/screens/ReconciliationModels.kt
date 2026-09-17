package com.example.geosamplemanager.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.geosamplemanager.data.voice.AnswerReason
import com.example.geosamplemanager.data.voice.AnswerState
import com.example.geosamplemanager.data.voice.UnifiedMatchKind
import com.example.geosamplemanager.data.voice.UnifiedSearch
import com.example.geosamplemanager.data.voice.UnifiedSearchResult
import com.example.geosamplemanager.data.voice.VoiceSampleHit

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

typealias MatchedKind = UnifiedMatchKind

data class MatchInfo(
    val reason: AnswerReason,
    val display: String = "",
    val areaTitles: List<String> = emptyList(),
    val orderTitles: List<String> = emptyList(),
    val matchedKind: UnifiedMatchKind = UnifiedMatchKind.NONE,
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

private fun matchesQuery(
    row: SampleRow,
    group: SampleGroup,
    q: String,
    filterMode: Boolean,
    selectedArea: String?,
    selectedOrder: String?
): Boolean {
    val well = normalizeNumber(row.wellNumber)
    val sample = normalizeNumber(row.sampleNumber)

    if (well == q || sample == q) return true

    if (filterMode &&
        group.areaTitle == selectedArea &&
        group.orderTitle == selectedOrder &&
        sample.startsWith(q)
    ) return true

    return false
}

fun filterByQuery(
    groups: List<SampleGroup>,
    query: String,
    selectedArea: String? = null,
    selectedOrder: String? = null
): List<SampleGroup> {
    val q = normalizeNumber(query)
    if (q.isBlank()) return groups
    val filterMode = selectedArea != null && selectedOrder != null
    return groups.mapNotNull { group ->
        val filtered = group.rows.filter { row ->
            matchesQuery(row, group, q, filterMode, selectedArea, selectedOrder)
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

private fun toVoiceHits(group: SampleGroup): List<VoiceSampleHit> {
    val orderNumber = group.orderTitle
        .removePrefix("Наряд №")
        .removePrefix("Наряд ")
        .trim()
    val orderIdLong = group.id.toLongOrNull() ?: return emptyList()
    return group.rows.mapNotNull { row ->
        val sid = row.id.toLongOrNull() ?: return@mapNotNull null
        VoiceSampleHit(
            sampleId = sid,
            sampleNumber = row.sampleNumber,
            wellNumber = row.wellNumber,
            orderId = orderIdLong,
            orderNumber = orderNumber,
            areaTitle = group.areaTitle
        )
    }
}

fun analyzeMatch(
    query: String, selectedArea: String?, selectedOrder: String?,
    allGroups: List<SampleGroup>
): MatchInfo {
    val q = normalizeNumber(query)
    if (q.isBlank()) return MatchInfo(AnswerReason.IDLE_WAITING)

    val filterMode = selectedArea != null && selectedOrder != null

    if (filterMode) {
        return analyzeMatchFilterMode(q, selectedArea!!, selectedOrder!!, allGroups)
    }

    val allHits = allGroups.flatMap { toVoiceHits(it) }
    if (allHits.isEmpty()) return MatchInfo(AnswerReason.NOT_FOUND)

    val result = UnifiedSearch.search(allHits, listOf(q), filterMode = false)
    return when (result) {
        UnifiedSearchResult.NotFound -> MatchInfo(AnswerReason.NOT_FOUND)
        is UnifiedSearchResult.Found -> buildMatchInfo(result, selectedArea, selectedOrder)
    }
}

private fun analyzeMatchFilterMode(
    q: String,
    selectedArea: String,
    selectedOrder: String,
    allGroups: List<SampleGroup>
): MatchInfo {
    val matching = allGroups.mapNotNull { group ->
        val rows = group.rows.filter { row ->
            matchesQuery(row, group, q, true, selectedArea, selectedOrder)
        }
        if (rows.isEmpty()) null else group.copy(rows = rows)
    }
    if (matching.isEmpty()) return MatchInfo(AnswerReason.NOT_FOUND)

    val exactGroups = matching.filter { g ->
        g.rows.any { row ->
            normalizeNumber(row.wellNumber) == q || normalizeNumber(row.sampleNumber) == q
        }
    }
    val exactInSelected = exactGroups.filter {
        it.areaTitle == selectedArea && it.orderTitle == selectedOrder
    }

    val reason: AnswerReason = when {
        exactGroups.isEmpty() -> AnswerReason.OK_SINGLE
        exactInSelected.isNotEmpty() -> AnswerReason.OK_SINGLE
        exactGroups.any { it.areaTitle != selectedArea } -> AnswerReason.FOUND_OTHER_AREA
        else -> AnswerReason.FOUND_OTHER_ORDER
    }

    val areaTitles = matching.map { it.areaTitle }.distinct()
    val orderTitles = matching.map { "${it.areaTitle} / ${it.orderTitle}" }.distinct()

    val primary = exactGroups.firstOrNull() ?: matching.first()
    val primaryRow = primary.rows.first()
    val pk: UnifiedMatchKind = when {
        normalizeNumber(primaryRow.wellNumber) == q -> UnifiedMatchKind.WELL
        normalizeNumber(primaryRow.sampleNumber) == q -> UnifiedMatchKind.SAMPLE
        normalizeNumber(primaryRow.sampleNumber).startsWith(q) -> UnifiedMatchKind.SAMPLE
        normalizeNumber(primaryRow.wellNumber).startsWith(q) -> UnifiedMatchKind.WELL
        else -> UnifiedMatchKind.NONE
    }
    val pv: String? = when (pk) {
        UnifiedMatchKind.WELL -> primaryRow.wellNumber
        UnifiedMatchKind.SAMPLE -> primaryRow.sampleNumber
        UnifiedMatchKind.NONE -> null
    }

    return MatchInfo(
        reason = reason,
        display = orderTitles.joinToString(", "),
        areaTitles = areaTitles,
        orderTitles = orderTitles,
        matchedKind = pk,
        matchedValue = pv
    )
}

private fun buildMatchInfo(
    result: UnifiedSearchResult.Found,
    selectedArea: String?,
    selectedOrder: String?
): MatchInfo {
    val hits = result.hits
    val orderFull = hits
        .map { "${it.areaTitle} / Наряд №${it.orderNumber}" }
        .distinct()
    val areaTitles = hits.map { it.areaTitle }.distinct()

    val reason: AnswerReason = when {
        orderFull.size == 1 -> {
            val area = hits.first().areaTitle
            val orderNumber = hits.first().orderNumber
            when {
                selectedArea != null && area != selectedArea ->
                    AnswerReason.FOUND_OTHER_AREA
                selectedOrder != null && "Наряд №$orderNumber" != selectedOrder ->
                    AnswerReason.FOUND_OTHER_ORDER
                else -> AnswerReason.OK_SINGLE
            }
        }
        areaTitles.size > 1 -> AnswerReason.FOUND_MULTIPLE_AREA
        else -> AnswerReason.FOUND_MULTIPLE
    }

    return MatchInfo(
        reason = reason,
        display = orderFull.joinToString(", "),
        areaTitles = areaTitles,
        orderTitles = orderFull,
        matchedKind = result.matchedKind,
        matchedValue = result.matchedValue
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

data class QuickAnswer(
    val index: Int,
    val query: String,
    val orderTitle: String?,
    val areaTitle: String? = null,
    val answerKind: UnifiedMatchKind = UnifiedMatchKind.NONE,
    val answerValue: String? = null,
    val isMultiple: Boolean,
    val isForeignArea: Boolean = false
)