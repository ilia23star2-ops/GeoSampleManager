package com.example.geosamplemanager.data.logs

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * FIX 5.9-logs-1:
 * Форматирование времени для журнала.
 *
 * full() — «05.10.2026 14:32:05» — полная дата и время.
 * timeOnly() — «14:32:05» — только время в пределах дня.
 *
 * TimeZone — параметр (по умолчанию системный). Так тесты
 * детерминированы без зависимости от TZ машины.
 *
 * SimpleDateFormat не потокобезопасен — создаём инстанс на
 * каждый вызов. Для журнала частота вызовов низкая.
 */
object LogFormatter {

    private const val FULL_PATTERN = "dd.MM.yyyy HH:mm:ss"
    private const val TIME_PATTERN = "HH:mm:ss"

    private val ruLocale = Locale("ru", "RU")

    fun full(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat(FULL_PATTERN, ruLocale).apply {
            timeZone = tz
        }
        return sdf.format(Date(ms))
    }

    fun timeOnly(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat(TIME_PATTERN, ruLocale).apply {
            timeZone = tz
        }
        return sdf.format(Date(ms))
    }
}