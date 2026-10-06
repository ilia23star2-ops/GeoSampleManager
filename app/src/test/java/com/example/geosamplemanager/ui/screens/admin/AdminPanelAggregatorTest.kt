package com.example.geosamplemanager.ui.screens.admin

import com.example.geosamplemanager.data.dao.OrderSampleCounts
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventEntity
import com.example.geosamplemanager.data.stats.EventLevel
import com.example.geosamplemanager.data.stats.OrderWorkEntity
import com.example.geosamplemanager.data.stats.OrderWorkStatus
import com.example.geosamplemanager.data.stats.SessionEntity
import com.example.geosamplemanager.data.stats.TabKind
import com.example.geosamplemanager.data.stats.TabVisitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.10-stat-admin-ui-1: тесты агрегатора.
 * FIX 5.10-stat-admin-ui-5a: незавершённые.
 * FIX 5.10-stat-admin-v2-nav: timeline-сегменты.
 * FIX 5.10-stat-admin-v2-orders-a: список нарядов, статус, фильтр.
 */
class AdminPanelAggregatorTest {

    // ============================================================
    // formatDuration
    // ============================================================

    @Test
    fun formatDuration_zero() {
        assertEquals("0 сек", AdminPanelAggregator.formatDuration(0))
    }

    @Test
    fun formatDuration_negative() {
        assertEquals("0 сек", AdminPanelAggregator.formatDuration(-5))
    }

    @Test
    fun formatDuration_seconds() {
        assertEquals("45 сек", AdminPanelAggregator.formatDuration(45))
    }

    @Test
    fun formatDuration_minutes() {
        assertEquals("2 мин 5 сек", AdminPanelAggregator.formatDuration(125))
    }

    @Test
    fun formatDuration_exactMinute() {
        assertEquals("1 мин 0 сек", AdminPanelAggregator.formatDuration(60))
    }

    @Test
    fun formatDuration_hours() {
        assertEquals("1 ч 30 мин", AdminPanelAggregator.formatDuration(5400))
    }

    @Test
    fun formatDuration_hours_noMinutes() {
        assertEquals("2 ч 0 мин", AdminPanelAggregator.formatDuration(7200))
    }

    // ============================================================
    // computeTotals
    // ============================================================

    @Test
    fun totals_emptySessions_zero() {
        val t = AdminPanelAggregator.computeTotals(
            sessions = emptyList(),
            events = emptyList(),
            dailySummary = null
        )
        assertEquals(0, t.activeSec)
        assertEquals(0, t.idleSec)
        assertEquals(0, t.activePercent)
        assertEquals(0, t.idlePercent)
        assertEquals(0, t.runs)
    }

    @Test
    fun totals_activeAndIdle() {
        val t = AdminPanelAggregator.computeTotals(
            sessions = listOf(
                SessionEntity(
                    id = 1,
                    startedAt = 0,
                    endedAt = 100_000,
                    activeSec = 60,
                    idleSec = 40
                )
            ),
            events = emptyList(),
            dailySummary = null
        )
        assertEquals(60, t.activeSec)
        assertEquals(40, t.idleSec)
        assertEquals(60, t.activePercent)
        assertEquals(40, t.idlePercent)
        assertEquals(1, t.runs)
    }

    @Test
    fun totals_eventsCountByLevel() {
        val t = AdminPanelAggregator.computeTotals(
            sessions = emptyList(),
            events = listOf(
                EventEntity(sessionId = 1, atTs = 1, level = "error", category = "db", summary = "e1"),
                EventEntity(sessionId = 1, atTs = 2, level = "error", category = "db", summary = "e2"),
                EventEntity(sessionId = 1, atTs = 3, level = "warn", category = "search", summary = "w1"),
                EventEntity(sessionId = 1, atTs = 4, level = "warn", category = "voice", summary = "w2"),
                EventEntity(sessionId = 1, atTs = 5, level = "warn", category = "voice", summary = "w3"),
                EventEntity(sessionId = 1, atTs = 6, level = "info", category = "app", summary = "i1")
            ),
            dailySummary = null
        )
        assertEquals(2, t.errorsCount)
        assertEquals(3, t.warnsCount)
    }

    // ============================================================
    // computeTabUsage
    // ============================================================

    @Test
    fun tabUsage_emptyReturnsEmpty() {
        assertTrue(AdminPanelAggregator.computeTabUsage(emptyList()).isEmpty())
    }

