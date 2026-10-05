package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.9-logs-1:
 * Маппинг уровней и их метки.
 */
class LogLevelTest {

    @Test
    fun allLevelsHaveRussianLabels() {
        for (l in LogLevel.values()) {
            assertEquals(true, l.label.isNotEmpty())
            assertEquals(
                false,
                l.label.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' }
            )
        }
    }

    @Test
    fun fromCodeReturnsCorrectLevel() {
        assertEquals(LogLevel.INFO, LogLevel.fromCode("info"))
        assertEquals(LogLevel.WARN, LogLevel.fromCode("warn"))
        assertEquals(LogLevel.ERROR, LogLevel.fromCode("error"))
    }

    @Test
    fun fromCodeReturnsNullForUnknown() {
        assertNull(LogLevel.fromCode("debug"))
        assertNull(LogLevel.fromCode(""))
    }
}