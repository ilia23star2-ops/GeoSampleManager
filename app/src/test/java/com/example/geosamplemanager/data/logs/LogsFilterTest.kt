package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-logs-5:
 * Проверяем фильтр журнала: русские метки, маппинг на
 * LogCategory, единственность ALL.
 */
class LogsFilterTest {

    @Test
    fun allLabelsAreRussian() {
        for (f in LogsFilter.values()) {
            assertEquals(
                "Метка должна быть без английских букв: ${f.label}",
                false,
                f.label.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' }
            )
        }
    }

    @Test
    fun allIsTheOnlyNullCode() {
        val nulls = LogsFilter.values().filter { it.code == null }
        assertEquals(1, nulls.size)
        assertEquals(LogsFilter.ALL, nulls.first())
    }

    @Test
    fun everyCodeMatchesExistingCategory() {
        for (f in LogsFilter.values()) {
            // Smart cast невозможен через границу модуля —
            // фиксируем значение в локальной переменной.
            val code = f.code ?: continue
            val cat = LogCategory.fromCode(code)
            assertNotNull("Фильтр ${f.name} → нет категории $code", cat)
        }
    }

    @Test
    fun allCategoriesCoveredExceptNone() {
        // Каждая LogCategory должна быть представлена фильтром.
        for (cat in LogCategory.values()) {
            val present = LogsFilter.values().any { it.code == cat.code }
            assertTrue("Нет фильтра для категории ${cat.code}", present)
        }
    }

    @Test
    fun allFilterCodeIsNull() {
        assertNull(LogsFilter.ALL.code)
    }

    @Test
    fun errorFilterTargetsErrorCategory() {
        assertEquals(LogCategory.ERROR.code, LogsFilter.ERROR.code)
    }
}