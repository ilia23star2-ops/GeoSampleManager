package com.example.geosamplemanager.data.logs

import android.content.Context
import com.example.geosamplemanager.data.stats.AutoWarnRules
import com.example.geosamplemanager.data.stats.EventEntity
import com.example.geosamplemanager.data.stats.SessionTracker
import com.example.geosamplemanager.data.stats.StatsDao
import com.example.geosamplemanager.data.stats.StatsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * FIX 5.9-logs-2: центральный писатель журнала.
 * FIX 5.10-stat-activity-a: параллельно каждая запись — в stats.db.
 * FIX 5.10-stat-errors-b: авто-повышение в warn через AutoWarnRules.
 *
 * FIX 5.10-logs-cleanup-b:
 *  - logs.db и LogFileWriter удалены. Единственное хранилище —
 *    stats.db.events;
 *  - убраны logDao, trimToMaxEntries, MAX_ENTRIES, TRIM_AFTER_WRITES;
 *  - sessionId (UUID) остаётся: используется CrashHandler'ом для
 *    метки crash_session.
 */
object LogWriter {

    private const val CHANNEL_CAPACITY = 1000
    private const val BATCH_SIZE = 50
    private const val FLUSH_INTERVAL_MS = 500L

    private var appContext: Context? = null
    private var writerJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<LogEntry>(capacity = CHANNEL_CAPACITY)

    private val sessionId: String = UUID.randomUUID().toString()

    fun init(context: Context) {
        if (writerJob != null) return
        synchronized(this) {
            if (writerJob != null) return
            appContext = context.applicationContext
            writerJob = scope.launch { runWriter() }
        }
    }

    fun currentSessionId(): String = sessionId

    fun send(entry: LogEntry) {
        channel.trySend(entry)
    }

    fun info(cat: LogCategory, summary: String): LogEntryBuilder =
        LogEntryBuilder(cat, LogLevel.INFO, summary, sessionId)

    fun warn(cat: LogCategory, summary: String): LogEntryBuilder =
        LogEntryBuilder(cat, LogLevel.WARN, summary, sessionId)

    fun error(
        cat: LogCategory,
        summary: String,
        t: Throwable? = null
    ): LogEntryBuilder =
        LogEntryBuilder(cat, LogLevel.ERROR, summary, sessionId)
            .detailThrowable(t)

    private suspend fun runWriter() {
        val ctx = appContext ?: return

        // FIX 5.10-stat-activity-a: один раз получаем statsDao.
        // Первое getInstance может занять время (открытие файла) —
        // это фон, не UI.
        val statsDao: StatsDao? = try {
            StatsDatabase.getInstance(ctx).statsDao()
        } catch (_: Exception) {
            null
        }

        val buffer = mutableListOf<LogEntry>()

        while (true) {
            val entry = withTimeoutOrNull(FLUSH_INTERVAL_MS) {
                channel.receive()
            }
            if (entry != null) {
                buffer += entry
                if (buffer.size >= BATCH_SIZE) {
                    flush(statsDao, buffer)
                }
            } else {
                if (buffer.isNotEmpty()) {
                    flush(statsDao, buffer)
                }
            }
        }
    }

    private suspend fun flush(
        statsDao: StatsDao?,
        buffer: MutableList<LogEntry>
    ) {
        if (buffer.isEmpty()) return
        val batch = buffer.toList().toMutableList()
        buffer.clear()

        // FIX 5.10-stat-errors-b:
        // Проверяем каждую ERROR-запись. При срабатывании порога
        // дописываем одну warn-запись в тот же батч.
        val autoWarns = mutableListOf<LogEntry>()
        for (entry in batch) {
            if (entry.level == LogLevel.ERROR.code) {
                if (AutoWarnRules.onError(entry.category, entry.summary)) {
                    autoWarns.add(buildAutoWarnEntry(entry))
                }
            }
        }
        if (autoWarns.isNotEmpty()) {
            batch.addAll(autoWarns)
        }

        // FIX 5.10-logs-cleanup-b: единственное хранилище —
        // stats.db.events. logs.db и файловый архив удалены.
        if (statsDao != null) {
            try {
                val sid = SessionTracker.currentId() ?: 0L
                val events = batch.map { entry ->
                    EventEntity(
                        sessionId = sid,
                        atTs = entry.createdAt,
                        level = entry.level,
                        category = entry.category,
                        summary = entry.summary,
                        detailsJson = entry.details,
                        rawVoice = null,
                        parsedVoice = null
                    )
                }
                statsDao.insertEvents(events)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * FIX 5.10-stat-errors-b:
     * Сборка warn-записи про повторяющуюся ошибку. Всегда категория
     * ERROR, уровень WARN. В details — ссылка на исходную запись.
     */
    private fun buildAutoWarnEntry(source: LogEntry): LogEntry {
        val detailsMap = linkedMapOf<String, Any?>(
            "auto_warn" to true,
            "source_category" to source.category,
            "source_summary" to source.summary,
            "source_at" to source.createdAt
        )
        return LogEntry(
            createdAt = System.currentTimeMillis(),
            sessionId = source.sessionId,
            category = LogCategory.ERROR.code,
            level = LogLevel.WARN.code,
            summary = "Повторяется ошибка: ${source.summary}",
            details = DetailsJson.encode(detailsMap)
        )
    }
}