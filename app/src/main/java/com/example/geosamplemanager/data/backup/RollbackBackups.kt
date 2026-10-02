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
 *
 * FIX 5.9-db-backups-ops:
 *  - поддержка трёх префиксов: pre_restore_, pre_rollback_, pre_clean_;
 *  - поле operation — после какой операции сделан бэкап;
 *  - rotateByPrefix — ротация отдельно по каждому префиксу
 *    (5 на каждый).
 */
data class RollbackBackup(
    val file: File,
    val operation: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val manifest: BackupManifest?
)

data class ParsedName(
    val operation: String,
    val createdAt: Long
)

object RollbackBackups {

    /** Известные операции авто-бэкапа. */
    val OPERATIONS: List<String> = listOf("restore", "rollback", "clean")

    const val NAME_SUFFIX = ".gsmbackup"

    /** Сколько последних pre_*_ держим на диске — для каждой операции. */
    const val MAX_KEEP = 5

    private const val NAME_PATTERN = "yyyyMMdd_HHmm"
    private const val DATE_PART_LENGTH = 13 // YYYYMMDD_HHmm

    /** Префикс имени файла для операции. */
    fun prefixFor(operation: String): String = "pre_${operation}_"

    /**
     * Парсит имя pre_<operation>_YYYYMMDD_HHmm.gsmbackup.
     * Возвращает operation и timestamp, либо null.
     */
    fun parseFileName(fileName: String): ParsedName? {
        if (!fileName.endsWith(NAME_SUFFIX)) return null
        val body = fileName.removeSuffix(NAME_SUFFIX)

        for (op in OPERATIONS) {
            val prefix = prefixFor(op)
            if (!body.startsWith(prefix)) continue

            val datePart = body.removePrefix(prefix)
            if (datePart.length != DATE_PART_LENGTH) continue

            val ts = tryParseDate(datePart) ?: continue
            return ParsedName(operation = op, createdAt = ts)
        }
        return null
    }

    private fun tryParseDate(datePart: String): Long? {
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
     * Список pre_*_* из папки, отсортированный по убыванию даты.
     * Не-файлы и посторонние имена отбрасываются.
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
                val parsed = parseFileName(file.name) ?: return@mapNotNull null
                RollbackBackup(
                    file = file,
                    operation = parsed.operation,
                    createdAt = parsed.createdAt,
                    sizeBytes = file.length(),
                    manifest = manifestReader(file)
                )
            }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    /**
     * FIX 5.9-db-backups-ops:
     * Ротация отдельно по каждой операции (префиксу).
     * Оставляем N последних в каждой группе, остальные удаляем.
     *
     * @return список реально удалённых файлов.
     */
    fun rotateByPrefix(
        dir: File,
        keep: Int = MAX_KEEP
    ): List<File> {
        if (keep < 0) return emptyList()
        val all = list(dir)
        if (all.isEmpty()) return emptyList()

        val deleted = mutableListOf<File>()
        for (op in OPERATIONS) {
            val group = all.filter { it.operation == op }
            if (group.size <= keep) continue
            group.drop(keep).forEach { b ->
                try {
                    if (b.file.delete()) deleted += b.file
                } catch (_: Exception) {
                    // Не критично: файл останется, следующая ротация уберёт.
                }
            }
        }
        return deleted
    }
}