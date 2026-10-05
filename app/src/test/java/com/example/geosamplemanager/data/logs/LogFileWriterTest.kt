package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * FIX 5.9-logs-7:
 * Проверяем новый формат записи: Unicode-маркеры, шапка дня,
 * раскрытие details. Тесты используют UTC — результат не
 * зависит от TZ машины.
 */
class LogFileWriterTest {

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

    private fun entry(
        createdAt: Long,
        level: String = "info",
        category: String = "app",
        summary: String = "Приложение запущено",
        details: String? = null
    ) = LogEntry(
        id = 1,
        createdAt = createdAt,
        sessionId = "s1",
        category = category,
        level = level,
        summary = summary,
        details = details
    )

    // ============================================================
    // Базовый формат
    // ============================================================

    @Test
    fun infoLineHasCircleMarkerAndTimeOnly() {
        val t = ts(2026, 10, 5, 14, 32, 5)
        val line = LogFileWriter.formatEntry(entry(t), utc)
        assertTrue(line.startsWith(" ●  14:32:05"))
        assertTrue(line.contains("Приложение"))
        assertTrue(line.endsWith("Приложение запущено"))
    }

    @Test
    fun errorLineHasCrossMarker() {
        val t = ts(2026, 10, 5, 14, 36, 12)
        val line = LogFileWriter.formatEntry(
            entry(
                t,
                level = "error",
                category = "error",
                summary = "Не удалось прочитать бэкап"
            ),
            utc
        )
        assertTrue(line.startsWith(" ✕  14:36:12"))
        assertTrue(line.contains("Ошибки"))
    }

    @Test
    fun warnLineHasWarningMarker() {
        val t = ts(2026, 10, 5, 14, 35, 12)
        val line = LogFileWriter.formatEntry(
            entry(t, level = "warn", category = "db", summary = "Медленный запрос"),
            utc
        )
        assertTrue(line.startsWith(" ⚠  14:35:12"))
        assertTrue(line.contains("База данных"))
    }

    @Test
    fun noDateInEntryOnlyTime() {
        val t = ts(2026, 10, 5, 14, 32, 5)
        val line = LogFileWriter.formatEntry(entry(t), utc)
        // В записи не должно быть полной даты — только время.
        assertEquals(false, line.contains("05.10.2026"))
        assertTrue(line.contains("14:32:05"))
    }

    // ============================================================
    // Details
    // ============================================================

    @Test
    fun detailsExpandedToKeyValueLines() {
        val t = ts(2026, 10, 5, 14, 36, 12)
        val line = LogFileWriter.formatEntry(
            entry(
                t,
                details = "{\"error_type\":\"FileNotFoundException\",\"file\":\"x.gsmbackup\"}"
            ),
            utc
        )
        assertTrue(line.contains("\n"))
        assertTrue(line.contains("└ error_type: FileNotFoundException"))
        assertTrue(line.contains("└ file: x.gsmbackup"))
    }

    @Test
    fun nullDetailsLeavesSingleLine() {
        val t = ts(2026, 10, 5, 14, 32, 5)
        val line = LogFileWriter.formatEntry(entry(t, details = null), utc)
        assertEquals(false, line.contains("\n"))
        assertEquals(false, line.contains("└"))
    }

    @Test
    fun invalidJsonFallsBackToSingleValue() {
        val t = ts(2026, 10, 5, 14, 32, 5)
        val line = LogFileWriter.formatEntry(
            entry(t, details = "not-a-json"),
            utc
        )
        assertTrue(line.contains("└ not-a-json"))
    }

    @Test
    fun multilineSummaryCollapsedToSingleLine() {
        val t = ts(2026, 10, 5, 14, 32, 5)
        val line = LogFileWriter.formatEntry(
            entry(t, summary = "Первая строка\nВторая строка"),
            utc
        )
        // \n в summary недопустим — заменяется пробелом.
        assertEquals(1, line.lines().size)
        assertTrue(line.contains("Первая строка"))
        assertTrue(line.contains("Вторая строка"))
    }

    // ============================================================
    // Шапка дня
    // ============================================================

    @Test
    fun dayHeaderHasCorrectDate() {
        val header = LogFileWriter.formatDayHeader("2026-10-05")
        assertTrue(header.contains("05.10.2026"))
    }

    @Test
    fun dayHeaderHasDividers() {
        val header = LogFileWriter.formatDayHeader("2026-10-05")
        assertTrue(header.contains("━"))
        // Две линии-разделителя.
        assertEquals(2, header.lines().count { it.startsWith("━") })
    }

    @Test
    fun dayHeaderUnknownFormatKeepsRaw() {
        val header = LogFileWriter.formatDayHeader("not-a-date")
        assertTrue(header.contains("not-a-date"))
    }
}