package com.example.geosamplemanager.data.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * FIX 5.9-db-import-picker:
 * Поиск .gsmbackup в публичных Загрузках.
 *
 * FIX 5.9-db-rollback-public:
 *  - rotateAutoBackups() — ротация публичных pre_*;
 *  - PublicBackup.uri — String.
 *
 * FIX 5.9-db-backup-manager:
 *  - listAllPublic() — все .gsmbackup (pre_* + exports);
 *  - deleteByUri() — удаление файла из MediaStore.
 */
object PublicBackupsLister {

    private const val EXT = "." + GsmBackupWriter.EXTENSION

    fun listForImport(context: Context): List<PublicBackup> =
        queryAll(context) { subDir -> subDir == PublicBackupsMigrator.EXPORTS_DIR }

    fun listAutoBackups(context: Context): List<PublicBackup> =
        queryAll(context) { subDir ->
            RollbackBackups.OPERATIONS.any { op -> subDir == "pre_$op" }
        }

    /**
     * FIX 5.9-db-backup-manager:
     * Все .gsmbackup в Загрузках/GeoSampleManager и подпапках —
     * для диалога управления бэкапами.
     */
    fun listAllPublic(context: Context): List<PublicBackup> =
        queryAll(context) { true }

    /**
     * FIX 5.9-db-backup-manager:
     * Удалить один публичный бэкап через ContentResolver.
     * На API < 29 возвращает false.
     */
    fun deleteByUri(context: Context, uriString: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ротация публичных авто-бэкапов: держим N последних на каждую
     * операцию, остальные удаляем.
     */
    fun rotateAutoBackups(
        context: Context,
        keep: Int = RollbackBackups.MAX_KEEP
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        if (keep < 0) return 0

        val all = listAutoBackups(context)
        if (all.isEmpty()) return 0

        val toDelete = mutableListOf<PublicBackup>()
        for (op in RollbackBackups.OPERATIONS) {
            val subDir = "pre_$op"
            val group = all
                .filter { it.subDir == subDir }
                .sortedByDescending { it.lastModified }
            if (group.size > keep) toDelete += group.drop(keep)
        }

        if (toDelete.isEmpty()) return 0

        var deleted = 0
        for (b in toDelete) {
            if (deleteByUri(context, b.uri)) deleted++
        }
        return deleted
    }

    private fun queryAll(
        context: Context,
        acceptSubDir: (String) -> Boolean
    ): List<PublicBackup> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%${PublicBackupsMigrator.ROOT_DIR}%")

        val result = mutableListOf<PublicBackup>()

        try {
            resolver.query(collection, projection, selection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                val pathCol = c.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.RELATIVE_PATH
                )

                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    if (!name.endsWith(EXT)) continue

                    val path = c.getString(pathCol) ?: continue
                    val subDir = extractSubDir(path) ?: continue
                    if (!acceptSubDir(subDir)) continue

                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val size = c.getLong(sizeCol)
                    val dateSec = c.getLong(dateCol)
                    val dateMs = dateSec * 1000L

                    val manifest = try {
                        GsmBackupReader.readManifest(context, uri)
                    } catch (_: Exception) {
                        null
                    }

                    val operation = manifest?.operation
                        ?: RollbackBackups.parseFileName(name)?.operation
                        ?: GsmBackupWriter.OP_UNKNOWN

                    result += PublicBackup(
                        uri = uri.toString(),
                        displayName = name,
                        subDir = subDir,
                        operation = operation,
                        sizeBytes = size,
                        lastModified = dateMs,
                        manifest = manifest
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return result.sortedByDescending { it.lastModified }
    }

    fun extractSubDir(relativePath: String): String? {
        if (relativePath.isBlank()) return null
        val segments = relativePath
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
        val idx = segments.indexOf(PublicBackupsMigrator.ROOT_DIR)
        if (idx < 0) return null
        return if (idx == segments.size - 1) "" else segments[idx + 1]
    }
}