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
 * Юнит-тесты чистой логики RollbackBackups:
 *  - парсер имени pre_restore_YYYYMMDD_HHmm.gsmbackup;
 *  - фильтр по имени и типу;
 *  - сортировка по убыванию даты;
 *  - ротация (оставляем N последних).
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
    // parseTimestamp
    // ============================================================

    @Test
    fun parseTimestamp_validName_returnsNotNull() {
        val ts = RollbackBackups.parseTimestamp("pre_restore_20261001_2130.gsmbackup")
        assertNotNull(ts)
    }

    @Test
    fun parseTimestamp_wrongPrefix_returnsNull() {
        val ts = RollbackBackups.parseTimestamp("pre_rollback_20261001_2130.gsmbackup")
        assertNull(ts)
    }

    @Test
    fun parseTimestamp_wrongSuffix_returnsNull() {
        val ts = RollbackBackups.parseTimestamp("pre_restore_20261001_2130.zip")
        assertNull(ts)
    }

    @Test
    fun parseTimestamp_garbageDatePart_returnsNull() {
        val ts = RollbackBackups.parseTimestamp("pre_restore_garbage.gsmbackup")
        assertNull(ts)
    }

    @Test
    fun parseTimestamp_shortDatePart_returnsNull() {
        val ts = RollbackBackups.parseTimestamp("pre_restore_20261001_21.gsmbackup")
        assertNull(ts)
    }

    @Test
    fun parseTimestamp_invalidMonth_returnsNull() {
        // isLenient=false → месяц 13 не пройдёт
        val ts = RollbackBackups.parseTimestamp("pre_restore_20261301_1200.gsmbackup")
        assertNull(ts)
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
    fun list_noPreRestoreFiles_returnsEmpty() {
        touch("pre_rollback_20261001_2130.gsmbackup")
        touch("random_file.txt")
        touch("pre_restore_garbage.gsmbackup")
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(0, result.size)
    }

    @Test
    fun list_multiplePreRestore_sortedDescending() {
        touch("pre_restore_20261001_2130.gsmbackup")
        touch("pre_restore_20261002_1200.gsmbackup")
        touch("pre_restore_20260930_0900.gsmbackup")
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(3, result.size)
        assertTrue(result[0].createdAt > result[1].createdAt)
        assertTrue(result[1].createdAt > result[2].createdAt)
    }

    @Test
    fun list_manifestReaderCalledForEachFile() {
        touch("pre_restore_20261001_2130.gsmbackup")
        touch("pre_restore_20261002_1200.gsmbackup")

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
        val f = touch("pre_restore_20261001_2130.gsmbackup")
        f.writeText("hello") // 5 байт
        val result = RollbackBackups.list(tempFolder.root)
        assertEquals(1, result.size)
        assertEquals(5L, result[0].sizeBytes)
    }

    // ============================================================
    // rotate
    // ============================================================

    @Test
    fun rotate_lessThanKeep_noDeletions() {
        touch("pre_restore_20261001_1200.gsmbackup")
        touch("pre_restore_20261002_1200.gsmbackup")
        val deleted = RollbackBackups.rotate(tempFolder.root, keep = 5)
        assertEquals(0, deleted.size)
        assertEquals(2, RollbackBackups.list(tempFolder.root).size)
    }

    @Test
    fun rotate_exactKeep_noDeletions() {
        for (i in 1..5) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
        }
        val deleted = RollbackBackups.rotate(tempFolder.root, keep = 5)
        assertEquals(0, deleted.size)
        assertEquals(5, RollbackBackups.list(tempFolder.root).size)
    }

    @Test
    fun rotate_overKeep_deletesOldest() {
        for (i in 1..7) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
        }
        val deleted = RollbackBackups.rotate(tempFolder.root, keep = 5)
        assertEquals(2, deleted.size)

        val remaining = RollbackBackups.list(tempFolder.root)
        assertEquals(5, remaining.size)
        // Самые старые (01 и 02) должны быть удалены.
        val deletedNames = deleted.map { it.name }.toSet()
        assertTrue(deletedNames.contains("pre_restore_20261001_1200.gsmbackup"))
        assertTrue(deletedNames.contains("pre_restore_20261002_1200.gsmbackup"))
    }

    @Test
    fun rotate_keepsPreRollbackUntouched() {
        touch("pre_rollback_20261001_1200.gsmbackup")
        for (i in 1..7) {
            touch("pre_restore_2026100${i}_1200.gsmbackup")
        }
        RollbackBackups.rotate(tempFolder.root, keep = 5)
        // pre_rollback_* не считаем и не трогаем.
        assertTrue(File(tempFolder.root, "pre_rollback_20261001_1200.gsmbackup").exists())
    }

    @Test
    fun rotate_emptyDir_noCrash() {
        val deleted = RollbackBackups.rotate(tempFolder.root, keep = 5)
        assertEquals(0, deleted.size)
    }
}