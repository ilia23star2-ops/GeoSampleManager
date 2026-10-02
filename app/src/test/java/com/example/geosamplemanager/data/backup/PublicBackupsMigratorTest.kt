package com.example.geosamplemanager.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.9-db-backups-ops/2:
 * Юнит-тесты чистой логики PublicBackupsMigrator.subdirFor.
 * Сама миграция — device-check.
 */
class PublicBackupsMigratorTest {

    @Test
    fun subdirFor_restore_returnsPreRestore() {
        assertEquals(
            "pre_restore",
            PublicBackupsMigrator.subdirFor("pre_restore_20261001_1200.gsmbackup")
        )
    }

    @Test
    fun subdirFor_rollback_returnsPreRollback() {
        assertEquals(
            "pre_rollback",
            PublicBackupsMigrator.subdirFor("pre_rollback_20261001_1200.gsmbackup")
        )
    }

    @Test
    fun subdirFor_clean_returnsPreClean() {
        assertEquals(
            "pre_clean",
            PublicBackupsMigrator.subdirFor("pre_clean_20261001_1200.gsmbackup")
        )
    }

    @Test
    fun subdirFor_export_returnsExports() {
        assertEquals(
            "exports",
            PublicBackupsMigrator.subdirFor("geosamples_20261001_1200.gsmbackup")
        )
    }

    @Test
    fun subdirFor_anyGsmbackupWithoutPre_returnsExports() {
        assertEquals(
            "exports",
            PublicBackupsMigrator.subdirFor("backup.gsmbackup")
        )
    }

    @Test
    fun subdirFor_nonGsmbackup_returnsNull() {
        assertNull(PublicBackupsMigrator.subdirFor("photo.jpg"))
    }

    @Test
    fun subdirFor_txtFile_returnsNull() {
        assertNull(PublicBackupsMigrator.subdirFor("readme.txt"))
    }

    @Test
    fun subdirFor_emptyString_returnsNull() {
        assertNull(PublicBackupsMigrator.subdirFor(""))
    }

    @Test
    fun subdirFor_unknownPreOp_returnsExports() {
        // "pre_unknown_" не входит в OPERATIONS → считаем как
        // пользовательский экспорт.
        assertEquals(
            "exports",
            PublicBackupsMigrator.subdirFor("pre_unknown_20261001_1200.gsmbackup")
        )
    }

    @Test
    fun subdirFor_partialPrefix_returnsExports() {
        // Имя начинается на "pre_" но не на конкретный префикс —
        // значит экспорт.
        assertEquals(
            "exports",
            PublicBackupsMigrator.subdirFor("pre_20261001_1200.gsmbackup")
        )
    }
}