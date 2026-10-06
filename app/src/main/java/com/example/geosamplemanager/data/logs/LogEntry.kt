package com.example.geosamplemanager.data.logs

/**
 * FIX 5.9-logs-1: запись журнала.
 *
 * FIX 5.10-logs-cleanup-b:
 *  - убраны Room-аннотации — класс больше не связан с БД;
 *  - logs.db удалена, журнал живёт в stats.db.events (см. LogWriter);
 *  - LogEntry — только промежуточная модель для канала LogWriter.
 *
 * summary — человекочитаемая русская фраза для UI. Полное
 * предложение в прошедшем времени, без английского и без
 * технического жаргона.
 *
 * details — JSON с контекстом и техническими данными
 * (id, коды, тип исключения, stacktrace). Раскрывается по тапу.
 */
data class LogEntry(
    val createdAt: Long,
    val sessionId: String,
    val category: String,
    val level: String,
    val summary: String,
    val details: String? = null
)