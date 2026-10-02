package com.example.geosamplemanager.data.backup

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FIX 5.9-db-backup-v2:
 * Формат `.gsmbackup` — zip-архив со структурой:
 *
 *   manifest.json          — метаданные (версия формата, дата, счётчики)
 *   geosamples.db          — сама БД (SQLite)
 *   sample_photos/<файлы>  — папка с фото
 *
 * Имя расширения — .gsmbackup (внутри обычный zip, но узнаваемый).
 * Извлекается любым архиватором.
 *
 * Используется при экспорте бэкапа и (позже) при восстановлении.
 *
 * FIX 5.9-db-backups-ops:
 *  - параметр operation ("restore"/"rollback"/"clean"/"export") —
 *    пишется в манифест, чтобы UI мог показать, после какой
 *    операции сделан авто-бэкап.
 */
data class BackupCounts(
    val areas: Int,
    val orders: Int,
    val samples: Int,
    val photos: Int,
    val notes: Int
)

object GsmBackupWriter {

    /** Текущая версия формата бэкапа. */
    const val FORMAT_VERSION = 1

    /** Расширение файла. */
    const val EXTENSION = "gsmbackup"

    /** Значение operation по умолчанию — если не задано. */
    const val OP_UNKNOWN = "unknown"

    /**
     * Записать .gsmbackup в поток.
     *
     * @param out куда писать.
     * @param dbFile файл geosamples.db (уже после wal_checkpoint).
     * @param photosDir папка с фото (может не существовать).
     * @param dbSchemaVersion версия схемы БД Room.
     * @param appVersion версия приложения (для диагностики).
     * @param counts счётчики для манифеста.
     * @param operation после какой операции сделан бэкап
     *        ("restore"/"rollback"/"clean"/"export"). По умолчанию
     *        "unknown".
     * @param createdAt timestamp создания (мс). По умолчанию — now.
     */
    fun write(
        out: OutputStream,
        dbFile: File,
        photosDir: File,
        dbSchemaVersion: Int,
        appVersion: String,
        counts: BackupCounts,
        operation: String = OP_UNKNOWN,
        createdAt: Long = System.currentTimeMillis()
    ) {
        val zip = ZipOutputStream(out)

        // 1. manifest.json — первым, чтобы читалка сразу видела метаданные.
        val manifestJson = buildManifestJson(
            dbSchemaVersion = dbSchemaVersion,
            appVersion = appVersion,
            counts = counts,
            operation = operation,
            createdAt = createdAt
        )
        putEntry(zip, "manifest.json", manifestJson.toByteArray(Charsets.UTF_8))

        // 2. Сама БД.
        if (dbFile.exists()) {
            putEntry(zip, "geosamples.db", dbFile.readBytes())
        }

        // 3. Папка с фото (если есть).
        if (photosDir.exists() && photosDir.isDirectory) {
            val files = photosDir.listFiles()
            if (files != null) {
                for (f in files) {
                    if (f.isFile) {
                        putEntry(zip, "sample_photos/${f.name}", f.readBytes())
                    }
                }
            }
        }

        zip.finish()
    }

    // ================================================================
    // Сборка манифеста
    // ================================================================

    private fun buildManifestJson(
        dbSchemaVersion: Int,
        appVersion: String,
        counts: BackupCounts,
        operation: String,
        createdAt: Long
    ): String {
        val sb = StringBuilder(256)
        sb.append("{")
        sb.append("\"format_version\":").append(FORMAT_VERSION).append(",")
        sb.append("\"created_at\":").append(createdAt).append(",")
        sb.append("\"app_version\":\"").append(escapeJson(appVersion)).append("\",")
        sb.append("\"db_schema_version\":").append(dbSchemaVersion).append(",")
        sb.append("\"operation\":\"").append(escapeJson(operation)).append("\",")
        sb.append("\"counts\":{")
        sb.append("\"areas\":").append(counts.areas).append(",")
        sb.append("\"orders\":").append(counts.orders).append(",")
        sb.append("\"samples\":").append(counts.samples).append(",")
        sb.append("\"photos\":").append(counts.photos).append(",")
        sb.append("\"notes\":").append(counts.notes)
        sb.append("}")
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder(s.length + 8)
        s.forEach { c ->
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    // ================================================================
    // Утилиты
    // ================================================================

    private fun putEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        // Дату-время не задаём — берётся сейчас.
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }
}