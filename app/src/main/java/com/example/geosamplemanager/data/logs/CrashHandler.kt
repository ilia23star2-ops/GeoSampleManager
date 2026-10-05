package com.example.geosamplemanager.data.logs

import android.content.Context
import java.io.File

/**
 * FIX 5.9-logs-3:
 * Глобальный перехватчик необработанных исключений.
 *
 * Что делает:
 *  1. При падении — пишет CrashRecord в файл
 *     filesDir/logs/pending_crash.json.
 *  2. Передаёт управление дефолтному хендлеру — приложение
 *     падает как обычно, но с сохранённой причиной.
 *  3. При следующем старте — читает файл, возвращает CrashRecord
 *     вызывающему (GeoSampleApp), файл удаляется.
 *
 * install() идемпотентен: повторный вызов no-op.
 */
object CrashHandler {

    private const val LOGS_DIR = "logs"
    private const val CRASH_FILE = "pending_crash.json"

    @Volatile
    private var installed = false

    /**
     * Установить хендлер. Вызывается один раз в GeoSampleApp.onCreate.
     */
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true

            val appContext = context.applicationContext
            val default = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    writeCrashFile(appContext, thread, throwable)
                } catch (_: Exception) {
                    // Файловая система может быть недоступна —
                    // не мешаем приложению падать как обычно.
                }
                // Передаём управление дальше — пусть система
                // сделает своё (logcat, диалог «приложение
                // остановлено»). Не глотаем краш.
                default?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Прочитать сохранённую запись о падении (если была) и удалить
     * файл. Возвращает null, если падения не было или файл повреждён.
     */
    fun readAndClear(context: Context): CrashRecord? {
        return try {
            val file = crashFile(context)
            if (!file.exists()) return null
            val text = file.readText()
            file.delete()
            CrashRecord.decode(text)
        } catch (_: Exception) {
            null
        }
    }

    // ================================================================
    // Internal
    // ================================================================

    private fun writeCrashFile(
        context: Context,
        thread: Thread,
        throwable: Throwable
    ) {
        val record = CrashRecord(
            timestamp = System.currentTimeMillis(),
            sessionId = LogWriter.currentSessionId(),
            threadName = thread.name,
            exceptionType = throwable::class.qualifiedName
                ?: throwable::class.simpleName
                ?: "Unknown",
            message = throwable.message,
            stackTrace = throwable.stackTraceToString()
        )
        val file = crashFile(context)
        file.parentFile?.mkdirs()
        file.writeText(record.encode())
    }

    private fun crashFile(context: Context): File =
        File(File(context.filesDir, LOGS_DIR), CRASH_FILE)
}