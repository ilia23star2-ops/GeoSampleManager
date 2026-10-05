package com.example.geosamplemanager.data.backup

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * FIX 5.9-db-rollback / 5.9-db-backups-ops / 5.9-db-rollback-public.
 *
 * FIX 5.9-db-diagnostics:
 *  - в OPERATIONS добавлена операция "diagnostics" — авто-бэкап
 *    pre_diagnostics_* создаётся перед исправлениями на вкладке БД;
 *  - добавление подхватывается автоматически в:
 *      RollbackBackups.parseFileName / rotateByPrefix,
 *      PublicBackupsLister.isAutoBackupSubDir / rotateAutoBackups,
 *      PublicBackupsMigrator.subdirFor.
 */
enum class BackupSource { PRIVATE, PUBLIC }

data class RollbackBackup(
    val fileName: String,
    val source: BackupSource,
    val file: File?,
    val publicUri: String?,
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

    val OPERATIONS: List<String> =
        listOf("restore", "rollback", "clean", "diagnostics")

    const val NAME_SUFFIX = ".gsmbackup"

    const val MAX_KEEP = 5

    private const val NAME_PATTERN = "yyyyMMdd_HHmm"
    private const val DATE_PART_LENGTH = 13

    fun prefixFor(operation: String): String = "pre_${operation}_"

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
                    fileName = file.name,
                    source = BackupSource.PRIVATE,
                    file = file,
                    publicUri = null,
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
     * FIX 5.9-db-rollback-public:
     * PublicBackup → RollbackBackup. publicUri — уже строка, без
     * Android-зависимостей.
     */
    fun fromPublic(pb: PublicBackup): RollbackBackup {
        val parsed = parseFileName(pb.displayName)
        val createdAt = parsed?.createdAt ?: pb.lastModified
        return RollbackBackup(
            fileName = pb.displayName,
            source = BackupSource.PUBLIC,
            file = null,
            publicUri = pb.uri,
            operation = pb.operation,
            createdAt = createdAt,
            sizeBytes = pb.sizeBytes,
            manifest = pb.manifest
        )
    }

    fun merge(
        privateList: List<RollbackBackup>,
        publicList: List<RollbackBackup>
    ): List<RollbackBackup> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<RollbackBackup>()
        for (b in privateList) {
            if (seen.add(b.fileName)) result += b
        }
        for (b in publicList) {
            if (seen.add(b.fileName)) result += b
        }
        return result.sortedByDescending { it.createdAt }
    }

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
                val f = b.file ?: return@forEach
                try {
                    if (f.delete()) deleted += f
                } catch (_: Exception) {
                }
            }
        }
        return deleted
    }
}