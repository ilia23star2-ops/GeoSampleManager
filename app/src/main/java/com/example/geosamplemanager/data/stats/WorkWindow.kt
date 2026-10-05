package com.example.geosamplemanager.data.stats

import java.util.Calendar

/**
 * FIX 5.10-stat-session:
 * Рабочее окно: Пн–Пт, 08:00–17:00.
 *
 * Чистая функция — тестируется без Android.
 *
 * Семантика: timestamp попадает в окно, если это будний день
 * и час — от 08:00 включительно до 17:00 не включая.
 * То есть 08:00:00 — да, 16:59:59 — да, 17:00:00 — уже нет.
 */
object WorkWindow {

    fun isInWorkWindow(timestampMs: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampMs

        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in START_HOUR until END_HOUR
    }

    const val START_HOUR = 8
    const val END_HOUR = 17
}