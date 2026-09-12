package com.example.geosamplemanager.ui.screens

import androidx.compose.ui.graphics.Color

/**
 * Модели и вспомогательная логика для экрана «Сверка и поиск».
 *
 * Здесь нет Composable-функций и нет состояния — только:
 *  - UI-модели (SampleGroup, SampleRow, MatchInfo, GroupKind, ...),
 *  - фильтры и сортировка (чистые функции),
 *  - правила анализа запроса (фонарик, уникальность ответа),
 *  - модели Undo-действий.
 *
 * Когда перейдём на Room — эти модели заменятся на «настоящие»
 * сущности, но интерфейс функций останется тот же.
 */

// ====================================================================
// Типы и статусы
// ====================================================================

/**
 * Тип пробы.
 */
enum class SampleType(val title: String) {
    ORDINARY("Рядовая"),
    BLANK("Холостая"),
    CONTROL("Контрольная"),
    ERROR("Ошибка")
}

/**
 * Фильтры результатов (чипы под статистикой).
 */
enum class ResultFilter(val title: String) {
    FOUND("Найдены"),
    NOT_FOUND("Не найдены"),
    BLANK("Холостые"),
    CONTROL_WEIGHT("Весовой"),
    POSTPONED("Отложенные"),
    ERRORS("Ошибки")
}

/**
 * Вид группы в результатах поиска.
 * Определяет подсветку и предупреждения в шапке.
 */
enum class GroupKind {
    CURRENT_ORDER,   // группа выбранного наряда
    SAME_AREA,       // другой наряд того же участка
    OTHER_AREA,      // другой участок
    NEUTRAL          // ничего не выбрано — все наряды равны
}

/**
 * Информация для индикатора-фонарика.
 */
sealed class MatchInfo {
    data object None : MatchInfo()
    data class Unique(val display: String) : MatchInfo()
    data class Multiple(val display: List<String>) : MatchInfo()
}

// ====================================================================
// UI-модели
// ====================================================================

/**
 * Один элемент статистики.
 */
data class StatItem(
    val label: String,
    val value: Int,
    val color: Color? = null
)

/**
 * Группа проб (наряд + его пробы).
 * В реальной БД — наряд с пробами.
 */
data class SampleGroup(
    val id: String,
    val areaTitle: String,
    val orderTitle: String,
    val subtitle: String,
    val rows: List<SampleRow>
)

/**
 * Одна проба.
 *
 * Правила:
 *  - isBlank = true → isControlWeight ВСЕГДА false, postponed ВСЕГДА false.
 *  - postponed ставит рабочий, не импорт.
 *  - found — отметка рабочего о физическом наличии.
 */
data class SampleRow(
    val id: String,
    val groupId: String,
    val wellNumber: String,
    val sampleNumber: String,
    val numberInWell: Int,
    val intervalFrom: String,
    val intervalTo: String,
    val weight: Double?,          // основной вес
    val controlWeight: Double?,   // вес весового контроля
    val type: SampleType,
    val characteristic: String,
    val found: Boolean,
    val postponed: Boolean,
    val isControlWeight: Boolean,
    val hasNote: Boolean,
    val hasPhoto: Boolean,
    val hasImportError: Boolean
) {
    val isBlank: Boolean get() = type == SampleType.BLANK
}

// ====================================================================
// Undo-действия
// ====================================================================

/**
 * Одно действие, которое можно откатить.
 * Каждое хранит старое значение, чтобы undo вернул как было.
 */
sealed class UndoAction {
    data class SetFound(
        val rowId: String,
        val oldValue: Boolean,
        val newValue: Boolean
    ) : UndoAction()

    data class SetWeight(
        val rowId: String,
        val oldValue: Double?,
        val newValue: Double?
    ) : UndoAction()

    data class SetControlWeight(
        val rowId: String,
        val oldValue: Double?,
        val newValue: Double?
    ) : UndoAction()

    data class SetPostponed(
        val rowId: String,
        val oldValue: Boolean,
        val newValue: Boolean
    ) : UndoAction()

    /**
     * Массовое действие. changes — список (rowId, старое значение found)
     * для всех затронутых проб в группе.
     */
    data class SetAllFound(
        val groupId: String,
        val changes: List<Pair<String, Boolean>>
    ) : UndoAction()
}

// ====================================================================
// Логика фильтрации и сортировки
// ====================================================================

/**
 * Убирает всё, кроме цифр. "TST1234" → "1234".
 */
fun normalizeNumber(s: String): String = s.filter { it.isDigit() }

/**
 * Фильтр по участку. Если участок не выбран — возвращаем всё.
 */
fun filterByArea(
    groups: List<SampleGroup>,
    areaTitle: String?
): List<SampleGroup> {
    if (areaTitle == null) return groups
    return groups.filter { it.areaTitle == areaTitle }
}

/**
 * Фильтр по наряду. Если наряд не выбран — возвращаем всё.
 */
fun filterByOrder(
    groups: List<SampleGroup>,
    orderTitle: String?
): List<SampleGroup> {
    if (orderTitle == null) return groups
    return groups.filter { it.orderTitle == orderTitle }
}

