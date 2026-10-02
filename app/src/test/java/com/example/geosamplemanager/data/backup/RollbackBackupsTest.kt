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
 * FIX 5.9-db-rollback:
 * Юнит-тесты чистой логики RollbackBackups.
 *
 * FIX 5.9-db-backups-ops:
 *  - три префикса (restore/rollback/clean);
 *  - ротация по каждому префиксу отдельно.
 */
class RollbackBackupsTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun touch(name: String): File {
        val f = File(tempFolder.root, name)
        f.writeText("")
        return f
    }

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
        val p = RollbackBackups.parseFileName("pre_unknown_20261001_2130.gsmbackup")
        assertNull(p)
    }

    @Test
    fun parseFileName_wrongSuffix_returnsNull() {
        val p = RollbackBackups.parseFileName("pre_restore_20261001_2130.zip")
        assertNull(p)
    }

    @Test
    fun parseFileName_garbageDate_returnsNull() {
        val p = RollbackBackups.parseFileName("pre_restore_garbage.gsmbackup")
        assertNull(p)
    }

    @Test
    fun parseFileName_shortDate_returnsNull() {
        val p = RollbackBackups.parseFileName("pre_restore_20261001_21.gsmbackup")
        assertNull(p)
    }

    @Test
    fun parseFileName_invalidMonth_returnsNull() {
        val p = RollbackBackups.parseFileName("pre_restore_20261301_1200.gsmbackup")
        assertNull(p)
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
    // list — фильтр и сортировка
    // ============================================================

    @Test
    fun list_emptyDir_returnsEmpty() {
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(0, result.size)
    }

    @Test
    fun list_missingDir_returnsEmpty() {
        val result = RollbackBackups.list(File(tempFolder.root, "no_such_dir"))
        assertEquals(0, result.size)
    }

    @Test
    fun list_foreignFiles_returnsEmpty() {
        touch("random_file.txt")
        touch("pre_unknown_20261001_1200.gsmbackup")
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(0, result.size)
    }

    @Test
    fun list_allThreeOps_returnsAll() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_rollback_20261002_1200.gsmbackup")
        touch("pre_clean_20261003_1200.gsmbackup")
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(3, result.size)
    }

    @Test
    fun list_mixed_sortedDescending() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_rollback_20261002_1200.gsmbackup")
        touch("pre_clean_20261003_1200.gsmbackup")
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals("clean", result[0].operation)
        assertEquals("rollback", result[1].operation)
        assertEquals("restore", result[2].operation)
    }

    @Test
    fun list_operationExtractedFromName() {
        touch("pre_clean_20261001_1200.gsmbackup")
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(1, result.size)
        assertEquals("clean", result[0].operation)
    }

    @Test
    fun list_manifestReaderCalledForEachFile() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_rollback_20261002_1200.gsmbackup")

        var calls = 0
        val result = RollbackBackups.list(tempFolder.root) { _ ->
            calls++
            null
        }
        assertEquals(2, result.size)
        assertEquals(2, calls)
    }

    @Test
    fun list_fillsSizeFromFile() {
        val f = touch("pre_restore_20261001_1200.gsmbackup")
        f.writeText("hello")
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(1, result.size)
        assertEquals(5L, result[0].sizeBytes)
    }

    // ============================================================
    // rotateByPrefix
    // ============================================================

    @Test
    fun rotateByPrefix_lessThanKeep_noDeletions() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_restore_20261002_1200.gsmbackup")
        val deleted = RollbackBackups.rotateByPrefix(tempFolder.root, keep = 5)
        assertEquals(0, deleted.size)
    }

    @Test
    fun rotateByPrefix_overKeep_deletesOldestPerOp() {
        for (i in 1..7) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
        }
        val deleted = RollbackBackups.rotateByPrefix(tempFolder.root, keep = 5)
        assertEquals(2, deleted.size)

        val remaining = RollbackBackups.list(tempFolder.root)
        assertEquals(5, remaining.size)

        val deletedNames = deleted.map { it.name }.toSet()
        assertTrue(deletedNames.contains("pre_restore_20261001_1200.gsmbackup"))
        assertTrue(deletedNames.contains("pre_restore_20261002_1200.gsmbackup"))
    }

    @Test
    fun rotateByPrefix_doesNotTouchOtherOps() {
        // restore — 7 файлов (ротируется), clean — 3 (не трогаем).
        for (i in 1..7) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
        }
        touch("pre_clean_20261001_1200.gsmbackup")
        touch("pre_clean_20261002_1200.gsmbackup")
        touch("pre_clean_20261003_1200.gsmbackup")

        RollbackBackups.rotateByPrefix(tempFolder.root, keep = 5)

        val all = RollbackBackups.list(tempFolder.root)
        assertEquals(5, all.count { it.operation == "restore" })
        assertEquals(3, all.count { it.operation == "clean" })
    }

    @Test
    fun rotateByPrefix_eachOpGetsOwnQuota() {
        // 7 restore + 7 clean. Оба должны оставить по 5.
        for (i in 1..7) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
            touch("pre_clean_2026100${i}_1200.gsmbackup")
        }
        RollbackBackups.rotateByPrefix(tempFolder.root, keep = 5)

        val all = RollbackBackups.list(tempFolder.root)
        assertEquals(10, all.size)
        assertEquals(5, all.count { it.operation == "restore" })
        assertEquals(5, all.count { it.operation == "clean" })
    }

    @Test
    fun rotateByPrefix_emptyDir_noCrash() {
        val deleted = RollbackBackups.rotateByPrefix(tempFolder.root, keep = 5)
        assertEquals(0, deleted.size)
    }

    @Test
    fun rotateByPrefix_keepZero_deletesAll() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_restore_20261002_1200.gsmbackup")
        val deleted = RollbackBackups.rotateByPrefix(tempFolder.root, keep = 0)
        assertEquals(2, deleted.size)
        assertEquals(0, RollbackBackups.list(tempFolder.root).size)
    }
}