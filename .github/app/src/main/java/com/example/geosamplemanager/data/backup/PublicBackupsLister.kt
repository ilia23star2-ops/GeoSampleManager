package com.example.geosamplemanager.data.backup

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * FIX 5.9-db-import-picker:
 * Поиск .gsmbackup в публичных Загрузках:
 *
 *   Downloads/GeoSampleManager/*.gsmbackup
 *   Downloads/GeoSampleManager/pre_restore/*.gsmbackup
 *   Downloads/GeoSampleManager/pre_rollback/*.gsmbackup
 *   Downloads/GeoSampleManager/pre_clean/*.gsmbackup
 *   Downloads/GeoSampleManager/exports/*.gsmbackup
 *
 * Работает только на Android 10+ (MediaStore.Downloads).
 * На более старых возвращает пустой список — используется SAF.
*/
object PublicBackupsLister {

private const val EXT = ".${GsmBackupWriter.EXTENSION}"

/**
 * Список публичных бэкапов, отсортированный по убыванию даты.
*/
fun list(context: Context): List<PublicBackup> {
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

val id = c.getLong(idCol)
val uri = ContentUris.withAppendedId(collection, id)
val size = c.getLong(sizeCol)
// DATE_MODIFIED — секунды, приводим к мс.
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
uri = uri,
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

/**
 * FIX 5.9-db-import-picker:
 * Из RELATIVE_PATH MediaStore вытащить подпапку внутри
 * GeoSampleManager. Чистая функция — покрывается юнит-тестами.
 *
 *  "Download/GeoSampleManager/"                 → ""  (корень)
 *  "Download/GeoSampleManager/pre_restore/"     → "pre_restore"
 *  "Download/GeoSampleManager/exports/"         → "exports"
 *  "Download/Other/pre_restore/"                → null
 *  ""                                           → null
*/
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