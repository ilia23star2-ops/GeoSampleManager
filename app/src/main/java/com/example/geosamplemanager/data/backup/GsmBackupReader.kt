package com.example.geosamplemanager.data.backup

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class BackupManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val dbSchemaVersion: Int,
    val areas: Int,
    val orders: Int,
    val samples: Int,
    val photos: Int,
    val notes: Int
)

object GsmBackupReader {

    const val MANIFEST = "manifest.json"
    const val DB_ENTRY = "geosamples.db"
    const val PHOTOS_PREFIX = "sample_photos/"
    const val EXPECTED_FORMAT_VERSION = 1

    fun readManifest(context: Context, uri: Uri): BackupManifest? {
        var result: BackupManifest? = null
        try {
            val input = context.contentResolver.openInputStream(uri)
            if (input != null) {
                input.use { stream ->
                    val zip = ZipInputStream(stream)
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == MANIFEST) {
                            val bytes = zip.readBytes()
                            result = parseManifest(bytes.toString(Charsets.UTF_8))
                            break
                        }
                        entry = zip.nextEntry
                    }
                    try { zip.close() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            return null
        }
        return result
    }

    private fun parseManifest(json: String): BackupManifest? {
        return try {
            val obj = JSONObject(json)
            val counts = obj.optJSONObject("counts") ?: JSONObject()
            BackupManifest(
                formatVersion = obj.optInt("format_version", -1),
                createdAt = obj.optLong("created_at", 0L),
                appVersion = obj.optString("app_version", "?"),
                dbSchemaVersion = obj.optInt("db_schema_version", -1),
                areas = counts.optInt("areas", 0),
                orders = counts.optInt("orders", 0),
                samples = counts.optInt("samples", 0),
                photos = counts.optInt("photos", 0),
                notes = counts.optInt("notes", 0)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun extract(
        context: Context,
        uri: Uri,
        targetDb: File,
        targetPhotosDir: File
    ): Boolean {
        var dbWritten = false
        try {
            val input = context.contentResolver.openInputStream(uri)
            if (input != null) {
                input.use { stream ->
                    val zip = ZipInputStream(stream)
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name ?: ""
                        if (name == DB_ENTRY) {
                            targetDb.outputStream().use { out -> zip.copyTo(out) }
                            dbWritten = true
                        } else if (name.startsWith(PHOTOS_PREFIX) && !entry.isDirectory) {
                            val relative = name.removePrefix(PHOTOS_PREFIX)
                            if (relative.isNotBlank()) {
                                val f = File(targetPhotosDir, relative)
                                f.parentFile?.mkdirs()
                                f.outputStream().use { out -> zip.copyTo(out) }
                            }
                        }
                        entry = zip.nextEntry
                    }
                    try { zip.close() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            return false
        }
        return dbWritten
    }
}