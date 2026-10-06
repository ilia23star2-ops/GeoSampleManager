package com.example.geosamplemanager.ui.screens.admin

import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventEntity
import com.example.geosamplemanager.data.stats.EventLevel
import com.example.geosamplemanager.data.stats.OrderWorkEntity
import com.example.geosamplemanager.data.stats.OrderWorkStatus
import com.example.geosamplemanager.data.stats.SessionEntity
import com.example.geosamplemanager.data.stats.TabKind
import com.example.geosamplemanager.data.stats.TabVisitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.10-stat-admin-ui-1: тесты агрегатора.
 * FIX 5.10-stat-admin-ui-5a: тесты незавершённых.
 * FIX 5.10-stat-admin-v2-nav: тесты timeline-сегментов.
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
    // computeTimelineSegments (FIX 5.10-stat-admin-v2-nav)
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
        // Сессия началась до дня и закончилась после.
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
}