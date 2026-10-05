package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.9-logs-1:
 * Проверяем маппинг кодов и русские метки категорий.
 */
class LogCategoryTest {

    @Test
    fun allCategoriesHaveRussianLabels() {
        for (c in LogCategory.values()) {
            assertEquals(true, c.label.isNotEmpty())
            // В метке не должно быть английских букв.
            assertEquals(
                false,
                c.label.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' }
            )
        }
    }

    @Test
    fun fromCodeReturnsCorrectCategory() {
        assertEquals(LogCategory.APP, LogCategory.fromCode("app"))
        assertEquals(LogCategory.NAV, LogCategory.fromCode("nav"))
        assertEquals(LogCategory.SEARCH, LogCategory.fromCode("search"))
        assertEquals(LogCategory.VOICE, LogCategory.fromCode("voice"))
        assertEquals(LogCategory.MARK, LogCategory.fromCode("mark"))
        assertEquals(LogCategory.EDIT, LogCategory.fromCode("edit"))
        assertEquals(LogCategory.DB, LogCategory.fromCode("db"))
        assertEquals(LogCategory.ERROR, LogCategory.fromCode("error"))
    }

    @Test
    fun fromCodeReturnsNullForUnknown() {
        assertNull(LogCategory.fromCode("unknown"))
        assertNull(LogCategory.fromCode(""))
        assertNull(LogCategory.fromCode("APP"))
    }

    @Test
    fun allCodesAreUnique() {
        val codes = LogCategory.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }
}