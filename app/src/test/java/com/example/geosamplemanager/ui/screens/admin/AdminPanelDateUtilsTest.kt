package com.example.geosamplemanager.ui.screens.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.10-stat-admin-ui-4:
 * Тесты на AdminPanelDateUtils. Чистая логика, без Android.
 * Имена тестов — латиница (правило TESTING.md).
 */
class AdminPanelDateUtilsTest {

    // ============================================================
    // formatDate
    // ============================================================

    @Test
    fun formatDate_padsMonthAndDay() {
        // 15 января 2026 12:00 локально.
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, 0, 15, 12, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        assertEquals("2026-01-15", AdminPanelDateUtils.formatDate(cal.timeInMillis))
    }

    @Test
    fun formatDate_decemberCorrect() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, 11, 31, 23, 59, 59)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        assertEquals("2026-12-31", AdminPanelDateUtils.formatDate(cal.timeInMillis))
    }

    // ============================================================
    // today
    // ============================================================

    @Test
    fun today_matchesFormat() {
        val t = AdminPanelDateUtils.today()
        // YYYY-MM-DD — ровно 10 символов, две чёрточки.
        assertEquals(10, t.length)
        assertEquals('-', t[4])
        assertEquals('-', t[7])
    }

    // ============================================================
    // dayBounds
    // ============================================================

    @Test
    fun dayBounds_startBeforeEnd() {
        val (start, end) = AdminPanelDateUtils.dayBounds("2026-10-06")
        assertTrue(end > start)
    }

    @Test
    fun dayBounds_durationIsAboutOneDay() {
        val (start, end) = AdminPanelDateUtils.dayBounds("2026-10-06")
        val diffSec = (end - start) / 1000L
        // 23ч..25ч — с запасом на летнее/зимнее время.
        assertTrue("diffSec=$diffSec", diffSec in 82_800L..90_000L)
    }

    @Test
    fun dayBounds_startIsMidnight() {
        val (start, _) = AdminPanelDateUtils.dayBounds("2026-10-06")
        val formatted = AdminPanelDateUtils.formatDate(start)
        assertEquals("2026-10-06", formatted)
    }

    // ============================================================
    // label
    // ============================================================

    @Test
    fun label_containsDateAndWeekday() {
        val l = AdminPanelDateUtils.label("2026-10-06")
        assertTrue(l.startsWith("2026-10-06"))
        assertTrue(l.contains("("))
        assertTrue(l.contains(")"))
    }

    @Test
    fun label_weekdayIsTwoRussianLetters() {
        val l = AdminPanelDateUtils.label("2026-10-06")
        // Внутри скобок — ровно 2 буквы.
        val inner = l.substringAfter("(").substringBefore(")")
        assertEquals(2, inner.length)
        assertTrue(inner.all { it in 'а'..'я' })
    }

    @Test
    fun label_weekdayDoesNotDependOnFirstDayOfWeekSetting() {
        // 2026-10-06 — вторник. Проверяем согласованность
        // с Calendar.DAY_OF_WEEK независимо от Locale.
        val l = AdminPanelDateUtils.label("2026-10-06")
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, 9, 6, 0, 0, 0)
        val expectedIndex = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val names = arrayOf("вс", "пн", "вт", "ср", "чт", "пт", "сб")
        assertTrue(l.contains("(${names[expectedIndex]})"))
    }
}