    @Test
    fun tabUsage_sumsByTab() {
        val visits = listOf(
            TabVisitView(TabKind.SEARCH, 0, 100, 100),
            TabVisitView(TabKind.SEARCH, 100, 200, 50),
            TabVisitView(TabKind.DB, 200, 250, 50)
        )
        val usage = AdminPanelAggregator.computeTabUsage(visits)
        assertEquals(2, usage.size)
        assertEquals(TabKind.SEARCH, usage[0].tab)
        assertEquals(150, usage[0].totalSec)
        assertEquals(75, usage[0].percent)
        assertEquals(TabKind.DB, usage[1].tab)
        assertEquals(50, usage[1].totalSec)
        assertEquals(25, usage[1].percent)
    }

    @Test
    fun tabUsage_zeroDurationsIgnored() {
        val visits = listOf(
            TabVisitView(TabKind.MAIN, 0, 0, 0),
            TabVisitView(TabKind.MAIN, 0, 0, 0)
        )
        assertTrue(AdminPanelAggregator.computeTabUsage(visits).isEmpty())
    }

    @Test
    fun tabUsage_sortedBySecondsDesc() {
        val visits = listOf(
            TabVisitView(TabKind.DB, 0, 10, 10),
            TabVisitView(TabKind.SEARCH, 0, 100, 100),
            TabVisitView(TabKind.MAIN, 0, 50, 50)
        )
        val usage = AdminPanelAggregator.computeTabUsage(visits)
        assertEquals(3, usage.size)
        assertEquals(TabKind.SEARCH, usage[0].tab)
        assertEquals(TabKind.MAIN, usage[1].tab)
        assertEquals(TabKind.DB, usage[2].tab)
    }

    // ============================================================
    // buildDayView
    // ============================================================

    @Test
    fun dayView_empty() {
        val v = AdminPanelAggregator.buildDayView(
            date = "2026-10-06",
            sessions = emptyList(),
            visitsBySession = emptyMap(),
            orderWorkBySession = emptyMap(),
            events = emptyList(),
            dailySummary = null,
            problems = ProblemsView(0, 0, 0)
        )
        assertEquals("2026-10-06", v.date)
        assertTrue(v.sessions.isEmpty())
        assertTrue(v.events.isEmpty())
        assertTrue(v.tabUsage.isEmpty())
        assertEquals(0, v.totals.activeSec)
    }

    @Test
    fun dayView_singleSession_visitsAndOrderWork() {
        val v = AdminPanelAggregator.buildDayView(
            date = "2026-10-06",
            sessions = listOf(
                SessionEntity(
                    id = 1,
                    startedAt = 1000,
                    endedAt = 5000,
                    activeSec = 100,
                    idleSec = 50
                )
            ),
            visitsBySession = mapOf(
                1L to listOf(
                    TabVisitEntity(
                        id = 10,
                        sessionId = 1,
                        tab = "search",
                        fromTs = 1000,
                        toTs = 3000,
                        durationSec = 60
                    )
                )
            ),
            orderWorkBySession = mapOf(
                1L to listOf(
                    OrderWorkEntity(
                        id = 5,
                        sessionId = 1,
                        orderId = 42,
                        areaTitle = "Коптеловский",
                        orderTitle = "Наряд №27",
                        startedAt = 1000,
                        endedAt = 3000,
                        searchSec = 20,
                        verifySec = 40,
                        status = "done",
                        totalSamples = 15,
                        foundSamples = 15
                    )
                )
            ),
            events = listOf(
                EventEntity(
                    sessionId = 1,
                    atTs = 1500,
                    level = "info",
                    category = "app",
                    summary = "Запуск"
                )
            ),
            dailySummary = null,
            problems = ProblemsView(0, 0, 0)
        )
        assertEquals(1, v.sessions.size)
        val s = v.sessions[0]
        assertEquals(1, s.visits.size)
        assertEquals(TabKind.SEARCH, s.visits[0].tab)
        assertEquals(60, s.visits[0].durationSec)
        assertEquals(1, s.orderWorks.size)
        assertEquals(OrderWorkStatus.DONE, s.orderWorks[0].status)
        assertEquals(15, s.orderWorks[0].foundSamples)
        assertEquals(1, v.events.size)
        assertEquals(EventLevel.INFO, v.events[0].level)
        assertEquals(EventCategory.APP, v.events[0].category)
    }

