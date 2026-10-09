package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * FIX 5.10-stat-session:
 * Тесты на рабочее окно. Пн–Пт, 08:00–17:00.
 */
class WorkWindowTest {

    @Test
    fun monday_10am_isInWindow() {
        val ts = timestamp(Calendar.MONDAY, 10, 0)
        assertTrue(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun monday_8am_sharp_isInWindow() {
        val ts = timestamp(Calendar.MONDAY, 8, 0)
        assertTrue(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun monday_7am_isNotInWindow() {
        val ts = timestamp(Calendar.MONDAY, 7, 0)
        assertFalse(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun monday_5pm_sharp_isNotInWindow() {
        val ts = timestamp(Calendar.MONDAY, 17, 0)
        assertFalse(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun monday_4_59pm_isInWindow() {
        val ts = timestamp(Calendar.MONDAY, 16, 59)
        assertTrue(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun friday_10am_isInWindow() {
        val ts = timestamp(Calendar.FRIDAY, 10, 0)
        assertTrue(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun saturday_10am_isNotInWindow() {
        val ts = timestamp(Calendar.SATURDAY, 10, 0)
        assertFalse(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun sunday_10am_isNotInWindow() {
        val ts = timestamp(Calendar.SUNDAY, 10, 0)
        assertFalse(WorkWindow.isInWorkWindow(ts))
    }

    @Test
    fun monday_midnight_isNotInWindow() {
        val ts = timestamp(Calendar.MONDAY, 0, 0)
        assertFalse(WorkWindow.isInWorkWindow(ts))
    }

    /**
     * timestamp по дню недели и часу-минуте. Неделя берётся
     * из фиксированной базовой даты — 06.10.2026 (вторник).
     */
    private fun timestamp(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val base = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 6)  // вторник
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Сдвигаем к нужному дню недели.
        val current = base.get(Calendar.DAY_OF_WEEK)
        var delta = dayOfWeek - current
        if (delta < 0) delta += 7
        base.add(Calendar.DAY_OF_MONTH, delta)
        return base.timeInMillis
    }
}