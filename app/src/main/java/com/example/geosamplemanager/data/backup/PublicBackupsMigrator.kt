package com.example.geosamplemanager.data.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * FIX 5.9-db-backups-ops/2:
 * Одноразовая миграция старых бэкапов из корня
 * Downloads/GeoSampleManager/ в подпапки:
 *
 *   GeoSampleManager/
 *     pre_restore/
 *     pre_rollback/
 *     pre_clean/
 *     exports/
 *
 * Запускается лениво при первом открытии вкладки БД,
 * помечается флагом в SharedPreferences.
 *
 * Все ошибки глотаются — это фоновая задача, падать нельзя.
 */
object PublicBackupsMigrator {

    /** Корневая папка в Загрузках. */
    const val ROOT_DIR = "GeoSampleManager"

    /** Подпапка для пользовательских экспортов. */
    const val EXPORTS_DIR = "exports"

    /** SharedPreferences для флага миграции. */
    const val PREFS_NAME = "db_backups"
    const val KEY_MIGRATED = "public_backups_migrated_v1"

    /**
     * Какая подпапка для файла. Чистая функция — тестируется без IO.
     *
     *  - pre_restore_*.gsmbackup  → "pre_restore"
     *  - pre_rollback_*.gsmbackup → "pre_rollback"
     *  - pre_clean_*.gsmbackup    → "pre_clean"
     *  - любой другой .gsmbackup  → "exports"
     *  - не .gsmbackup            → null (не мигрируем)
     */
    fun subdirFor(fileName: String): String? {
        if (!fileName.endsWith(".${GsmBackupWriter.EXTENSION}")) return null
        for (op in RollbackBackups.OPERATIONS) {
            if (fileName.startsWith(RollbackBackups.prefixFor(op))) {
                return "pre_$op"
            }
        }
        return EXPORTS_DIR
    }

    /**
     * Запустить миграцию. Возвращает число перемещённых файлов.
     * Все исключения глотаются: фоновая задача.
     */
    fun migrate(context: Context): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                migrateViaMediaStore(context)
            } else {
                migrateViaFileSystem()
            }
        } catch (_: Exception) {
            0
        }
    }

    // ================================================================
    // Android 10+ — через MediaStore
    // ================================================================

    private fun migrateViaMediaStore(context: Context): Int {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%$ROOT_DIR%")

        // Пары (id, name) файлов, лежащих в КОРНЕ GeoSampleManager,
        // без подпапок.
        val toMove = mutableListOf<Pair<Long, String>>()

        resolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: continue
                val path = c.getString(pathCol) ?: continue

                // RELATIVE_PATH корня — "Download/GeoSampleManager/"
                // (у некоторых вендоров "Downloads/"). У подпапки —
                // ".../GeoSampleManager/pre_restore/".
                // Отличаем по последнему сегменту.
                val lastSegment = path.trimEnd('/').substringAfterLast('/')
                if (lastSegment != ROOT_DIR) continue

                toMove += id to name
            }
        }

        if (toMove.isEmpty()) return 0

        var count = 0
        for ((oldId, name) in toMove) {
            val subdir = subdirFor(name) ?: continue
            val oldUri = collection.buildUpon()
                .appendPath(oldId.toString())
                .build()

            val newValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_DIR/$subdir"
                )
            }
            val newUri = resolver.insert(collection, newValues) ?: continue

            val moved = try {
                val input = resolver.openInputStream(oldUri)
                    ?: throw IllegalStateException("no input")
                val output = resolver.openOutputStream(newUri)
                    ?: throw IllegalStateException("no output")
                input.use { i -> output.use { o -> i.copyTo(o) } }
                true
            } catch (_: Exception) {
                false
            }

            if (moved) {
                try { resolver.delete(oldUri, null, null) } catch (_: Exception) {}
                count++
            } else {
                try { resolver.delete(newUri, null, null) } catch (_: Exception) {}
            }
        }
        return count
    }

    // ================================================================
    // Android 9 и ниже — через прямые File
    // ================================================================

    private fun migrateViaFileSystem(): Int {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val root = File(downloads, ROOT_DIR)
        if (!root.exists() || !root.isDirectory) return 0

        val files = root.listFiles() ?: return 0
        var count = 0

        for (f in files) {
            if (!f.isFile) continue
            val subdir = subdirFor(f.name) ?: continue

            val destDir = File(root, subdir)
            if (!destDir.exists()) destDir.mkdirs()

            val dest = File(destDir, f.name)
            if (dest.exists()) continue

            try {
                if (f.renameTo(dest)) count++
            } catch (_: Exception) {
                // Не критично: файл останется в корне.
            }
        }
        return count
    }
}