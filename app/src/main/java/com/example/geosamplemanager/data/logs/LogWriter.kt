package com.example.geosamplemanager.data.logs

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * FIX 5.9-logs-2:
 * Центральный писатель журнала.
 *
 * Схема:
 *   вызывающий код → LogWriter.send(entry) → Channel<LogEntry>(1000)
 *   → корутина-писатель в Dispatchers.IO → batch insert в LogsDatabase
 *
 * Ключевые решения:
 *  - trySend (не suspend) — не блокирует UI, даже если БД занята.
 *    При переполнении канала запись теряется, приложение не падает.
 *  - BATCH_SIZE = 50 — батч-вставка раз в 50 записей.
 *  - FLUSH_INTERVAL_MS = 500 — принудительный сброс, если батч
 *    не собрался (чтобы свежая запись не висела в буфере).
 *  - MAX_ENTRIES = 10 000 — ротация при вставке (не на каждой, а
 *    раз в TRIM_AFTER_WRITES записей).
 *
 * sessionId генерируется один раз при загрузке класса (то есть
 * живёт до перезапуска процесса). Все записи сессии помечены им —
 * это позволяет восстановить ход одной сессии в UI.
 *
 * FIX 5.9-logs-6:
 * Параллельно с записью в logs.db каждая запись уходит в
 * LogFileWriter (файловый архив в Загрузках). Ошибки файла
 * не влияют на основную запись.
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

    /** Идентификатор текущей сессии (жизнь процесса). */
    private val sessionId: String = UUID.randomUUID().toString()

    /**
     * Запустить писателя. Вызывается из GeoSampleApp.onCreate.
     * Идемпотентно — повторный вызов no-op.
     */
    fun init(context: Context) {
        if (writerJob != null) return
        synchronized(this) {
            if (writerJob != null) return
            appContext = context.applicationContext
            writerJob = scope.launch { runWriter() }
        }
    }

    fun currentSessionId(): String = sessionId

    /**
     * Неблокирующая отправка записи. Если канал переполнен —
     * запись теряется (без шума в logcat, чтобы не создавать
     * каскад при отладке).
     */
    fun send(entry: LogEntry) {
        channel.trySend(entry)
    }

    // ================================================================
    // Factory для builder'ов — используется Log.* и внутренним кодом
    // ================================================================

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

    // ================================================================
    // Внутренний цикл писателя
    // ================================================================

    private suspend fun runWriter() {
        val ctx = appContext ?: return
        val dao = try {
            LogsDatabase.getInstance(ctx).logDao()
        } catch (_: Exception) {
            return
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
                    writtenSinceTrim += flush(ctx, dao, buffer)
                    if (writtenSinceTrim >= TRIM_AFTER_WRITES) {
                        try {
                            dao.trimToMaxEntries(MAX_ENTRIES)
                        } catch (_: Exception) {
                        }
                        writtenSinceTrim = 0
                    }
                }
            } else {
                // Тайм-аут — сбрасываем то, что успело накопиться.
                if (buffer.isNotEmpty()) {
                    writtenSinceTrim += flush(ctx, dao, buffer)
                }
            }
        }
    }

    /**
     * FIX 5.9-logs-6:
     * Батч уходит одновременно в logs.db и в файл (по дням).
     * Сначала — БД, потом — файл. Ошибка файла не влияет на
     * результат возврата (считаем записанным то, что в БД).
     */
    private suspend fun flush(
        ctx: Context,
        dao: LogDao,
        buffer: MutableList<LogEntry>
    ): Int {
        if (buffer.isEmpty()) return 0
        val batch = buffer.toList()
        buffer.clear()

        val written = try {
            dao.insertAll(batch)
            batch.size
        } catch (_: Exception) {
            0
        }

        // Файл — после БД. Ошибки глотаются внутри LogFileWriter.
        try {
            LogFileWriter.appendBatch(ctx, batch)
        } catch (_: Exception) {
        }

        return written
    }
}