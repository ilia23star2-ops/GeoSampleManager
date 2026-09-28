package com.example.geosamplemanager.ui.screens

/**
 * FIX 5.9-stats-search: withSearch.
 *
 * FIX 5.9-stats-order-status:
 *  - OrderStatus enum + computeOrderStatus — статус наряда с учётом
 *    ошибок импорта и отложенных.
 *  - sortOrdersByStatus — готовые сверху, пустые внизу.
 */

enum class StatsFilter(val title: String) {
    ALL("Все"),
    FOUND("Найдено"),
    NOT_FOUND("Не найдено")
}

/**
 * FIX 5.9-stats-order-status: статус наряда для иконки-кружка и
 * сортировки в дереве.
 *
 * EMPTY       — в наряде нет проб.
 * NOT_STARTED — есть пробы, но ни одна не отмечена.
 * IN_PROGRESS — часть отмечена, ещё есть неотложенные.
 * NEEDS_REVIEW — отмечено всё, кроме отложенных. Осталось проверить.
 * READY       — все активные (не ошибки) отмечены.
 */
enum class OrderStatus(val title: String) {
    EMPTY("Пустой"),
    NOT_STARTED("Не начат"),
    IN_PROGRESS("В работе"),
    NEEDS_REVIEW("Проверить"),
    READY("Готов")
}

/**
 * FIX 5.9-stats-order-status:
 * Логика статуса:
 *  - ошибки импорта не портят картину (вычитаются из total);
 *  - если отмечено всё, кроме отложенных — NEEDS_REVIEW;
 *  - иначе — IN_PROGRESS.
 */
fun computeOrderStatus(stats: GroupStats): OrderStatus {
    val active = stats.total - stats.errors
    return when {
        stats.total == 0 -> OrderStatus.EMPTY
        active <= 0 -> OrderStatus.READY
        stats.found >= active -> OrderStatus.READY
        stats.found == 0 -> OrderStatus.NOT_STARTED
        stats.found + stats.postponed >= active -> OrderStatus.NEEDS_REVIEW
        else -> OrderStatus.IN_PROGRESS
    }
}

/**
 * FIX 5.9-stats-order-status:
 * Сортировка нарядов внутри участка — готовые сверху, пустые внизу.
 * Внутри одной группы — по номеру.
 */
fun sortOrdersByStatus(orders: List<StatsOrderUi>): List<StatsOrderUi> {
    val orderRank = mapOf(
        OrderStatus.READY to 0,
        OrderStatus.NEEDS_REVIEW to 1,
        OrderStatus.IN_PROGRESS to 2,
        OrderStatus.NOT_STARTED to 3,
        OrderStatus.EMPTY to 4
    )
    return orders.sortedWith(
        compareBy(
            { orderRank[computeOrderStatus(it.stats)] ?: 5 },
            { it.orderNumber }
        )
    )
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
 * FIX 5.9-stats-order-status: скрыть готовые наряды.
 */
fun StatsData.withHideReady(hideReady: Boolean): StatsData {
    if (!hideReady) return this
    val filteredAreas = areas.mapNotNull { area ->
        val orders = area.orders.filter {
            computeOrderStatus(it.stats) != OrderStatus.READY
        }
        if (orders.isEmpty()) null
        else area.copy(orders = orders, stats = combineStats(orders.map { it.stats }))
    }
    return copy(areas = filteredAreas)
}

fun StatsData.withSearch(query: String): StatsData {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    val filteredAreas = areas.mapNotNull { area ->
        val matchesArea = area.areaName.lowercase().contains(q)
        val matchingOrders = area.orders.filter { order ->
            matchesArea || order.orderNumber.lowercase().contains(q)
        }
        if (matchingOrders.isEmpty()) null
        else area.copy(
            orders = matchingOrders,
            stats = combineStats(matchingOrders.map { it.stats })
        )
    }
    return copy(areas = filteredAreas)
}

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
