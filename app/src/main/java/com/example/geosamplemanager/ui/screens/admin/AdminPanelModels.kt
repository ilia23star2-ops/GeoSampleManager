package com.example.geosamplemanager.ui.screens.admin

import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventLevel
import com.example.geosamplemanager.data.stats.OrderWorkStatus
import com.example.geosamplemanager.data.stats.TabKind

/**
 * FIX 5.10-stat-admin-ui-1:
 * UI-модели экрана админа. Иммутабельные, без зависимостей от Android
 * и Room. Строятся из сущностей stats.db в AdminPanelAggregator.
 *
 * FIX 5.10-stat-admin-ui-5a:
 *  - DayView.unfinishedOrders — незавершённые наряды дня (§7.6).
 *    Отсортированы по % found убыв → время убыв → orderId.
 */

/** Полный снапшот дня для отображения. */
data class DayView(
    val date: String,
    val sessions: List<SessionView>,
    val totals: DayTotals,
    val tabUsage: List<TabUsage>,
    val unfinishedOrders: List<OrderWorkView>,
    val events: List<EventView>,
    val problems: ProblemsView
)

/** Агрегаты дня. */
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

/** Одна сессия с визитами и работой с нарядами. */
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

/** Визит вкладки внутри сессии. */
data class TabVisitView(
    val tab: TabKind,
    val fromTs: Long,
    val toTs: Long?,
    val durationSec: Int
)

/** Работа с нарядом внутри сессии. */
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
    /** Прогресс в процентах (0..100). 0 — если нет проб. */
    val percent: Int
        get() = if (totalSamples <= 0) 0 else (foundSamples * 100) / totalSamples

    /** Метка статуса для UI: «в поиске» / «в сверке» / «готов». */
    val statusLabel: String
        get() = when (status) {
            OrderWorkStatus.IN_PROGRESS -> "в поиске"
            OrderWorkStatus.HALF_DONE -> "в сверке"
            OrderWorkStatus.DONE -> "готов"
        }
}

/** Сводка по вкладке: сколько секунд суммарно и доля от общего. */
data class TabUsage(
    val tab: TabKind,
    val totalSec: Int,
    val percent: Int
)

/** Событие для ленты. */
data class EventView(
    val atTs: Long,
    val level: EventLevel,
    val category: EventCategory,
    val summary: String,
    val detailsJson: String?
)

/**
 * Проблемы БД. В первой версии — нули, реальная загрузка будет
 * подключена отдельно (через DatabaseRepository.runDiagnostics).
 */
data class ProblemsView(
    val orphanOrders: Int,
    val orphanSamples: Int,
    val brokenPhotos: Int
) {
    val isEmpty: Boolean
        get() = orphanOrders == 0 && orphanSamples == 0 && brokenPhotos == 0
}