    @Test
    fun dayView_unknownTabFallsBackToMain() {
        val v = AdminPanelAggregator.buildDayView(
            date = "2026-10-06",
            sessions = listOf(SessionEntity(id = 1, startedAt = 0)),
            visitsBySession = mapOf(
                1L to listOf(
                    TabVisitEntity(
                        id = 10,
                        sessionId = 1,
                        tab = "unknown-tab-code",
                        fromTs = 0,
                        toTs = 1000,
                        durationSec = 1
                    )
                )
            ),
            orderWorkBySession = emptyMap(),
            events = emptyList(),
            dailySummary = null,
            problems = ProblemsView(0, 0, 0)
        )
        assertEquals(TabKind.MAIN, v.sessions[0].visits[0].tab)
    }

    // ============================================================
    // ProblemsView
    // ============================================================

    @Test
    fun problemsView_isEmpty_whenAllZero() {
        assertTrue(ProblemsView(0, 0, 0).isEmpty)
    }

    @Test
    fun problemsView_notEmpty_whenAnyPositive() {
        assertTrue(!ProblemsView(0, 0, 1).isEmpty)
        assertTrue(!ProblemsView(1, 0, 0).isEmpty)
        assertTrue(!ProblemsView(0, 2, 0).isEmpty)
    }

    // ============================================================
    // computeTimelineSegments
    // ============================================================

    @Test
    fun timeline_empty() {
        val s = AdminPanelAggregator.computeTimelineSegments(
            sessions = emptyList(),
            date = "2026-10-06",
            now = 0
        )
        assertTrue(s.isEmpty())
    }

    @Test
    fun timeline_oneSessionInBounds() {
        val (dayStart, _) = AdminPanelDateUtils.dayBounds("2026-10-06")
        val from = dayStart + 3_600_000L
        val to = dayStart + 7_200_000L
        val s = AdminPanelAggregator.computeTimelineSegments(
            sessions = listOf(
                SessionEntity(id = 1, startedAt = from, endedAt = to)
            ),
            date = "2026-10-06",
            now = to
        )
        assertEquals(1, s.size)
        assertEquals(1L, s[0].sessionId)
        assertEquals(from, s[0].fromTs)
        assertEquals(to, s[0].toTs)
    }

    @Test
    fun timeline_openSessionClampedToNow() {
        val (dayStart, _) = AdminPanelDateUtils.dayBounds("2026-10-06")
        val from = dayStart + 3_600_000L
        val now = dayStart + 5_400_000L
        val s = AdminPanelAggregator.computeTimelineSegments(
            sessions = listOf(
                SessionEntity(id = 1, startedAt = from, endedAt = null)
            ),
            date = "2026-10-06",
            now = now
        )
        assertEquals(1, s.size)
        assertEquals(now, s[0].toTs)
    }

    @Test
    fun timeline_sessionClampedByDayBounds() {
        val (dayStart, dayEnd) = AdminPanelDateUtils.dayBounds("2026-10-06")
        val s = AdminPanelAggregator.computeTimelineSegments(
            sessions = listOf(
                SessionEntity(
                    id = 1,
                    startedAt = dayStart - 3_600_000L,
                    endedAt = dayEnd + 3_600_000L
                )
            ),
            date = "2026-10-06",
            now = dayEnd + 3_600_000L
        )
        assertEquals(1, s.size)
        assertEquals(dayStart, s[0].fromTs)
        assertEquals(dayEnd, s[0].toTs)
    }

    @Test
    fun timeline_zeroLengthDropped() {
        val (dayStart, _) = AdminPanelDateUtils.dayBounds("2026-10-06")
        val s = AdminPanelAggregator.computeTimelineSegments(
            sessions = listOf(
                SessionEntity(
                    id = 1,
                    startedAt = dayStart - 3_600_000L,
                    endedAt = dayStart - 1_800_000L
                )
            ),
            date = "2026-10-06",
            now = dayStart
        )
        assertTrue(s.isEmpty())
    }

    // ============================================================
    // classifyOrderStatus (FIX 5.10-stat-admin-v2-orders-a)
    // ============================================================

    @Test
    fun classifyStatus_notStarted_noWork_noFound() {
        assertEquals(
            AdminOrderStatus.NOT_STARTED,
            AdminPanelAggregator.classifyOrderStatus(
                totalSamples = 10,
                foundSamples = 0,
                hasWork = false
            )
        )
    }

