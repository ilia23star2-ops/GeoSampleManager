package com.example.geosamplemanager.ui.screens.admin

import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventLevel
import com.example.geosamplemanager.data.stats.OrderWorkStatus
import com.example.geosamplemanager.data.stats.TabKind

/**
 * FIX 5.10-stat-admin-ui-1: UI-модели экрана админа.
 * FIX 5.10-stat-admin-ui-5a: DayView.unfinishedOrders.
 * FIX 5.10-stat-admin-v2-nav: TimelineSegment.
 *
 * FIX 5.10-stat-admin-v2-orders-a:
 *  - AdminOrderStatus — статус наряда для таба «Наряды»;
 *  - AdminOrderSummary — строка списка нарядов.
 */

data class DayView(
    val date: String,
    val sessions: List<SessionView>,
    val totals: DayTotals,
    val tabUsage: List<TabUsage>,
    val unfinishedOrders: List<OrderWorkView>,
    val events: List<EventView>,
    val problems: ProblemsView
)

data class DayTotals(
    val activeSec: Int,
    val idleSec: Int,
    val activePercent: Int,
    val idlePercent: Int,
    val runs: Int,
    val readyOrders: Int,
    val errorsCount: Int,
    val warnsCount: Int
)

data class SessionView(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val activeSec: Int,
    val idleSec: Int,
    val crashFlag: Boolean,
    val visits: List<TabVisitView>,
    val orderWorks: List<OrderWorkView>
)

data class TabVisitView(
    val tab: TabKind,
    val fromTs: Long,
    val toTs: Long?,
    val durationSec: Int
)

data class OrderWorkView(
    val orderId: Long,
    val areaTitle: String,
    val orderTitle: String,
    val startedAt: Long,
    val endedAt: Long?,
    val searchSec: Int,
    val verifySec: Int,
    val status: OrderWorkStatus,
    val totalSamples: Int,
    val foundSamples: Int
) {
    val percent: Int
        get() = if (totalSamples <= 0) 0 else (foundSamples * 100) / totalSamples

    val statusLabel: String
        get() = when (status) {
            OrderWorkStatus.IN_PROGRESS -> "в поиске"
            OrderWorkStatus.HALF_DONE -> "в сверке"
            OrderWorkStatus.DONE -> "готов"
        }
}

data class TabUsage(
    val tab: TabKind,
    val totalSec: Int,
    val percent: Int
)

data class EventView(
    val atTs: Long,
    val level: EventLevel,
    val category: EventCategory,
    val summary: String,
    val detailsJson: String?
)

data class ProblemsView(
    val orphanOrders: Int,
    val orphanSamples: Int,
    val brokenPhotos: Int
) {
    val isEmpty: Boolean
        get() = orphanOrders == 0 && orphanSamples == 0 && brokenPhotos == 0
}

/**
 * FIX 5.10-stat-admin-v2-nav: сегмент таймлайна дня.
 */
data class TimelineSegment(
    val sessionId: Long,
    val fromTs: Long,
    val toTs: Long,
    val crashFlag: Boolean = false
)

/**
 * FIX 5.10-stat-admin-v2-orders-a:
 * Статус наряда для таба «Наряды». Отдельный от OrderWorkStatus:
 *  - OrderWorkStatus — фаза работы (поиск / сверка / done);
 *  - AdminOrderStatus — общий статус по данным двух БД.
 *
 * `NOT_STARTED` — наряд есть в основной БД, но в stats.db по нему
 * нет ни одной записи order_work.
 */
enum class AdminOrderStatus(val label: String) {
    NOT_STARTED("не начат"),
    IN_PROGRESS("в работе"),
    DONE("готов")
}

/**
 * FIX 5.10-stat-admin-v2-orders-a:
 * Одна строка списка нарядов. Собирается из areas + orders
 * (основная БД) и order_work (stats.db).
 *
 * Поля времени:
 *  - null, если order_work по наряду нет — UI показывает «—»;
 *  - сумма по всем записям order_work за всё время.
 */
data class AdminOrderSummary(
    val orderId: Long,
    val areaTitle: String,
    val orderTitle: String,
    val createdDate: Long,
    val totalSamples: Int,
    val foundSamples: Int,
    val status: AdminOrderStatus,
    /** Секунды в фазе «Поиск» (сумма по всем order_work). null если не работали. */
    val searchSec: Int?,
    /** Секунды в фазе «Сверка». null если не работали. */
    val verifySec: Int?
) {
    val percent: Int
        get() = if (totalSamples <= 0) 0 else (foundSamples * 100) / totalSamples

    val hasWork: Boolean
        get() = searchSec != null || verifySec != null

    val totalSec: Int
        get() = (searchSec ?: 0) + (verifySec ?: 0)
}