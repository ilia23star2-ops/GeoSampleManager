package com.example.geosamplemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.geosamplemanager.data.dao.AreaDao
import com.example.geosamplemanager.data.dao.OrderDao
import com.example.geosamplemanager.data.dao.OrderWellDao
import com.example.geosamplemanager.data.dao.SampleDao
import com.example.geosamplemanager.data.dao.SampleImageDao
import com.example.geosamplemanager.data.dao.SampleNoteDao
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.OrderWellEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import java.io.File

/**
 * FIX 5.9-db-restore-v2:
 *  - добавлен closeAndReset().
 *
 * FIX 5.9-db-merge-v2/1:
 *  - добавлен buildTemp() — открыть БД по произвольному пути
 *    (для чтения архива при слиянии). Не влияет на синглтон.
 */
@Database(
    entities = [
        AreaEntity::class,
        OrderEntity::class,
        SampleEntity::class,
        OrderWellEntity::class,
        SampleNoteEntity::class,
        SampleImageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun areaDao(): AreaDao
    abstract fun orderDao(): OrderDao
    abstract fun sampleDao(): SampleDao
    abstract fun orderWellDao(): OrderWellDao
    abstract fun sampleNoteDao(): SampleNoteDao
    abstract fun sampleImageDao(): SampleImageDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Миграция 1 → 2 (этап 5.5.1 — заметки и фото).
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE samples ADD COLUMN has_photo INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sample_notes_new (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sample_id    INTEGER NOT NULL,
                        note_text    TEXT,
                        created_date INTEGER NOT NULL,
                        FOREIGN KEY(sample_id) REFERENCES samples(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO sample_notes_new (id, sample_id, note_text, created_date)
                    SELECT id, sample_id, note_text, created_date FROM sample_notes
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE sample_notes")
                db.execSQL("ALTER TABLE sample_notes_new RENAME TO sample_notes")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sample_notes_sample_id " +
                            "ON sample_notes(sample_id)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sample_images (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sample_id    INTEGER NOT NULL,
                        image_path   TEXT NOT NULL,
                        created_date INTEGER NOT NULL,
                        FOREIGN KEY(sample_id) REFERENCES samples(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sample_images_sample_id " +
                            "ON sample_images(sample_id)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geosamples.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * FIX 5.9-db-restore-v2:
         * Закрыть текущее соединение и сбросить синглтон.
         */
        fun closeAndReset() {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Exception) {}
                INSTANCE = null
            }
        }

        /**
         * FIX 5.9-db-merge-v2/1:
         * Открыть AppDatabase по произвольному пути — используется
         * для чтения временной БД архива при слиянии. Не пишет в
         * синглтон INSTANCE, вызывающий обязан закрыть вручную.
         */
        fun buildTemp(context: Context, file: File): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                file.absolutePath
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}