    @Test
    fun classifyStatus_done_allFound() {
        assertEquals(
            AdminOrderStatus.DONE,
            AdminPanelAggregator.classifyOrderStatus(
                totalSamples = 10,
                foundSamples = 10,
                hasWork = true
            )
        )
    }

    @Test
    fun classifyStatus_inProgress_partiallyFound() {
        assertEquals(
            AdminOrderStatus.IN_PROGRESS,
            AdminPanelAggregator.classifyOrderStatus(
                totalSamples = 10,
                foundSamples = 3,
                hasWork = true
            )
        )
    }

    @Test
    fun classifyStatus_inProgress_zeroFoundButHasWork() {
        assertEquals(
            AdminOrderStatus.IN_PROGRESS,
            AdminPanelAggregator.classifyOrderStatus(
                totalSamples = 10,
                foundSamples = 0,
                hasWork = true
            )
        )
    }

    @Test
    fun classifyStatus_emptyOrder_notStarted() {
        assertEquals(
            AdminOrderStatus.NOT_STARTED,
            AdminPanelAggregator.classifyOrderStatus(
                totalSamples = 0,
                foundSamples = 0,
                hasWork = false
            )
        )
    }

    // ============================================================
    // buildOrdersSummary (FIX 5.10-stat-admin-v2-orders-a)
    // ============================================================

    private fun area(id: Long, name: String) =
        AreaEntity(id = id, areaName = name)

    private fun order(id: Long, areaId: Long, number: String, created: Long) =
        OrderEntity(
            id = id,
            areaId = areaId,
            orderNumber = number,
            createdDate = created
        )

