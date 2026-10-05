package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.9-edit-multiselect:
 * Тесты чистых функций мультивыбора.
 *
 * Покрываем:
 *  - applyMultiselectToggle: добавить / убрать / стабильность;
 *  - computeSelectionLabel: 0/1/2-4/5+ / 11 / 21.
 */
class EditMultiselectTest {

    // ================================================================
    // applyMultiselectToggle
    // ================================================================

    @Test
    fun toggle_addsToEmpty() {
        val result = applyMultiselectToggle(emptySet(), "1")
        assertEquals(setOf("1"), result)
    }

    @Test
    fun toggle_addsToExisting() {
        val result = applyMultiselectToggle(setOf("1"), "2")
        assertEquals(setOf("1", "2"), result)
    }

    @Test
    fun toggle_removesExisting() {
        val result = applyMultiselectToggle(setOf("1", "2"), "1")
        assertEquals(setOf("2"), result)
    }

    @Test
    fun toggle_toLastRemovesToEmpty() {
        val result = applyMultiselectToggle(setOf("1"), "1")
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun toggle_unknownIdAdds() {
        val result = applyMultiselectToggle(setOf("1", "2"), "999")
        assertEquals(setOf("1", "2", "999"), result)
    }

    // ================================================================
    // computeSelectionLabel
    // ================================================================

    @Test
    fun label_empty() {
        assertEquals("", computeSelectionLabel(0))
    }

    @Test
    fun label_one() {
        assertEquals("1 проба", computeSelectionLabel(1))
    }

    @Test
    fun label_two() {
        assertEquals("2 пробы", computeSelectionLabel(2))
    }

    @Test
    fun label_four() {
        assertEquals("4 пробы", computeSelectionLabel(4))
    }

    @Test
    fun label_five() {
        assertEquals("5 проб", computeSelectionLabel(5))
    }

    @Test
    fun label_eleven() {
        // 11 → "11 проб" (не "11 проба").
        assertEquals("11 проб", computeSelectionLabel(11))
    }

    @Test
    fun label_twentyOne() {
        // 21 → "21 проба".
        assertEquals("21 проба", computeSelectionLabel(21))
    }

    @Test
    fun label_negative() {
        assertEquals("", computeSelectionLabel(-3))
    }
}