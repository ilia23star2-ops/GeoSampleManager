package com.example.geosamplemanager.data.stats

/**
 * FIX 5.10-stat-session:
 * Чистый трекер времени сессии. Считает секунды в foreground
 * и background. Не зависит от Android — тестируется в JVM.
 *
 * Логика:
 *  - resume(now) — из onResume Activity. Закрывает интервал
 *    background, открывает foreground.
 *  - pause(now) — из onPause Activity. Закрывает интервал
 *    foreground, открывает background.
 *  - flush(now) — принудительно закрывает открытый интервал
 *    (вызывается при закрытии сессии).
 *
 * Защита от двойных вызовов:
 *  - resume при уже открытом foreground — no-op.
 *  - pause при уже открытом background — no-op.
 *
 * На первой версии:
 *  - active_sec = fg_sec;
 *  - idle_sec = bg_sec;
 *  - screen_off_sec = 0 (не отделяем).
 * Разделение fg на active/idle — задача 5.10-stat-activity.
 */
class SessionTimeAccumulator {

    var fgSec: Int = 0
        private set

    var bgSec: Int = 0
        private set

    private var fgStartedAt: Long? = null
    private var bgStartedAt: Long? = null

    fun resume(now: Long) {
        if (fgStartedAt != null) return

        bgStartedAt?.let { start ->
            bgSec += secondsBetween(start, now)
            bgStartedAt = null
        }
        fgStartedAt = now
    }

    fun pause(now: Long) {
        if (bgStartedAt != null) return

        fgStartedAt?.let { start ->
            fgSec += secondsBetween(start, now)
            fgStartedAt = null
        }
        bgStartedAt = now
    }

    /**
     * Закрывает любой открытый интервал — используется при
     * закрытии сессии.
     */
    fun flush(now: Long) {
        fgStartedAt?.let { start ->
            fgSec += secondsBetween(start, now)
            fgStartedAt = null
        }
        bgStartedAt?.let { start ->
            bgSec += secondsBetween(start, now)
            bgStartedAt = null
        }
    }

    private fun secondsBetween(from: Long, to: Long): Int {
        if (to <= from) return 0
        return ((to - from) / 1000L).toInt()
    }
}