    @Test
    fun ordersSummary_emptyOrders_emptyResult() {
        val r = AdminPanelAggregator.buildOrdersSummary(
            areas = listOf(area(1, "Коптеловский")),
            orders = emptyList(),
            countsByOrder = emptyList(),
            workByOrder = emptyMap()
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun ordersSummary_notStarted_noCountsNoWork() {
        val r = AdminPanelAggregator.buildOrdersSummary(
            areas = listOf(area(1, "Коптеловский")),
            orders = listOf(order(10, 1, "27", 1000)),
            countsByOrder = emptyList(),
            workByOrder = emptyMap()
        )
        assertEquals(1, r.size)
        assertEquals(AdminOrderStatus.NOT_STARTED, r[0].status)
        assertEquals("Коптеловский", r[0].areaTitle)
        assertEquals("Наряд №27", r[0].orderTitle)
        assertEquals(0, r[0].totalSamples)
        assertEquals(0, r[0].foundSamples)
        assertNull(r[0].searchSec)
        assertNull(r[0].verifySec)
        assertEquals(false, r[0].hasWork)
    }

    @Test
    fun ordersSummary_inProgress_partial() {
        val r = AdminPanelAggregator.buildOrdersSummary(
            areas = listOf(area(1, "Коптеловский")),
            orders = listOf(order(10, 1, "27", 1000)),
            countsByOrder = listOf(
                OrderSampleCounts(orderId = 10, totalSamples = 15, foundSamples = 7)
            ),
            workByOrder = mapOf(
                10L to listOf(
                    OrderWorkEntity(
                        id = 1, sessionId = 1, orderId = 10,
                        areaTitle = "Коптеловский", orderTitle = "Наряд №27",
                        startedAt = 0, endedAt = 0,
                        searchSec = 30, verifySec = 60,
                        status = "half_done",
                        totalSamples = 15, foundSamples = 7
                    )
                )
            )
        )
        assertEquals(1, r.size)
        assertEquals(AdminOrderStatus.IN_PROGRESS, r[0].status)
        assertEquals(15, r[0].totalSamples)
        assertEquals(7, r[0].foundSamples)
        assertEquals(30, r[0].searchSec)
        assertEquals(60, r[0].verifySec)
        assertEquals(90, r[0].totalSec)
        assertEquals(46, r[0].percent)  // 7/15 = 46
    }

    @Test
    fun ordersSummary_done_sumsWorkFromMultipleSessions() {
        val r = AdminPanelAggregator.buildOrdersSummary(
            areas = listOf(area(1, "Коптеловский")),
            orders = listOf(order(10, 1, "27", 1000)),
            countsByOrder = listOf(
                OrderSampleCounts(orderId = 10, totalSamples = 15, foundSamples = 15)
            ),
            workByOrder = mapOf(
                10L to listOf(
                    OrderWorkEntity(
                        id = 1, sessionId = 1, orderId = 10,
                        areaTitle = "Коптеловский", orderTitle = "Наряд №27",
                        startedAt = 0, endedAt = 0,
                        searchSec = 30, verifySec = 60,
                        status = "half_done",
                        totalSamples = 15, foundSamples = 7
                    ),
                    OrderWorkEntity(
                        id = 2, sessionId = 2, orderId = 10,
                        areaTitle = "Коптеловский", orderTitle = "Наряд №27",
                        startedAt = 0, endedAt = 0,
                        searchSec = 10, verifySec = 20,
                        status = "done",
                        totalSamples = 15, foundSamples = 15
                    )
                )
            )
        )
        assertEquals(1, r.size)
        assertEquals(AdminOrderStatus.DONE, r[0].status)
        assertEquals(40, r[0].searchSec)   // 30 + 10
        assertEquals(80, r[0].verifySec)   // 60 + 20
        assertEquals(100, r[0].percent)
    }

    @Test
    fun ordersSummary_sortedByCreatedDesc() {
        val r = AdminPanelAggregator.buildOrdersSummary(
            areas = listOf(area(1, "Коптеловский")),
            orders = listOf(
                order(10, 1, "20", 1_000),
                order(11, 1, "21", 3_000),
                order(12, 1, "22", 2_000)
            ),
            countsByOrder = emptyList(),
            workByOrder = emptyMap()
        )
        assertEquals(3, r.size)
        assertEquals(11L, r[0].orderId)
        assertEquals(12L, r[1].orderId)
        assertEquals(10L, r[2].orderId)
    }

    @Test
    fun ordersSummary_limitApplied() {
        val r = AdminPanelAggregator.buildOrdersSummary(
            areas = listOf(area(1, "Коптеловский")),
            orders = (1L..50L).map { order(it, 1, "$it", it * 100) },
            countsByOrder = emptyList(),
            workByOrder = emptyMap(),
            limit = 10
        )
        assertEquals(10, r.size)
    }

    @Test
    fun ordersSummary_unknownArea_fallback() {
        val r = AdminPanelAggregator.buildOrdersSummary(
            areas = emptyList(),
            orders = listOf(order(10, 99, "27", 1000)),
            countsByOrder = emptyList(),
            workByOrder = emptyMap()
        )
        assertEquals(1, r.size)
        assertEquals("—", r[0].areaTitle)
    }

    // ============================================================
    // filterOrders (FIX 5.10-stat-admin-v2-orders-a)
    // ============================================================

    @Test
    fun filterOrders_empty_returnsAll() {
        val list = listOf(
            AdminOrderSummary(1, "Коптеловский", "Наряд №27", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null),
            AdminOrderSummary(2, "Актайский", "Наряд №14", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null)
        )
        assertEquals(2, AdminPanelAggregator.filterOrders(list, null).size)
        assertEquals(2, AdminPanelAggregator.filterOrders(list, "").size)
        assertEquals(2, AdminPanelAggregator.filterOrders(list, "  ").size)
    }

    @Test
    fun filterOrders_byArea() {
        val list = listOf(
            AdminOrderSummary(1, "Коптеловский", "Наряд №27", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null),
            AdminOrderSummary(2, "Актайский", "Наряд №14", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null)
        )
        val r = AdminPanelAggregator.filterOrders(list, "коптел")
        assertEquals(1, r.size)
        assertEquals(1L, r[0].orderId)
    }

    @Test
    fun filterOrders_byOrderNumber() {
        val list = listOf(
            AdminOrderSummary(1, "Коптеловский", "Наряд №27", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null),
            AdminOrderSummary(2, "Актайский", "Наряд №14", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null)
        )
        val r = AdminPanelAggregator.filterOrders(list, "14")
        assertEquals(1, r.size)
        assertEquals(2L, r[0].orderId)
    }

    @Test
    fun filterOrders_byOrderId() {
        val list = listOf(
            AdminOrderSummary(100, "Коптеловский", "Наряд №27", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null),
            AdminOrderSummary(200, "Актайский", "Наряд №14", 0, 0, 0,
                AdminOrderStatus.NOT_STARTED, null, null)
        )
        val r = AdminPanelAggregator.filterOrders(list, "200")
        assertEquals(1, r.size)
        assertEquals(200L, r[0].orderId)
    }
}