package com.example.geosamplemanager.data.stats

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * FIX 5.10-stat-model:
 * Отдельная БД теневой статистики. Не в основной схеме —
 * переживает очистку и восстановление geosamples.db.
 *
 * Путь: filesDir/stats/active.db
 * (не getDatabasePath(), потому что там — основная БД; нужна
 * своя папка, чтобы ротация по месяцам складывала архив рядом).
 *
 * Версия 1 — первая, миграций нет. exportSchema = false.
 *
 * Синглтон + closeAndReset + buildTemp — по образцу LogsDatabase
 * и AppDatabase.
 */
@Database(
    entities = [
        SessionEntity::class,
        TabVisitEntity::class,
        OrderWorkEntity::class,
        EventEntity::class,
        DailySummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StatsDatabase : RoomDatabase() {

    abstract fun statsDao(): StatsDao

    companion object {

        @Volatile
        private var INSTANCE: StatsDatabase? = null

        private const val DIR_NAME = "stats"
        private const val FILE_NAME = "active.db"

        /**
         * Папка со статистикой. Создаётся при первом обращении.
         */
        fun statsDir(context: Context): File =
            File(context.filesDir, DIR_NAME)

        /**
         * Активный файл stats.db.
         */
        fun activeFile(context: Context): File =
            File(statsDir(context), FILE_NAME)

        fun getInstance(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val dir = statsDir(context)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, FILE_NAME)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    file.absolutePath
                ).build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Закрыть соединение и сбросить синглтон.
         * Используется при выходе из приложения и при ротации.
         */
        fun closeAndReset() {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Exception) {}
                INSTANCE = null
            }
        }

        /**
         * Открыть StatsDatabase по произвольному пути — для чтения
         * архива месяца. Не пишет в синглтон.
         */
        fun buildTemp(context: Context, file: File): StatsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                StatsDatabase::class.java,
                file.absolutePath
            ).build()
        }
    }
}