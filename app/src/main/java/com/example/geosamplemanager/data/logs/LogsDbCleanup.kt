package com.example.geosamplemanager.data.logs

import android.content.Context

/**
 * FIX 5.10-logs-cleanup-b:
 * Одноразовое удаление `logs.db` при апдейте на новую версию.
 *
 * По решению — журнал переехал в `stats.db.events` (панель
 * администратора), отдельная `logs.db` больше не нужна.
 *
 * Файл удаляется вместе с `-wal` и `-shm`. Раньше `LogsDatabase`
 * закрывалась через `closeAndReset()`, но сам класс удалён — работаем
 * с файлами напрямую.
 *
 * Безопасно вызывать при каждом старте: если файла нет — no-op.
 */
object LogsDbCleanup {

    private const val DB_NAME = "logs.db"

    fun cleanupIfNeeded(context: Context) {
        deleteFile(context, DB_NAME)
        deleteFile(context, "$DB_NAME-wal")
        deleteFile(context, "$DB_NAME-shm")
    }

    private fun deleteFile(context: Context, name: String) {
        try {
            val f = context.getDatabasePath(name)
            if (f.exists()) f.delete()
        } catch (_: Exception) {
            // Не критично — файл может быть занят, удалим при
            // следующем запуске.
        }
    }
}