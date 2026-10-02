package com.example.geosamplemanager.data.backup

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * FIX 5.9-db-rollback:
 * Одна точка авто-отката в filesDir/db_backups/.
 *
 * createdAt — timestamp, извлечённый из имени файла
 * (pre_restore_YYYYMMDD_HHmm.gsmbackup), а не lastModified.
 * Manifest подтягивается опционально (см. list(dir, manifestReader)).
 */
data class RollbackBackup(
    val file: File,
    val createdAt: Long,
    val sizeBytes: Long,
    val manifest: BackupManifest?
)

object RollbackBackups {

    const val NAME_PREFIX = "pre_restore_"
    const val NAME_SUFFIX = ".gsmbackup"

    /** Сколько последних pre_restore_* держим на диске. */
    const val MAX_KEEP = 5

    private const val NAME_PATTERN = "yyyyMMdd_HHmm"
    private const val DATE_PART_LENGTH = 13 // YYYYMMDD_HHmm

    /**
     * Парсит timestamp из имени pre_restore_YYYYMMDD_HHmm.gsmbackup.
     * Возвращает null, если имя не подходит по префиксу, суффиксу,
     * длине или формату даты.
     */
    fun parseTimestamp(fileName: String): Long? {
        if (!fileName.startsWith(NAME_PREFIX)) return null
        if (!fileName.endsWith(NAME_SUFFIX)) return null

        val datePart = fileName
            .removePrefix(NAME_PREFIX)
            .removeSuffix(NAME_SUFFIX)
        if (datePart.length != DATE_PART_LENGTH) return null

        return try {
            val sdf = SimpleDateFormat(NAME_PATTERN, Locale.US).apply {
                isLenient = false
            }
            sdf.parse(datePart)?.time
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Список pre_restore_* из папки, отсортированный по убыванию даты
     * (новые — сверху). Не-файлы и посторонние имена отбрасываются.
     *
     * @param manifestReader читает manifest.json из конкретного файла;
     *        передаётся, чтобы не делать IO в юнит-тестах.
     */
    fun list(
        dir: File,
        manifestReader: (File) -> BackupManifest? = { null }
    ): List<RollbackBackup> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()

        return files
            .asSequence()
            .filter { it.isFile }
            .mapNotNull { file ->
                val ts = parseTimestamp(file.name) ?: return@mapNotNull null
                RollbackBackup(
                    file = file,
                    createdAt = ts,
                    sizeBytes = file.length(),
                    manifest = manifestReader(file)
                )
            }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    /**
     * Оставляет N самых свежих pre_restore_*, остальные удаляет.
     * Возвращает список реально удалённых файлов.
     */
    fun rotate(dir: File, keep: Int = MAX_KEEP): List<File> {
        if (keep < 0) return emptyList()
        val backups = list(dir)
        if (backups.size <= keep) return emptyList()

        val toDelete = backups.drop(keep)
        val deleted = mutableListOf<File>()
        for (b in toDelete) {
            try {
                if (b.file.delete()) deleted += b.file
            } catch (_: Exception) {
                // Не критично: файл останется, следующая ротация уберёт.
            }
        }
        return deleted
    }
}