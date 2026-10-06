package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * FIX 5.10-stat-daily-file-2:
 * Тесты на чистую логику StatsRotator. Без Android, без БД.
 * Имена — латиница.
 */
class StatsRotatorTest {

    private val tz = TimeZone.getTimeZone("Europe/Moscow")

    // ============================================================
    // monthKey
    // ============================================================

    @Test
    fun monthKey_januaryPadsMonth() {
        val cal = Calendar.getInstance(tz)
        cal.set(2026, 0, 15, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals("2026-01", StatsRotator.monthKey(cal.timeInMillis, tz))
    }

    @Test
    fun monthKey_december() {
        val cal = Calendar.getInstance(tz)
        cal.set(2026, 11, 31, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals("2026-12", StatsRotator.monthKey(cal.timeInMillis, tz))
    }

    @Test
    fun monthKey_endOfOctober() {
        val cal = Calendar.getInstance(tz)
        cal.set(2026, 9, 31, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals("2026-10", StatsRotator.monthKey(cal.timeInMillis, tz))
    }

    @Test
    fun monthKey_startOfNovember() {
        val cal = Calendar.getInstance(tz)
        cal.set(2026, 10, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals("2026-11", StatsRotator.monthKey(cal.timeInMillis, tz))
    }

    // ============================================================
    // shouldRotate
    // ============================================================

    @Test
    fun shouldRotate_nullData_false() {
        assertFalse(StatsRotator.shouldRotate(null, "2026-10"))
    }

    @Test
    fun shouldRotate_blankData_false() {
        assertFalse(StatsRotator.shouldRotate("", "2026-10"))
    }

    @Test
    fun shouldRotate_sameMonth_false() {
        assertFalse(StatsRotator.shouldRotate("2026-10", "2026-10"))
    }

    @Test
    fun shouldRotate_previousMonth_true() {
        assertTrue(StatsRotator.shouldRotate("2026-09", "2026-10"))
    }

    @Test
    fun shouldRotate_previousYear_true() {
        assertTrue(StatsRotator.shouldRotate("2025-12", "2026-01"))
    }

    @Test
    fun shouldRotate_futureMonth_true() {
        // Если по какой-то причине данные «в будущем» — тоже
        // ротируем (лучше в архив, чем потерять).
        assertTrue(StatsRotator.shouldRotate("2026-11", "2026-10"))
    }

    // ============================================================
    // currentMonthKey — только проверка формата
    // ============================================================

    @Test
    fun currentMonthKey_matchesPattern() {
        val key = StatsRotator.currentMonthKey(tz)
        assertEquals(7, key.length)
        assertEquals('-', key[4])
        assertTrue(key.substring(0, 4).toIntOrNull() != null)
        assertTrue(key.substring(5, 7).toIntOrNull() != null)
    }
}