/**
 * Фильтр по запросу.
 *  - Пусто → возвращаем как есть.
 *  - Точное совпадение с номером скважины → все пробы этой скважины.
 *  - Иначе — пробы, чей номер начинается с запроса.
 */
fun filterByQuery(
    groups: List<SampleGroup>,
    query: String
): List<SampleGroup> {
    val q = normalizeNumber(query)
    if (q.isBlank()) return groups

    return groups.mapNotNull { group ->
        val filteredRows = group.rows.filter { row ->
            val well = normalizeNumber(row.wellNumber)
            val sample = normalizeNumber(row.sampleNumber)
            well == q || sample.startsWith(q)
        }
        if (filteredRows.isEmpty()) null
        else group.copy(rows = filteredRows)
    }
}

/**
 * Фильтр по статусам (чипы).
 */
fun applyFilters(
    groups: List<SampleGroup>,
    filters: Set<ResultFilter>
): List<SampleGroup> {
    if (filters.isEmpty()) return groups

    return groups.mapNotNull { group ->
        val filteredRows = group.rows.filter { row ->
            filters.all { filter ->
                when (filter) {
                    ResultFilter.FOUND          -> row.found
                    ResultFilter.NOT_FOUND      -> !row.found
                    ResultFilter.BLANK          -> row.isBlank
                    ResultFilter.CONTROL_WEIGHT -> row.isControlWeight
                    ResultFilter.POSTPONED      -> row.postponed
                    ResultFilter.ERRORS         -> row.hasImportError
                }
            }
        }
        if (filteredRows.isEmpty()) null
        else group.copy(rows = filteredRows)
    }
}

/**
 * Определяет вид группы.
 */
fun determineGroupKind(
    group: SampleGroup,
    selectedArea: String?,
    selectedOrder: String?
): GroupKind {
    return when {
        selectedOrder != null && group.orderTitle == selectedOrder -> GroupKind.CURRENT_ORDER
        selectedArea != null && group.areaTitle == selectedArea -> GroupKind.SAME_AREA
        selectedArea != null -> GroupKind.OTHER_AREA
        else -> GroupKind.NEUTRAL
    }
}

/**
 * Приоритетная сортировка:
 *  0 — выбранный наряд
 *  1 — тот же участок
 *  2 — всё остальное
 * Внутри уровня — по алфавиту (участок, наряд).
 */
fun sortGroupsByRelevance(
    groups: List<SampleGroup>,
    selectedArea: String?,
    selectedOrder: String?
): List<SampleGroup> {
    if (selectedArea == null && selectedOrder == null) return groups

    return groups.sortedWith(
        compareBy(
            { group ->
                when {
                    selectedOrder != null && group.orderTitle == selectedOrder -> 0
                    selectedArea != null && group.areaTitle == selectedArea -> 1
                    else -> 2
                }
            },
            { it.areaTitle },
            { it.orderTitle }
        )
    )
}

/**
 * Анализ запроса для фонарика.
 *  - Пусто → None.
 *  - Совпадение ровно в одной группе И она совпадает с выбранным нарядом/участком → Unique.
 *  - Иначе → Multiple (фонарик не горит).
 */
fun analyzeMatch(
    query: String,
    selectedArea: String?,
    selectedOrder: String?,
    allGroups: List<SampleGroup>
): MatchInfo {
    val q = normalizeNumber(query)
    if (q.isBlank()) return MatchInfo.None

    val matching = allGroups.filter { group ->
        group.rows.any { row ->
            val well = normalizeNumber(row.wellNumber)
            val sample = normalizeNumber(row.sampleNumber)
            well == q || sample.startsWith(q)
        }
    }

    if (matching.isEmpty()) return MatchInfo.None

    if (matching.size == 1) {
        val g = matching.first()
        val inSelectedOrder = selectedOrder != null && g.orderTitle == selectedOrder
        val inSelectedArea = selectedOrder == null
                && selectedArea != null
                && g.areaTitle == selectedArea
        val noSelection = selectedArea == null && selectedOrder == null

        return if (inSelectedOrder || inSelectedArea || noSelection) {
            MatchInfo.Unique("${g.areaTitle} / ${g.orderTitle}")
        } else {
            MatchInfo.Multiple(matching.map { "${it.areaTitle} / ${it.orderTitle}" })
        }
    }

    return MatchInfo.Multiple(matching.map { "${it.areaTitle} / ${it.orderTitle}" })
}

/**
 * Список нарядов, у которых есть хотя бы одна проба.
 * Если участок выбран — только его.
 */
fun ordersWithSamples(
    area: String?,
    groups: List<SampleGroup>
): List<String> {
    return groups
        .filter { area == null || it.areaTitle == area }
        .map { it.orderTitle }
        .distinct()
        .sorted()
}

/**
 * Определяет, можно ли отметить пробу как найденную без диалогов.
 * Если у весового контроля или холостой нет веса — вернёт false.
 */
fun canMarkFoundDirectly(row: SampleRow): Boolean {
    if (row.isControlWeight && row.controlWeight == null) return false
    if (row.isBlank && row.weight == null) return false
    return true
}