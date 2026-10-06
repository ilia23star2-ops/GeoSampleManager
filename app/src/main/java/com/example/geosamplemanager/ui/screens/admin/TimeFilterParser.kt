package com.example.geosamplemanager.ui.screens.admin

/**
 * FIX 5.10-stat-admin-v2-time-filter:
 * Разбор строки фильтра времени на таймлайне.
 *
 * Поддерживаемые форматы:
 *  - `12`           → Point(12:00);
 *  - `12:30`        → Point(12:30);
 *  - `12-13`        → Range(12:00 … 13:00);
 *  - `12:00-13:30`  → Range(12:00 … 13:30).
 *
 * Пустая строка — Empty (нет фильтра, не ошибка).
 * Остальное — Invalid с текстом подсказки.
 */
object TimeFilterParser {

    private const val HINT = "Формат: 12, 12:30, 12-13, 12:00-13:30"
    private val TOKEN = Regex("^\\d{1,2}(:\\d{1,2})?$")

    fun parse(raw: String): TimeFilterParseResult {
        val s = raw.trim()
        if (s.isEmpty()) return TimeFilterParseResult.Empty

        return if (s.contains('-')) {
            val parts = s.split('-')
            if (parts.size != 2) return TimeFilterParseResult.Invalid(HINT)
            val from = parseTimeToken(parts[0].trim())
                ?: return TimeFilterParseResult.Invalid(HINT)
            val to = parseTimeToken(parts[1].trim())
                ?: return TimeFilterParseResult.Invalid(HINT)
            if (from >= to) {
                return TimeFilterParseResult.Invalid("Конец должен быть позже начала")
            }
            TimeFilterParseResult.Ok(TimeFilter.Range(from, to))
        } else {
            val p = parseTimeToken(s)
                ?: return TimeFilterParseResult.Invalid(HINT)
            TimeFilterParseResult.Ok(TimeFilter.Point(p))
        }
    }

    /** Минуты от 00:00, или null если токен невалиден. */
    private fun parseTimeToken(s: String): Int? {
        if (!TOKEN.matches(s)) return null
        val parts = s.split(':')
        val h = parts[0].toIntOrNull() ?: return null
        if (h !in 0..23) return null
        val m = if (parts.size == 2) {
            val mm = parts[1].toIntOrNull() ?: return null
            if (mm !in 0..59) return null
            mm
        } else 0
        return h * 60 + m
    }
}

/** Распарсенный фильтр времени. */
sealed class TimeFilter {
    /** Точка: 12:30 → minutes = 750. */
    data class Point(val minutes: Int) : TimeFilter()

    /** Диапазон: [fromMin, toMin), оба — минуты от 00:00. */
    data class Range(val fromMin: Int, val toMin: Int) : TimeFilter()
}

/** Результат разбора строки. */
sealed class TimeFilterParseResult {
    /** Пустая строка — фильтра нет. */
    data object Empty : TimeFilterParseResult()

    /** Успешный разбор. */
    data class Ok(val filter: TimeFilter) : TimeFilterParseResult()

    /** Невалидный ввод — показать message под полем. */
    data class Invalid(val message: String) : TimeFilterParseResult()
}