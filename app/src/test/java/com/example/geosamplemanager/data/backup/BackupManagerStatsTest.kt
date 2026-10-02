package com.example.geosamplemanager.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * FIX 5.9-db-backup-manager:
 * Юнит-тесты чистой логики BackupManagerStats.
 * Работа с MediaStore и File — device-check.
 */
class BackupManagerStatsTest {

    private fun stubPrivate(
        fileName: String,
        operation: String,
        sizeBytes: Long
    ): RollbackBackup = RollbackBackup(
        fileName = fileName,
        source = BackupSource.PRIVATE,
        file = File("/tmp", fileName),
        publicUri = null,
        operation = operation,
        createdAt = 0L,
        sizeBytes = sizeBytes,
        manifest = null
    )

    private fun stubPublic(
        fileName: String,
        operation: String,
        sizeBytes: Long
    ): RollbackBackup = RollbackBackup(
        fileName = fileName,
        source = BackupSource.PUBLIC,
        file = null,
        publicUri = "content://stub/$fileName",
        operation = operation,
        createdAt = 0L,
        sizeBytes = sizeBytes,
        manifest = null
    )

    // ============================================================
    // summarize
    // ============================================================

    @Test
    fun summarize_empty_returnsZeros() {
        val s = BackupManagerStats.summarize(emptyList())
        assertEquals(0, s.totalCount)
        assertEquals(0L, s.totalSizeBytes)
        assertEquals(0, s.privateCount)
        assertEquals(0, s.publicCount)
        assertEquals(0, s.autoCount)
        assertEquals(0, s.exportCount)
    }

    @Test
    fun summarize_onlyPrivate_countsPrivate() {
        val list = listOf(
            stubPrivate("a.gsmbackup", "restore", 100L),
            stubPrivate("b.gsmbackup", "clean", 200L)
        )
        val s = BackupManagerStats.summarize(list)
        assertEquals(2, s.totalCount)
        assertEquals(300L, s.totalSizeBytes)
        assertEquals(2, s.privateCount)
        assertEquals(0, s.publicCount)
        assertEquals(2, s.autoCount)
        assertEquals(0, s.exportCount)
    }

    @Test
    fun summarize_onlyPublic_countsPublic() {
        val list = listOf(
            stubPublic("a.gsmbackup", "rollback", 50L)
        )
        val s = BackupManagerStats.summarize(list)
        assertEquals(1, s.totalCount)
        assertEquals(1, s.publicCount)
        assertEquals(0, s.privateCount)
        assertEquals(1, s.autoCount)
    }

    @Test
    fun summarize_mixed_countsBoth() {
        val list = listOf(
            stubPrivate("a.gsmbackup", "restore", 100L),
            stubPublic("b.gsmbackup", "rollback", 200L),
            stubPublic("c.gsmbackup", "clean", 300L)
        )
        val s = BackupManagerStats.summarize(list)
        assertEquals(3, s.totalCount)
        assertEquals(600L, s.totalSizeBytes)
        assertEquals(1, s.privateCount)
        assertEquals(2, s.publicCount)
        assertEquals(3, s.autoCount)
    }

    @Test
    fun summarize_exportOperation_countedAsExport() {
        val list = listOf(
            stubPublic("exp.gsmbackup", "export", 100L)
        )
        val s = BackupManagerStats.summarize(list)
        assertEquals(1, s.exportCount)
        assertEquals(0, s.autoCount)
    }

    @Test
    fun summarize_unknownOperation_countedAsExport() {
        val list = listOf(
            stubPrivate("unk.gsmbackup", "unknown", 100L)
        )
        val s = BackupManagerStats.summarize(list)
        assertEquals(1, s.exportCount)
        assertEquals(0, s.autoCount)
    }

    @Test
    fun summarize_mixedAutoAndExport_splitsCorrectly() {
        val list = listOf(
            stubPrivate("a.gsmbackup", "restore", 100L),
            stubPublic("b.gsmbackup", "export", 200L),
            stubPublic("c.gsmbackup", "rollback", 300L),
            stubPublic("d.gsmbackup", "unknown", 400L)
        )
        val s = BackupManagerStats.summarize(list)
        assertEquals(4, s.totalCount)
        assertEquals(1000L, s.totalSizeBytes)
        assertEquals(2, s.autoCount)
        assertEquals(2, s.exportCount)
    }

    // ============================================================
    // formatSize
    // ============================================================

    @Test
    fun formatSize_bytes_returnsBytes() {
        assertEquals("500 Б", BackupManagerStats.formatSize(500L))
    }

    @Test
    fun formatSize_kilobytes_returnsKb() {
        // 5 КБ
        assertEquals("5 КБ", BackupManagerStats.formatSize(5 * 1024L))
    }

    @Test
    fun formatSize_megabytes_returnsMb() {
        // 2.5 МБ
        val bytes = (2.5 * 1024 * 1024).toLong()
        assertEquals("2.5 МБ", BackupManagerStats.formatSize(bytes))
    }

    @Test
    fun formatSize_zero_returnsBytes() {
        assertEquals("0 Б", BackupManagerStats.formatSize(0L))
    }
}