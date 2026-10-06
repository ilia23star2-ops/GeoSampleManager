package com.example.geosamplemanager.ui.screens.admin

import com.example.geosamplemanager.data.stats.DailySummaryEntity
import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventEntity
import com.example.geosamplemanager.data.stats.EventLevel
import com.example.geosamplemanager.data.stats.OrderWorkEntity
import com.example.geosamplemanager.data.stats.OrderWorkStatus
import com.example.geosamplemanager.data.stats.SessionEntity
import com.example.geosamplemanager.data.stats.TabKind
import com.example.geosamplemanager.data.stats.TabVisitEntity

/**
 * FIX 5.10-stat-admin-ui-1:
 * Чистая логика агрегации дня для админ-панели. Не зависит от Android
 * и Room — тестируется в JVM.
 *
 * FIX 5.10-stat-admin-ui-5a:
 *  - DayView.unfinishedOrders — незавершённые наряды (§7.6);
 *  - сортировка: % found убыв → время последнего действия убыв →
 *    orderId.
 */
object AdminPanelAggregator {

    /** Собрать снапшот дня. */
    fun buildDayView(
        date: String,
        sessions: List<SessionEntity>,
        visitsBySession: Map<Long, List<TabVisitEntity>>,
        orderWorkBySession: Map<Long, List<OrderWorkEntity>>,
        events: List<EventEntity>,
        dailySummary: DailySummaryEntity?,
        problems: ProblemsView
    ): DayView {
        val sessionViews = sessions.map { s ->
            SessionView(
                id = s.id,
                startedAt = s.startedAt,
                endedAt = s.endedAt,
                activeSec = s.activeSec,
                idleSec = s.idleSec,
                crashFlag = s.crashFlag,
                visits = (visitsBySession[s.id] ?: emptyList()).map { v ->
                    TabVisitView(
                        tab = TabKind.fromCode(v.tab) ?: TabKind.MAIN,
                        fromTs = v.fromTs,
                        toTs = v.toTs,
                        durationSec = v.durationSec
                    )
                },
                orderWorks = (orderWorkBySession[s.id] ?: emptyList()).map { w ->
                    OrderWorkView(
                        orderId = w.orderId,
                        areaTitle = w.areaTitle,
                        orderTitle = w.orderTitle,
                        startedAt = w.startedAt,
                        endedAt = w.endedAt,
                        searchSec = w.searchSec,
                        verifySec = w.verifySec,
                        status = OrderWorkStatus.fromCode(w.status),
                        totalSamples = w.totalSamples,
                        foundSamples = w.foundSamples
                    )
                }
            )
        }

        val allVisits = sessionViews.flatMap { it.visits }
        val tabUsage = computeTabUsage(allVisits)
        val totals = computeTotals(sessions, events, dailySummary)
        val unfinished = computeUnfinishedOrders(sessionViews)
        val eventViews = events.map { e ->
            EventView(
                atTs = e.atTs,
                level = EventLevel.fromCode(e.level),
                category = EventCategory.fromCode(e.category) ?: EventCategory.APP,
                summary = e.summary,
                detailsJson = e.detailsJson
            )
        }

        return DayView(
            date = date,
            sessions = sessionViews,
            totals = totals,
            tabUsage = tabUsage,
            unfinishedOrders = unfinished,
            events = eventViews,
            problems = problems
        )
    }

    /**
     * FIX 5.10-stat-admin-ui-5a:
     * Незавершённые наряды (§7.6). Собираются со всех сессий дня,
     * фильтруются по `status != DONE`. Сортировка:
     *  1. % found убыв (сначала «близкие к завершению»);
     *  2. время последнего действия убыв (endedAt ?: startedAt);
     *  3. orderId возрастание (стабильный ключ).
     *
     * Дубликаты по orderId не схлопываются: если оператор сегодня
     * дважды открывал один наряд, будет две строки — это видно
     * в ленте и полезно.
     */
    fun computeUnfinishedOrders(sessions: List<SessionView>): List<OrderWorkView> {
        return sessions
            .flatMap { it.orderWorks }
            .filter { it.status != OrderWorkStatus.DONE }
            .sortedWith(
                compareByDescending<OrderWorkView> { it.percent }
                    .thenByDescending { it.endedAt ?: it.startedAt }
                    .thenBy { it.orderId }
            )
    }

    /**
     * Сводные счётчики дня. `runs` — из daily_summary, если есть;
     * иначе — число сессий. `readyOrders` — только из daily_summary
     * (в первой версии без расчёта).
     */
    fun computeTotals(
        sessions: List<SessionEntity>,
        events: List<EventEntity>,
        dailySummary: DailySummaryEntity?
    ): DayTotals {
        val activeSec = sessions.sumOf { it.activeSec }
        val idleSec = sessions.sumOf { it.idleSec }
        val total = activeSec + idleSec
        val activePercent = if (total > 0) (activeSec * 100) / total else 0
        val idlePercent = if (total > 0) (idleSec * 100) / total else 0

        val errors = events.count { it.level == EventLevel.ERROR.code }
        val warns = events.count { it.level == EventLevel.WARN.code }

        return DayTotals(
            activeSec = activeSec,
            idleSec = idleSec,
            activePercent = activePercent,
            idlePercent = idlePercent,
            runs = dailySummary?.runs ?: sessions.size,
            readyOrders = dailySummary?.readyOrders ?: 0,
            errorsCount = errors,
            warnsCount = warns
        )
    }

    /**
     * Доля каждой вкладки в общем времени визитов. Сортировка —
     * по убыванию времени. Пустой список или нулевые длительности
     * дают пустой результат (деления на ноль не будет).
     */
    fun computeTabUsage(visits: List<TabVisitView>): List<TabUsage> {
        if (visits.isEmpty()) return emptyList()

        val byTab = mutableMapOf<TabKind, Int>()
        for (v in visits) {
            if (v.durationSec > 0) {
                byTab[v.tab] = (byTab[v.tab] ?: 0) + v.durationSec
            }
        }
        val total = byTab.values.sum()
        if (total <= 0) return emptyList()

        return byTab.entries
            .sortedByDescending { it.value }
            .map { (tab, sec) ->
                TabUsage(
                    tab = tab,
                    totalSec = sec,
                    percent = (sec * 100) / total
                )
            }
    }

    /**
     * Человекочитаемая длительность.
     *  - 0 или отрицательное → «0 сек»;
     *  - до минуты → «N сек»;
     *  - до часа → «M мин S сек»;
     *  - час и более → «H ч M мин».
     */
    fun formatDuration(sec: Int): String {
        if (sec <= 0) return "0 сек"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "$h ч $m мин"
            m > 0 -> "$m мин $s сек"
            else -> "$s сек"
        }
    }
}