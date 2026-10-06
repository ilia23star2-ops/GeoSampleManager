package com.example.geosamplemanager.ui.screens.admin

import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventLevel
import com.example.geosamplemanager.data.stats.OrderWorkStatus
import com.example.geosamplemanager.data.stats.TabKind
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.10-stat-daily-file-1:
 * Тесты на StatsExporter.buildJson. Без файлов, без Android.
 * Имена — латиница.
 */
class StatsExporterTest {

    private fun emptyDay(date: String = "2026-10-06"): DayView = DayView(
        date = date,
        sessions = emptyList(),
        totals = DayTotals(
            activeSec = 0,
            idleSec = 0,
            activePercent = 0,
            idlePercent = 0,
            runs = 0,
            readyOrders = 0,
            errorsCount = 0,
            warnsCount = 0
        ),
        tabUsage = emptyList(),
        unfinishedOrders = emptyList(),
        events = emptyList(),
        problems = ProblemsView(0, 0, 0)
    )

    private fun parseJson(s: String) = JsonParser.parseString(s).asJsonObject

    // ============================================================
    // Верхний уровень
    // ============================================================

    @Test
    fun topLevel_containsAllRequiredFields() {
        val json = StatsExporter.buildJson(emptyDay(), ProblemsView(0, 0, 0))
        val o = parseJson(json)
        assertEquals(1, o.get("format_version").asInt)
        assertEquals("2026-10-06", o.get("date").asString)
        assertTrue(o.has("weekday"))
        assertTrue(o.has("in_work_window"))
        assertTrue(o.has("totals"))
        assertTrue(o.has("sessions"))
        assertTrue(o.has("events"))
        assertTrue(o.has("db_problems"))
    }

    // ============================================================
    // weekday
    // ============================================================

    @Test
    fun weekday_tuesday() {
        // 2026-10-06 — вторник.
        val json = StatsExporter.buildJson(emptyDay("2026-10-06"), ProblemsView(0, 0, 0))
        assertEquals("Вт", parseJson(json).get("weekday").asString)
    }

    @Test
    fun weekday_saturday() {
        // 2026-10-10 — суббота.
        val json = StatsExporter.buildJson(emptyDay("2026-10-10"), ProblemsView(0, 0, 0))
        assertEquals("Сб", parseJson(json).get("weekday").asString)
    }

    @Test
    fun weekday_sunday() {
        val json = StatsExporter.buildJson(emptyDay("2026-10-11"), ProblemsView(0, 0, 0))
        assertEquals("Вс", parseJson(json).get("weekday").asString)
    }

    // ============================================================
    // in_work_window
    // ============================================================

    @Test
    fun inWorkWindow_weekdayIsTrue() {
        val json = StatsExporter.buildJson(emptyDay("2026-10-06"), ProblemsView(0, 0, 0))
        assertTrue(parseJson(json).get("in_work_window").asBoolean)
    }

    @Test
    fun inWorkWindow_weekendIsFalse() {
        val json = StatsExporter.buildJson(emptyDay("2026-10-10"), ProblemsView(0, 0, 0))
        assertFalse(parseJson(json).get("in_work_window").asBoolean)
    }

    // ============================================================
    // totals.by_tab
    // ============================================================

    @Test
    fun totals_byTabEmptyForEmptyDay() {
        val json = StatsExporter.buildJson(emptyDay(), ProblemsView(0, 0, 0))
        val byTab = parseJson(json).getAsJsonObject("totals").getAsJsonObject("by_tab")
        assertEquals(0, byTab.size())
    }

    @Test
    fun totals_byTabFilledFromTabUsage() {
        val day = emptyDay().copy(
            tabUsage = listOf(
                TabUsage(TabKind.SEARCH, 100, 50),
                TabUsage(TabKind.DB, 100, 50)
            ),
            totals = DayTotals(
                activeSec = 200,
                idleSec = 100,
                activePercent = 66,
                idlePercent = 34,
                runs = 1,
                readyOrders = 0,
                errorsCount = 1,
                warnsCount = 2
            )
        )
        val json = StatsExporter.buildJson(day, ProblemsView(0, 0, 0))
        val byTab = parseJson(json).getAsJsonObject("totals").getAsJsonObject("by_tab")
        assertEquals(100, byTab.get("search").asInt)
        assertEquals(100, byTab.get("db").asInt)
        assertEquals(1, parseJson(json).getAsJsonObject("totals").get("errors_count").asInt)
        assertEquals(2, parseJson(json).getAsJsonObject("totals").get("warns_count").asInt)
    }

    // ============================================================
    // sessions
    // ============================================================

