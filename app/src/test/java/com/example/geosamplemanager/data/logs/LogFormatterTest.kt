package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * FIX 5.9-logs-1:
 * Проверяем формат даты и времени. Тесты используют UTC —
 * результат не зависит от TZ машины.
 */
class LogFormatterTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun ts(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, second: Int
    ): Long {
        val cal = Calendar.getInstance(utc)
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun fullFormatsDateAndTime() {
        val t = ts(2026, 10, 5, 14, 32, 5)
        assertEquals("05.10.2026 14:32:05", LogFormatter.full(t, utc))
    }

    @Test
    fun fullPadsSingleDigits() {
        val t = ts(2026, 1, 3, 4, 5, 6)
        assertEquals("03.01.2026 04:05:06", LogFormatter.full(t, utc))
    }

    @Test
    fun timeOnlyDropsDate() {
        val t = ts(2026, 10, 5, 14, 32, 5)
        assertEquals("14:32:05", LogFormatter.timeOnly(t, utc))
    }

    @Test
    fun timeOnlyHandlesMidnight() {
        val t = ts(2026, 10, 5, 0, 0, 0)
        assertEquals("00:00:00", LogFormatter.timeOnly(t, utc))
    }

    @Test
    fun differentDaysDifferentFull() {
        val t1 = ts(2026, 10, 5, 14, 32, 5)
        val t2 = ts(2026, 10, 6, 14, 32, 5)
        assertEquals(
            true,
            LogFormatter.full(t1, utc) != LogFormatter.full(t2, utc)
        )
    }
}