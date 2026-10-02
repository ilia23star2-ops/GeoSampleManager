package com.example.geosamplemanager.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * FIX 5.9-db-rollback / 5.9-db-backups-ops / 5.9-db-rollback-public:
 * Юнит-тесты чистой логики RollbackBackups.
 */
class RollbackBackupsTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun touch(name: String): File {
        val f = File(tempFolder.root, name)
        f.writeText("")
        return f
    }

    private fun stubPrivate(
        fileName: String,
        operation: String,
        createdAt: Long
    ): RollbackBackup = RollbackBackup(
        fileName = fileName,
        source = BackupSource.PRIVATE,
        file = File(tempFolder.root, fileName),
        publicUri = null,
        operation = operation,
        createdAt = createdAt,
        sizeBytes = 0L,
        manifest = null
    )

    private fun stubPublic(
        fileName: String,
        operation: String,
        createdAt: Long
    ): RollbackBackup = RollbackBackup(
        fileName = fileName,
        source = BackupSource.PUBLIC,
        file = null,
        publicUri = "content://stub/$fileName",
        operation = operation,
        createdAt = createdAt,
        sizeBytes = 0L,
        manifest = null
    )

    // ============================================================
    // parseFileName
    // ============================================================

    @Test
    fun parseFileName_restore_returnsRestoreOp() {
        val p = RollbackBackups.parseFileName("pre_restore_20261001_2130.gsmbackup")
        assertNotNull(p)
        assertEquals("restore", p!!.operation)
    }

    @Test
    fun parseFileName_rollback_returnsRollbackOp() {
        val p = RollbackBackups.parseFileName("pre_rollback_20261001_2130.gsmbackup")
        assertNotNull(p)
        assertEquals("rollback", p!!.operation)
    }

    @Test
    fun parseFileName_clean_returnsCleanOp() {
        val p = RollbackBackups.parseFileName("pre_clean_20261001_2130.gsmbackup")
        assertNotNull(p)
        assertEquals("clean", p!!.operation)
    }

    @Test
    fun parseFileName_unknownPrefix_returnsNull() {
        assertNull(RollbackBackups.parseFileName("pre_unknown_20261001_2130.gsmbackup"))
    }

    @Test
    fun parseFileName_wrongSuffix_returnsNull() {
        assertNull(RollbackBackups.parseFileName("pre_restore_20261001_2130.zip"))
    }

    @Test
    fun parseFileName_garbageDate_returnsNull() {
        assertNull(RollbackBackups.parseFileName("pre_restore_garbage.gsmbackup"))
    }

    @Test
    fun parseFileName_invalidMonth_returnsNull() {
        assertNull(RollbackBackups.parseFileName("pre_restore_20261301_1200.gsmbackup"))
    }

    // ============================================================
    // prefixFor
    // ============================================================

    @Test
    fun prefixFor_restore_returnsPreRestoreUnderscore() {
        assertEquals("pre_restore_", RollbackBackups.prefixFor("restore"))
    }

    @Test
    fun prefixFor_clean_returnsPreCleanUnderscore() {
        assertEquals("pre_clean_", RollbackBackups.prefixFor("clean"))
    }

    // ============================================================
    // list — фильтр, сортировка, source
    // ============================================================

    @Test
    fun list_emptyDir_returnsEmpty() {
        assertEquals(0, RollbackBackups.list(tempFolder.root).size)
    }

    @Test
    fun list_missingDir_returnsEmpty() {
        assertEquals(
            0,
            RollbackBackups.list(File(tempFolder.root, "no_such")).size
        )
    }

    @Test
    fun list_foreignFiles_returnsEmpty() {
        touch("random.txt")
        touch("pre_unknown_20261001_1200.gsmbackup")
        assertEquals(0, RollbackBackups.list(tempFolder.root).size)
    }

    @Test
    fun list_allThreeOps_returnsAll() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_rollback_20261002_1200.gsmbackup")
        touch("pre_clean_20261003_1200.gsmbackup")
        assertEquals(3, RollbackBackups.list(tempFolder.root).size)
    }

    @Test
    fun list_mixed_sortedDescending() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_rollback_20261002_1200.gsmbackup")
        touch("pre_clean_20261003_1200.gsmbackup")
        val r = RollbackBackups.list(tempFolder.root)
        assertEquals("clean", r[0].operation)
        assertEquals("rollback", r[1].operation)
        assertEquals("restore", r[2].operation)
    }

    @Test
    fun list_setsSourcePrivate() {
        touch("pre_restore_20261001_1200.gsmbackup")
        val r = RollbackBackups.list(tempFolder.root)
        assertEquals(1, r.size)
        assertEquals(BackupSource.PRIVATE, r[0].source)
        assertNotNull(r[0].file)
        assertNull(r[0].publicUri)
    }

    // ============================================================
    // rotateByPrefix
    // ============================================================

    @Test
    fun rotateByPrefix_lessThanKeep_noDeletions() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_restore_20261002_1200.gsmbackup")
        assertEquals(0, RollbackBackups.rotateByPrefix(tempFolder.root, 5).size)
    }

    @Test
    fun rotateByPrefix_overKeep_deletesOldestPerOp() {
        for (i in 1..7) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
        }
        val deleted = RollbackBackups.rotateByPrefix(tempFolder.root, 5)
        assertEquals(2, deleted.size)
        assertEquals(5, RollbackBackups.list(tempFolder.root).size)
    }

    @Test
    fun rotateByPrefix_doesNotTouchOtherOps() {
        for (i in 1..7) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
        }
        touch("pre_clean_20261001_1200.gsmbackup")
        touch("pre_clean_20261002_1200.gsmbackup")
        RollbackBackups.rotateByPrefix(tempFolder.root, 5)
        val all = RollbackBackups.list(tempFolder.root)
        assertEquals(5, all.count { it.operation == "restore" })
        assertEquals(2, all.count { it.operation == "clean" })
    }

    // ============================================================
    // merge
    // ============================================================

    @Test
    fun merge_emptyBoth_returnsEmpty() {
        assertEquals(0, RollbackBackups.merge(emptyList(), emptyList()).size)
    }

    @Test
    fun merge_onlyPrivate_returnsPrivate() {
        val p = listOf(
            stubPrivate("pre_restore_20261001_1200.gsmbackup", "restore", 1000L)
        )
        val r = RollbackBackups.merge(p, emptyList())
        assertEquals(1, r.size)
        assertEquals(BackupSource.PRIVATE, r[0].source)
    }

    @Test
    fun merge_onlyPublic_returnsPublic() {
        val pub = listOf(
            stubPublic("pre_restore_20261001_1200.gsmbackup", "restore", 1000L)
        )
        val r = RollbackBackups.merge(emptyList(), pub)
        assertEquals(1, r.size)
        assertEquals(BackupSource.PUBLIC, r[0].source)
    }

    @Test
    fun merge_sameName_privateWins() {
        val name = "pre_restore_20261001_1200.gsmbackup"
        val p = listOf(stubPrivate(name, "restore", 1000L))
        val pub = listOf(stubPublic(name, "restore", 1000L))
        val r = RollbackBackups.merge(p, pub)
        assertEquals(1, r.size)
        assertEquals(BackupSource.PRIVATE, r[0].source)
    }

    @Test
    fun merge_differentNames_keepsBoth() {
        val p = listOf(
            stubPrivate("pre_restore_20261001_1200.gsmbackup", "restore", 1000L)
        )
        val pub = listOf(
            stubPublic("pre_restore_20261002_1200.gsmbackup", "restore", 2000L)
        )
        val r = RollbackBackups.merge(p, pub)
        assertEquals(2, r.size)
    }

    @Test
    fun merge_differentOps_keepsAll() {
        val p = listOf(
            stubPrivate("pre_restore_20261001_1200.gsmbackup", "restore", 1000L)
        )
        val pub = listOf(
            stubPublic("pre_clean_20261002_1200.gsmbackup", "clean", 2000L)
        )
        val r = RollbackBackups.merge(p, pub)
        assertEquals(2, r.size)
    }

    @Test
    fun merge_sortedDescending() {
        val p = listOf(
            stubPrivate("pre_restore_20261001_1200.gsmbackup", "restore", 1000L)
        )
        val pub = listOf(
            stubPublic("pre_clean_20261003_1200.gsmbackup", "clean", 3000L),
            stubPublic("pre_rollback_20261002_1200.gsmbackup", "rollback", 2000L)
        )
        val r = RollbackBackups.merge(p, pub)
        assertEquals(3, r.size)
        assertTrue(r[0].createdAt > r[1].createdAt)
        assertTrue(r[1].createdAt > r[2].createdAt)
    }

    @Test
    fun merge_duplicateNamesInPrivate_onlyFirstKept() {
        val name = "pre_restore_20261001_1200.gsmbackup"
        val p = listOf(
            stubPrivate(name, "restore", 1000L),
            stubPrivate(name, "restore", 1000L)
        )
        val r = RollbackBackups.merge(p, emptyList())
        assertEquals(1, r.size)
    }

    // ============================================================
    // fromPublic
    // ============================================================

    @Test
    fun fromPublic_setsSourcePublic() {
        // FIX 5.9-db-rollback-public: uri — строка, без Uri.parse.
        val pub = PublicBackup(
            uri = "content://stub/1",
            displayName = "pre_restore_20261001_1200.gsmbackup",
            subDir = "pre_restore",
            operation = "restore",
            sizeBytes = 100L,
            lastModified = 9999L,
            manifest = null
        )
        val r = RollbackBackups.fromPublic(pub)
        assertEquals(BackupSource.PUBLIC, r.source)
        assertEquals("pre_restore_20261001_1200.gsmbackup", r.fileName)
        assertNull(r.file)
        assertEquals("content://stub/1", r.publicUri)
        // createdAt — из имени файла, не lastModified.
        assertTrue(r.createdAt != 9999L)
    }
}