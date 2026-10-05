package com.example.geosamplemanager.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.9-db-backups-ops:
 * Юнит-тесты парсера manifest.json (GsmBackupReader.parseManifest).
 * Покрываем новое поле operation и обратную совместимость.
 */
class BackupManifestTest {

    @Test
    fun parse_withOperation_returnsIt() {
        val json = """
            {
              "format_version": 1,
              "created_at": 1234567890,
              "app_version": "1.0",
              "db_schema_version": 2,
              "operation": "restore",
              "counts": {
                "areas": 1, "orders": 2, "samples": 3,
                "photos": 4, "notes": 5
              }
            }
        """.trimIndent()

        val m = GsmBackupReader.parseManifest(json)
        assertNotNull(m)
        assertEquals("restore", m!!.operation)
        assertEquals(1, m.formatVersion)
        assertEquals(2, m.dbSchemaVersion)
        assertEquals(3, m.samples)
    }

    @Test
    fun parse_withoutOperation_returnsUnknown() {
        val json = """
            {
              "format_version": 1,
              "created_at": 1234567890,
              "app_version": "1.0",
              "db_schema_version": 2,
              "counts": {
                "areas": 0, "orders": 0, "samples": 0,
                "photos": 0, "notes": 0
              }
            }
        """.trimIndent()

        val m = GsmBackupReader.parseManifest(json)
        assertNotNull(m)
        assertEquals(GsmBackupWriter.OP_UNKNOWN, m!!.operation)
    }

    @Test
    fun parse_cleanOperation_returnsClean() {
        val json = """
            {"operation": "clean", "counts": {}}
        """.trimIndent()
        val m = GsmBackupReader.parseManifest(json)
        assertEquals("clean", m!!.operation)
    }

    @Test
    fun parse_rollbackOperation_returnsRollback() {
        val json = """
            {"operation": "rollback", "counts": {}}
        """.trimIndent()
        val m = GsmBackupReader.parseManifest(json)
        assertEquals("rollback", m!!.operation)
    }

    @Test
    fun parse_brokenJson_returnsNull() {
        val m = GsmBackupReader.parseManifest("not json at all")
        assertNull(m)
    }

    @Test
    fun parse_emptyJson_returnsDefaults() {
        val m = GsmBackupReader.parseManifest("{}")
        assertNotNull(m)
        assertEquals(-1, m!!.formatVersion)
        assertEquals(GsmBackupWriter.OP_UNKNOWN, m.operation)
        assertEquals(0, m.samples)
    }
}