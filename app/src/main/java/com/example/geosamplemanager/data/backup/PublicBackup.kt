package com.example.geosamplemanager.data.backup

import android.net.Uri

/**
 * FIX 5.9-db-import-picker:
 * Один .gsmbackup, найденный в публичных Загрузках
 * (Downloads/GeoSampleManager/...).
 *
 * Используется в диалоге «Импорт» — показываем список, пользователь
 * выбирает файл сам, вместо немедленного открытия SAF.
 */
data class PublicBackup(
    /** content:// Uri файла в MediaStore. */
    val uri: Uri,
    /** Имя файла с расширением. */
    val displayName: String,
    /**
     * Подпапка внутри GeoSampleManager:
     *  - "pre_restore" / "pre_rollback" / "pre_clean" — авто-бэкапы;
     *  - "exports" — пользовательские;
     *  - "" — лежит в корне GeoSampleManager (старый файл).
     */
    val subDir: String,
    /**
     * Операция бэкапа: "restore"/"rollback"/"clean"/"export"/"unknown".
     * Из manifest, или (если не прочитался) — из имени файла.
     */
    val operation: String,
    /** Размер файла в байтах. */
    val sizeBytes: Long,
    /** Дата изменения (мс). */
    val lastModified: Long,
    /** Manifest. null — если не удалось прочитать. */
    val manifest: BackupManifest?
)