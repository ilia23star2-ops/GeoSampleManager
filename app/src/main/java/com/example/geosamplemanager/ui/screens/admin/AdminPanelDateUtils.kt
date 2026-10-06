package com.example.geosamplemanager.ui.screens.admin

import java.util.Calendar
import java.util.Locale

/**
 * FIX 5.10-stat-admin-ui-4:
 * Чистые функции работы с датой для админ-панели. Не зависят
 * от Android — тестируются в JVM.
 *
 * Формат даты — `YYYY-MM-DD` (локальная зона). Метка для UI —
 * `YYYY-MM-DD (день_недели)`, день недели по-русски двумя буквами.
 */
object AdminPanelDateUtils {

    /** Русские сокращения дней недели. Индекс 0 = воскресенье. */
    private val RU_DAYS = arrayOf("вс", "пн", "вт", "ср", "чт", "пт", "сб")

    /**
     * Сегодняшняя дата в формате `YYYY-MM-DD` по локальной зоне.
     */
    fun today(): String = formatDate(System.currentTimeMillis())

    /**
     * Границы дня `YYYY-MM-DD` в миллисекундах, локальная зона.
     * Возврат: [начало дня, начало следующего дня).
     */
    fun dayBounds(date: String): Pair<Long, Long> {
        val parts = date.split("-")
        val cal = Calendar.getInstance()
        cal.set(
            parts[0].toInt(),
            parts[1].toInt() - 1,
            parts[2].toInt(),
            0, 0, 0
        )
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }

    /**
     * Метка дня для UI: `YYYY-MM-DD (день_недели)`.
     * Пример: `2026-10-06 (вт)`.
     */
    fun label(date: String): String {
        val parts = date.split("-")
        val cal = Calendar.getInstance()
        cal.set(
            parts[0].toInt(),
            parts[1].toInt() - 1,
            parts[2].toInt(),
            0, 0, 0
        )
        val dayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
        val safe = dayIndex.coerceIn(0, RU_DAYS.size - 1)
        return "$date (${RU_DAYS[safe]})"
    }

    /**
     * Форматирование timestamp в `YYYY-MM-DD` по локальной зоне.
     */
    fun formatDate(ts: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        // Locale.US — чтобы не получить арабские цифры в локалях с другим
        // письмом. Формат — только цифры и дефис.
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }
}