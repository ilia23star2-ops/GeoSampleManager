package com.example.geosamplemanager.ui.screens

/**
 * FIX 5.9-stats-screen:
 * Модели экрана «Статистика».
 *
 * Дерево: участок → наряд → проба.
 * Фильтры: Все / Найдено / Не найдено — глобальные чипы сверху.
 *
 * Переиспользуем GroupStats, SampleGroup, SampleRow из
 * ReconciliationModels — консистентность с экраном сверки.
 */

enum class StatsFilter(val title: String) {
    ALL("Все"),
    FOUND("Найдено"),
    NOT_FOUND("Не найдено")
}

data class StatsData(
    val totals: GroupStats,
    val areas: List<StatsAreaUi>,
    val filter: StatsFilter = StatsFilter.ALL
)

data class StatsAreaUi(
    val areaId: Long,
    val areaName: String,
    val stats: GroupStats,
    val orders: List<StatsOrderUi>
)

data class StatsOrderUi(
    val orderId: Long,
    val orderNumber: String,
    val group: SampleGroup,
    val stats: GroupStats
)

/**
 * Плоский список для LazyColumn.
 * Собирается на экране из дерева + Set<Long> раскрытых id.
 */
sealed interface StatsItem {
    val key: String

    data class AreaHeader(val area: StatsAreaUi, val expanded: Boolean) : StatsItem {
        override val key: String get() = "a_${area.areaId}"
    }

    data class OrderHeader(
        val areaId: Long,
        val order: StatsOrderUi,
        val expanded: Boolean
    ) : StatsItem {
        override val key: String get() = "o_${order.orderId}"
    }

    data class SampleRowItem(val orderId: Long, val row: SampleRow) : StatsItem {
        override val key: String get() = "r_${row.id}"
    }

    data class TableHeadItem(val orderId: Long) : StatsItem {
        override val key: String get() = "t_$orderId"
    }
}

fun buildStatsItems(
    data: StatsData,
    expandedAreaIds: Set<Long>,
    expandedOrderIds: Set<Long>
): List<StatsItem> {
    val result = ArrayList<StatsItem>(64)
    for (area in data.areas) {
        val areaExpanded = area.areaId in expandedAreaIds
        result.add(StatsItem.AreaHeader(area, areaExpanded))
        if (!areaExpanded) continue
        for (order in area.orders) {
            val orderExpanded = order.orderId in expandedOrderIds
            result.add(StatsItem.OrderHeader(area.areaId, order, orderExpanded))
            if (!orderExpanded) continue
            result.add(StatsItem.TableHeadItem(order.orderId))
            for (row in order.group.rows) {
                result.add(StatsItem.SampleRowItem(order.orderId, row))
            }
        }
    }
    return result
}

/**
 * Применить фильтр к дереву.
 * Оставляет только пробы, соответствующие фильтру, и пересчитывает
 * агрегаты. Участки/наряды без подходящих проб — выпадают.
 */
fun StatsData.withFilter(filter: StatsFilter): StatsData {
    if (filter == StatsFilter.ALL) return copy(filter = filter)
    val filteredAreas = areas.mapNotNull { area ->
        val orders = area.orders.mapNotNull { order ->
            val rows = order.group.rows.filter { row ->
                when (filter) {
                    StatsFilter.ALL -> true
                    StatsFilter.FOUND -> row.found
                    StatsFilter.NOT_FOUND -> !row.found
                }
            }
            if (rows.isEmpty()) null
            else {
                val newGroup = order.group.copy(rows = rows)
                order.copy(group = newGroup, stats = calculateGroupStats(newGroup))
            }
        }
        if (orders.isEmpty()) null
        else area.copy(orders = orders, stats = combineStats(orders.map { it.stats }))
    }
    val totals = combineStats(filteredAreas.map { it.stats })
    return copy(areas = filteredAreas, totals = totals, filter = filter)
}

/**
 * Суммирование GroupStats.
 */
fun combineStats(list: List<GroupStats>): GroupStats {
    var total = 0; var found = 0; var notFound = 0
    var blanks = 0; var weightControls = 0; var postponed = 0; var errors = 0
    list.forEach { s ->
        total += s.total
        found += s.found
        notFound += s.notFound
        blanks += s.blanks
        weightControls += s.weightControls
        postponed += s.postponed
        errors += s.errors
    }
    return GroupStats(total, found, notFound, blanks, weightControls, postponed, errors)
}
