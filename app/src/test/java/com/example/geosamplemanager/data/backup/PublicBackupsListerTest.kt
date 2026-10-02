package com.example.geosamplemanager.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-db-import-picker:
 * Юнит-тесты чистой функции PublicBackupsLister.extractSubDir.
 *
 * FIX 5.9-db-backups-fix:
 *  - тесты isAutoBackupSubDir — предикат «это авто-бэкап».
 */
class PublicBackupsListerTest {

    // ============================================================
    // extractSubDir
    // ============================================================

    @Test
    fun extractSubDir_root_returnsEmpty() {
        assertEquals(
            "",
            PublicBackupsLister.extractSubDir("Download/GeoSampleManager/")
        )
    }

    @Test
    fun extractSubDir_rootNoTrailingSlash_returnsEmpty() {
        assertEquals(
            "",
            PublicBackupsLister.extractSubDir("Download/GeoSampleManager")
        )
    }

    @Test
    fun extractSubDir_preRestore_returnsPreRestore() {
        assertEquals(
            "pre_restore",
            PublicBackupsLister.extractSubDir(
                "Download/GeoSampleManager/pre_restore/"
            )
        )
    }

    @Test
    fun extractSubDir_preRollbackNoTrailing_returnsPreRollback() {
        assertEquals(
            "pre_rollback",
            PublicBackupsLister.extractSubDir(
                "Download/GeoSampleManager/pre_rollback"
            )
        )
    }

    @Test
    fun extractSubDir_exports_returnsExports() {
        assertEquals(
            "exports",
            PublicBackupsLister.extractSubDir(
                "Download/GeoSampleManager/exports/"
            )
        )
    }

    @Test
    fun extractSubDir_preClean_returnsPreClean() {
        assertEquals(
            "pre_clean",
            PublicBackupsLister.extractSubDir(
                "Download/GeoSampleManager/pre_clean/"
            )
        )
    }

    @Test
    fun extractSubDir_otherFolder_returnsNull() {
        assertNull(
            PublicBackupsLister.extractSubDir("Download/Other/pre_restore/")
        )
    }

    @Test
    fun extractSubDir_emptyString_returnsNull() {
        assertNull(PublicBackupsLister.extractSubDir(""))
    }

    @Test
    fun extractSubDir_blankString_returnsNull() {
        assertNull(PublicBackupsLister.extractSubDir("   "))
    }

    @Test
    fun extractSubDir_noDownloadPrefix_returnsPreRestore() {
        assertEquals(
            "pre_restore",
            PublicBackupsLister.extractSubDir("GeoSampleManager/pre_restore/")
        )
    }

    @Test
    fun extractSubDir_nestedDeeper_takesFirstLevel() {
        assertEquals(
            "a",
            PublicBackupsLister.extractSubDir(
                "Download/GeoSampleManager/a/b/"
            )
        )
    }

    @Test
    fun extractSubDir_geoSampleManagerAsSuffix_returnsEmpty() {
        assertEquals(
            "",
            PublicBackupsLister.extractSubDir("Download/Foo/GeoSampleManager/")
        )
    }

    // ============================================================
    // isAutoBackupSubDir
    // ============================================================

    @Test
    fun isAutoBackupSubDir_preRestore_true() {
        assertTrue(PublicBackupsLister.isAutoBackupSubDir("pre_restore"))
    }

    @Test
    fun isAutoBackupSubDir_preRollback_true() {
        assertTrue(PublicBackupsLister.isAutoBackupSubDir("pre_rollback"))
    }

    @Test
    fun isAutoBackupSubDir_preClean_true() {
        assertTrue(PublicBackupsLister.isAutoBackupSubDir("pre_clean"))
    }

    @Test
    fun isAutoBackupSubDir_exports_false() {
        assertFalse(PublicBackupsLister.isAutoBackupSubDir("exports"))
    }

    @Test
    fun isAutoBackupSubDir_emptyString_false() {
        assertFalse(PublicBackupsLister.isAutoBackupSubDir(""))
    }

    @Test
    fun isAutoBackupSubDir_unknown_returnsFalse() {
        assertFalse(PublicBackupsLister.isAutoBackupSubDir("pre_unknown"))
    }

    @Test
    fun isAutoBackupSubDir_partialMatch_false() {
        // Не должно сработать по префиксу — только точное совпадение.
        assertFalse(PublicBackupsLister.isAutoBackupSubDir("pre_restore_backup"))
    }
}