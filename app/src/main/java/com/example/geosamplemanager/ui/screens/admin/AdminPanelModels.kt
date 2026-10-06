package com.example.geosamplemanager.ui.screens.admin

import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventLevel
import com.example.geosamplemanager.data.stats.OrderWorkStatus
import com.example.geosamplemanager.data.stats.TabKind

/**
 * FIX 5.10-stat-admin-ui-1:
 * UI-модели экрана админа. Иммутабельные, без зависимостей от Android
 * и Room.
 *
 * FIX 5.10-stat-admin-ui-5a:
 *  - DayView.unfinishedOrders — незавершённые наряды дня (§7.6).
 *
 * FIX 5.10-stat-admin-v2-nav:
 *  - TimelineSegment — сегмент таймлайна дня (сессия).
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
 * FIX 5.10-stat-admin-v2-nav:
 * Сегмент таймлайна дня. Пока — только сессии (полосы на шкале).
 * В пачке v2-details сюда добавятся подсегменты по вкладкам.
 *
 * Время — абсолютные timestamp в миллисекундах. UI сам переводит
 * их в координаты X через dayBounds(date).
 */
data class TimelineSegment(
    val sessionId: Long,
    val fromTs: Long,
    val toTs: Long,
    val crashFlag: Boolean = false
)