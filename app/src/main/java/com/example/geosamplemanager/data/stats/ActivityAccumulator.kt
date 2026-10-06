package com.example.geosamplemanager.data.stats

/**
 * FIX 5.10-stat-activity-a:
 * Постфактумный расчёт активного времени сессии по событиям.
 *
 * Правило:
 *  - Между двумя соседними событиями <60 сек — активное время.
 *  - Между двумя соседними событиями ≥60 сек — простой.
 *  - Хвост от последнего события до ended_at — по тому же правилу.
 *  - Событий нет — активного 0.
 *
 * Порог простоя — 60 сек (из ТЗ).
 *
 * Чистая функция, тестируется в JVM.
 */
object ActivityAccumulator {

    const val DEFAULT_IDLE_THRESHOLD_MS = 60_000L

    /**
     * Сумма активных секунд внутри сессии по её событиям.
     *
     * eventsTs — список at_ts (любой порядок, отсортируем).
     * sessionStart / sessionEnd — границы окна.
     * idleThresholdMs — порог простоя (по умолчанию 60 сек).
     *
     * Логика:
     *   activeMs = 0
     *   prev = sessionStart
     *   для каждого ts в sorted:
     *     delta = ts - prev
     *     если delta in [0, idleThreshold) — activeMs += delta
     *     prev = ts
     *   хвост: tail = sessionEnd - prev
     *   если tail in [0, idleThreshold) — activeMs += tail
     *   return activeMs / 1000
     */
    fun computeActiveSec(
        eventsTs: List<Long>,
        sessionStart: Long,
        sessionEnd: Long,
        idleThresholdMs: Long = DEFAULT_IDLE_THRESHOLD_MS
    ): Int {
        if (eventsTs.isEmpty()) return 0
        if (sessionEnd <= sessionStart) return 0
        if (idleThresholdMs <= 0) return 0

        val sorted = eventsTs
            .filter { it in sessionStart..sessionEnd }
            .sorted()
        if (sorted.isEmpty()) return 0

        var activeMs = 0L
        var prev = sessionStart

        for (ts in sorted) {
            val delta = ts - prev
            if (delta in 0 until idleThresholdMs) activeMs += delta
            prev = ts
        }

        val tail = sessionEnd - prev
        if (tail in 0 until idleThresholdMs) activeMs += tail

        return (activeMs / 1000L).toInt()
    }

    /**
     * Секунды простоя внутри foreground = fg - active.
     * Никогда не отрицательно.
     */
    fun computeIdleSec(fgSec: Int, activeSec: Int): Int =
        (fgSec - activeSec).coerceAtLeast(0)
}