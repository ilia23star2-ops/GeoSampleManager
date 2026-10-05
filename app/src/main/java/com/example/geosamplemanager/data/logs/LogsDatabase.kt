package com.example.geosamplemanager.data.logs

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * FIX 5.9-logs-1:
 * Отдельная БД для журнала. Файл logs.db в filesDir.
 *
 * Почему отдельная: основная БД стирается при clean и
 * заменяется при restore / rollback / merge — журнал должен
 * пережить эти операции, включая сам факт их выполнения.
 *
 * Версия 1 — первая, миграций пока нет. exportSchema = false.
 */
@Database(
    entities = [LogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class LogsDatabase : RoomDatabase() {

    abstract fun logDao(): LogDao

    companion object {

        @Volatile
        private var INSTANCE: LogsDatabase? = null

        fun getInstance(context: Context): LogsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LogsDatabase::class.java,
                    "logs.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Закрыть соединение и сбросить синглтон.
         * Нужно для симметрии с AppDatabase и для тестов.
         */
        fun closeAndReset() {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Exception) {}
                INSTANCE = null
            }
        }
    }
}