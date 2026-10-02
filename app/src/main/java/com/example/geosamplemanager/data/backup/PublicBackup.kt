package com.example.geosamplemanager.data.backup

/**
 * FIX 5.9-db-import-picker:
 * Один .gsmbackup, найденный в публичных Загрузках
 * (Downloads/GeoSampleManager/...).
 *
 * FIX 5.9-db-rollback-public:
 *  - uri — строка, не android.net.Uri. Модель данных не должна
 *    зависеть от Android: тесты в JVM (Uri.parse возвращает null).
 *    В местах, где нужен Uri, делаем Uri.parse(uri).
 */
data class PublicBackup(
    /** content:// строка файла в MediaStore. */
    val uri: String,
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
     */
    val operation: String,
    /** Размер файла в байтах. */
    val sizeBytes: Long,
    /** Дата изменения (мс). */
    val lastModified: Long,
    /** Manifest. null — если не удалось прочитать. */
    val manifest: BackupManifest?
)