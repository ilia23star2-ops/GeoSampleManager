package com.example.geosamplemanager.data.logs

import android.content.Context
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
 * FIX 5.9-logs-6: параллельно с logs.db — LogFileWriter.
 *
 * FIX 5.10-stat-activity-a:
 *  - параллельно с logs.db каждая запись уходит в stats.db.events;
 *  - statsDao получаем один раз при старте runWriter;
 *  - запись в stats.db — одним batch insert (одна транзакция).
 */
object LogWriter {

    private const val CHANNEL_CAPACITY = 1000
    private const val BATCH_SIZE = 50
    private const val FLUSH_INTERVAL_MS = 500L
    private const val MAX_ENTRIES = 10_000
    private const val TRIM_AFTER_WRITES = 500

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

        val logDao = try {
            LogsDatabase.getInstance(ctx).logDao()
        } catch (_: Exception) {
            return
        }

        // FIX 5.10-stat-activity-a: один раз получаем statsDao.
        // Первое getInstance может занять время (открытие файла) —
        // это фон, не UI.
        val statsDao: StatsDao? = try {
            StatsDatabase.getInstance(ctx).statsDao()
        } catch (_: Exception) {
            null
        }

        val buffer = mutableListOf<LogEntry>()
        var writtenSinceTrim = 0

        while (true) {
            val entry = withTimeoutOrNull(FLUSH_INTERVAL_MS) {
                channel.receive()
            }
            if (entry != null) {
                buffer += entry
                if (buffer.size >= BATCH_SIZE) {
                    writtenSinceTrim += flush(ctx, logDao, statsDao, buffer)
                    if (writtenSinceTrim >= TRIM_AFTER_WRITES) {
                        try {
                            logDao.trimToMaxEntries(MAX_ENTRIES)
                        } catch (_: Exception) {
                        }
                        writtenSinceTrim = 0
                    }
                }
            } else {
                if (buffer.isNotEmpty()) {
                    writtenSinceTrim += flush(ctx, logDao, statsDao, buffer)
                }
            }
        }
    }

    private suspend fun flush(
        ctx: Context,
        logDao: LogDao,
        statsDao: StatsDao?,
        buffer: MutableList<LogEntry>
    ): Int {
        if (buffer.isEmpty()) return 0
        val batch = buffer.toList()
        buffer.clear()

        val written = try {
            logDao.insertAll(batch)
            batch.size
        } catch (_: Exception) {
            0
        }

        // FIX 5.10-stat-activity-a: одна batch-вставка.
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

        try {
            LogFileWriter.appendBatch(ctx, batch)
        } catch (_: Exception) {
        }

        return written
    }
}