    @Test
    fun session_withVisitsAndOrderWork() {
        val day = emptyDay().copy(
            sessions = listOf(
                SessionView(
                    id = 42,
                    startedAt = 1_000_000,
                    endedAt = 2_000_000,
                    activeSec = 300,
                    idleSec = 100,
                    crashFlag = false,
                    visits = listOf(
                        TabVisitView(TabKind.SEARCH, 1_000_000, 1_500_000, 500)
                    ),
                    orderWorks = listOf(
                        OrderWorkView(
                            orderId = 10,
                            areaTitle = "Коптеловский",
                            orderTitle = "Наряд №27",
                            startedAt = 1_000_000,
                            endedAt = 1_500_000,
                            searchSec = 60,
                            verifySec = 120,
                            status = OrderWorkStatus.DONE,
                            totalSamples = 15,
                            foundSamples = 15
                        )
                    )
                )
            )
        )
        val json = StatsExporter.buildJson(day, ProblemsView(0, 0, 0))
        val sessions = parseJson(json).getAsJsonArray("sessions")
        assertEquals(1, sessions.size())
        val s = sessions.get(0).asJsonObject
        assertEquals(42L, s.get("id").asLong)
        assertEquals(1_000_000L, s.get("started_at").asLong)
        assertEquals(2_000_000L, s.get("ended_at").asLong)
        assertFalse(s.get("crash_flag").asBoolean)

        val visits = s.getAsJsonArray("tab_visits")
        assertEquals(1, visits.size())
        assertEquals("search", visits.get(0).asJsonObject.get("tab").asString)

        val work = s.getAsJsonArray("order_work")
        assertEquals(1, work.size())
        val w = work.get(0).asJsonObject
        assertEquals(10L, w.get("order_id").asLong)
        assertEquals("done", w.get("status").asString)
        assertEquals(15, w.get("total_samples").asInt)
    }

    @Test
    fun session_openEndedAtIsNull() {
        val day = emptyDay().copy(
            sessions = listOf(
                SessionView(
                    id = 1,
                    startedAt = 1000,
                    endedAt = null,
                    activeSec = 0,
                    idleSec = 0,
                    crashFlag = false,
                    visits = emptyList(),
                    orderWorks = emptyList()
                )
            )
        )
        val json = StatsExporter.buildJson(day, ProblemsView(0, 0, 0))
        val s = parseJson(json).getAsJsonArray("sessions").get(0).asJsonObject
        assertTrue(s.get("ended_at").isJsonNull)
    }

    // ============================================================
    // events
    // ============================================================

    @Test
    fun event_withDetailsJson_parsedToObject() {
        val day = emptyDay().copy(
            events = listOf(
                EventView(
                    sessionId = 1,
                    atTs = 1000,
                    level = EventLevel.ERROR,
                    category = EventCategory.DB,
                    summary = "Ошибка чтения",
                    detailsJson = "{\"key\":\"value\",\"n\":42}"
                )
            )
        )
        val json = StatsExporter.buildJson(day, ProblemsView(0, 0, 0))
        val e = parseJson(json).getAsJsonArray("events").get(0).asJsonObject
        assertEquals("error", e.get("level").asString)
        assertEquals("db", e.get("category").asString)
        val d = e.getAsJsonObject("details")
        assertEquals("value", d.get("key").asString)
        assertEquals(42, d.get("n").asInt)
    }

    @Test
    fun event_withoutDetails_detailsIsNull() {
        val day = emptyDay().copy(
            events = listOf(
                EventView(
                    sessionId = 1,
                    atTs = 1000,
                    level = EventLevel.INFO,
                    category = EventCategory.APP,
                    summary = "Запуск",
                    detailsJson = null
                )
            )
        )
        val json = StatsExporter.buildJson(day, ProblemsView(0, 0, 0))
        val e = parseJson(json).getAsJsonArray("events").get(0).asJsonObject
        assertTrue(e.get("details").isJsonNull)
    }

    @Test
    fun event_brokenDetailsJson_detailsIsNull() {
        val day = emptyDay().copy(
            events = listOf(
                EventView(
                    sessionId = 1,
                    atTs = 1000,
                    level = EventLevel.INFO,
                    category = EventCategory.APP,
                    summary = "s",
                    detailsJson = "{not valid json"
                )
            )
        )
        val json = StatsExporter.buildJson(day, ProblemsView(0, 0, 0))
        val e = parseJson(json).getAsJsonArray("events").get(0).asJsonObject
        assertTrue(e.get("details").isJsonNull)
    }

    // ============================================================
    // db_problems
    // ============================================================

    @Test
    fun dbProblems_fromProblemsView() {
        val json = StatsExporter.buildJson(
            emptyDay(),
            ProblemsView(orphanOrders = 1, orphanSamples = 2, brokenPhotos = 3)
        )
        val p = parseJson(json).getAsJsonObject("db_problems")
        assertEquals(1, p.get("orphan_orders").asInt)
        assertEquals(2, p.get("orphan_samples").asInt)
        assertEquals(3, p.get("broken_photos").asInt)
    }

    @Test
    fun dbProblems_zerosIncluded() {
        val json = StatsExporter.buildJson(emptyDay(), ProblemsView(0, 0, 0))
        val p = parseJson(json).getAsJsonObject("db_problems")
        assertEquals(0, p.get("orphan_orders").asInt)
        assertEquals(0, p.get("orphan_samples").asInt)
        assertEquals(0, p.get("broken_photos").asInt)
    }

    // ============================================================
    // Согласованность результата
    // ============================================================

    @Test
    fun jsonIsValidParseable() {
        val json = StatsExporter.buildJson(emptyDay(), ProblemsView(0, 0, 0))
        // Не бросает.
        parseJson(json)
    }

    @Test
    fun unknownDate_weekdayIsQuestionMark() {
        val json = StatsExporter.buildJson(emptyDay("not-a-date"), ProblemsView(0, 0, 0))
        assertEquals("?", parseJson(json).get("weekday").asString)
    }

    @Test
    fun unknownDate_inWorkWindowIsFalse() {
        val json = StatsExporter.buildJson(emptyDay("not-a-date"), ProblemsView(0, 0, 0))
        assertFalse(parseJson(json).get("in_work_window").asBoolean)
    }
}