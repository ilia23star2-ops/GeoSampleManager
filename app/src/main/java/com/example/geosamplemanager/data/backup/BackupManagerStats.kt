package com.example.geosamplemanager.data.backup

import java.util.Locale

/**
 * FIX 5.9-db-backup-manager:
 * Сводка по списку бэкапов для диалога управления.
 *
 * Чистая логика: считается по уже загруженному списку.
 * Покрывается юнит-тестами.
 */
data class BackupManagerSummary(
    val totalCount: Int,
    val totalSizeBytes: Long,
    val privateCount: Int,
    val publicCount: Int,
    val autoCount: Int,
    val exportCount: Int
)

object BackupManagerStats {

    /**
     * Свернуть список бэкапов в сводку.
     *
     *  - totalCount / totalSizeBytes — всё вместе.
     *  - privateCount / publicCount — по источнику.
     *  - autoCount — с операцией restore/rollback/clean.
     *  - exportCount — всё остальное (экспорт, unknown).
     */
    fun summarize(backups: List<RollbackBackup>): BackupManagerSummary {
        var totalSize = 0L
        var privateCount = 0
        var publicCount = 0
        var autoCount = 0
        var exportCount = 0

        for (b in backups) {
            totalSize += b.sizeBytes
            when (b.source) {
                BackupSource.PRIVATE -> privateCount++
                BackupSource.PUBLIC -> publicCount++
            }
            if (b.operation in RollbackBackups.OPERATIONS) {
                autoCount++
            } else {
                exportCount++
            }
        }

        return BackupManagerSummary(
            totalCount = backups.size,
            totalSizeBytes = totalSize,
            privateCount = privateCount,
            publicCount = publicCount,
            autoCount = autoCount,
            exportCount = exportCount
        )
    }

    /**
     * Форматирование размера. Единый вид для всех диалогов БД.
     *   "< 1 КБ"  → "<bytes> Б"
     *   "< 1 МБ"  → "<N> КБ"
     *   "≥ 1 МБ"  → "N.N МБ"
     */
    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f МБ", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f КБ", kb)
            else -> "$bytes Б"
        }
    }
}