package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-edit-add-sample/3:
 *  - buildShiftPlans: сдвиг интервалов вместе с номером;
 *  - planInsertPosition (сохранено);
 *  - findCommonWellPrefix, suggestIntervalFrom, findConflict (сохранено).
 */
class EditAddSampleTest {

    private fun row(
        id: String,
        numberInWell: Int,
        sampleNumber: String,
        wellNumber: String = "NV1366",
        intervalFrom: String = "0",
        intervalTo: String = "1",
        isBlank: Boolean = false
    ): SampleRow = SampleRow(
        id = id,
        groupId = "g1",
        serialNumber = numberInWell,
        wellNumber = wellNumber,
        sampleNumber = sampleNumber,
        numberInWell = numberInWell,
        intervalFrom = intervalFrom,
        intervalTo = intervalTo,
        weight = null,
        controlWeight = null,
        type = SampleType.AUGER,
        status = if (isBlank) SampleStatus.BLANK else SampleStatus.NORMAL,
        characteristic = "",
        found = false,
        postponed = false,
        weightControl = false,
        hasNote = false,
        hasPhoto = false,
        hasImportError = false
    )

    // ================================================================
    // buildShiftPlans — сдвиг интервалов
    // ================================================================

    @Test
    fun buildShiftPlans_noShiftEmpty() {
        val plan = InsertPlan(
            insertIndex = 2,
            newNumberInWell = 3,
            shifted = emptyList()
        )
        val result = buildShiftPlans(plan, emptyList(), intervalStep = 2.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun buildShiftPlans_stepNull_intervalsUntouched() {
        // Холостая новая — шага нет.
        val rows = listOf(
            row("1", 2, "NV136602", intervalFrom = "2", intervalTo = "4"),
            row("2", 3, "NV136603", intervalFrom = "4", intervalTo = "6")
        )
        val plan = InsertPlan(
            insertIndex = 1,
            newNumberInWell = 2,
            shifted = listOf(
                ShiftedSample("1", "NV136603"),
                ShiftedSample("2", "NV136604")
            )
        )
        val result = buildShiftPlans(plan, rows, intervalStep = null)
        assertEquals(2, result.size)
        // Номер меняется, интервал — нет.
        assertEquals(2.0, result[0].newIntervalFrom!!, 0.0001)
        assertEquals(4.0, result[0].newIntervalTo!!, 0.0001)
        assertEquals(4.0, result[1].newIntervalFrom!!, 0.0001)
        assertEquals(6.0, result[1].newIntervalTo!!, 0.0001)
    }

    @Test
    fun buildShiftPlans_shiftsIntervalsByStep() {
        // Пробы в скважине. Новая №2 имеет интервал [2, 4], шаг = 2.
        val rows = listOf(
            row("1", 1, "NV136601", intervalFrom = "0", intervalTo = "2"),
            row("2", 2, "NV136602", intervalFrom = "2", intervalTo = "4"),
            row("3", 3, "NV136603", intervalFrom = "4", intervalTo = "6")
        )
        val plan = InsertPlan(
            insertIndex = 1,
            newNumberInWell = 2,
            shifted = listOf(
                ShiftedSample("2", "NV136603"),
                ShiftedSample("3", "NV136604")
            )
        )
        val result = buildShiftPlans(plan, rows, intervalStep = 2.0)
        assertEquals(2, result.size)
        // Бывшая №2 → №3, интервал 2–4 → 4–6.
        assertEquals(4.0, result[0].newIntervalFrom!!, 0.0001)
        assertEquals(6.0, result[0].newIntervalTo!!, 0.0001)
        // Бывшая №3 → №4, интервал 4–6 → 6–8.
        assertEquals(6.0, result[1].newIntervalFrom!!, 0.0001)
        assertEquals(8.0, result[1].newIntervalTo!!, 0.0001)
    }

    @Test
    fun buildShiftPlans_blankStaysBlank() {
        // Холостая проба в скважине — её интервал не сдвигаем.
        val rows = listOf(
            row("1", 1, "NV136601", intervalFrom = "0", intervalTo = "2"),
            row("2", 2, "NV136602", intervalFrom = "—", intervalTo = "—", isBlank = true)
        )
        val plan = InsertPlan(
            insertIndex = 1,
            newNumberInWell = 2,
            shifted = listOf(
                ShiftedSample("2", "NV136603")
            )
        )
        val result = buildShiftPlans(plan, rows, intervalStep = 2.0)
        assertEquals(1, result.size)
        // Интервал не меняется — он и так был "—" → null.
        assertNull(result[0].newIntervalFrom)
        assertNull(result[0].newIntervalTo)
    }

    // ================================================================
    // planInsertPosition
    // ================================================================

    @Test
    fun emptyWell_insertFirst() {
        val plan = planInsertPosition("NV1366", "NV136603", emptyList())
        assertEquals(0, plan.insertIndex)
        assertEquals(3, plan.newNumberInWell)
        assertTrue(plan.shifted.isEmpty())
    }

    @Test
    fun exactMatch_shiftsFromThatPosition() {
        val existing = listOf(
            row("1", 1, "NV136601"),
            row("2", 2, "NV136602"),
            row("3", 3, "NV136603"),
            row("4", 4, "NV136604")
        )
        val plan = planInsertPosition("NV1366", "NV136603", existing)
        assertEquals(2, plan.insertIndex)
        assertEquals(3, plan.newNumberInWell)
        assertEquals(2, plan.shifted.size)
        assertEquals("NV136604", plan.shifted[0].newSampleNumber)
        assertEquals("NV136605", plan.shifted[1].newSampleNumber)
    }

    @Test
    fun greaterThanMax_appendsWithoutShift() {
        val existing = listOf(
            row("1", 1, "NV136601"),
            row("2", 2, "NV136602")
        )
        val plan = planInsertPosition("NV1366", "NV136610", existing)
        assertEquals(2, plan.insertIndex)
        assertTrue(plan.shifted.isEmpty())
    }

    @Test
    fun betweenNumbers_insertsAtPositionWithoutShift() {
        val existing = listOf(
            row("1", 1, "NV136601"),
            row("2", 2, "NV136602"),
            row("4", 4, "NV136604")
        )
        val plan = planInsertPosition("NV1366", "NV136603", existing)
        assertEquals(2, plan.insertIndex)
        assertTrue(plan.shifted.isEmpty())
    }

    // ================================================================
    // findCommonWellPrefix
    // ================================================================

    @Test
    fun findCommonWellPrefix_extractsLetters() {
        val rows = listOf(
            row("1", 1, "ACD210002101", "ACD2100021"),
            row("2", 1, "ACD210002201", "ACD2100022")
        )
        assertEquals("ACD", findCommonWellPrefix(rows))
    }

    @Test
    fun findCommonWellPrefix_differentLetters_returnsEmpty() {
        val rows = listOf(
            row("1", 1, "NV136601", "NV1366"),
            row("2", 1, "ACD210002101", "ACD2100021")
        )
        assertEquals("", findCommonWellPrefix(rows))
    }

    // ================================================================
    // suggestIntervalFrom
    // ================================================================

    @Test
    fun suggestIntervalFrom_usesPreviousNonBlank() {
        val rows = listOf(
            row("1", 1, "NV136601", intervalFrom = "0", intervalTo = "2"),
            row("2", 2, "NV136602", intervalFrom = "2", intervalTo = "4"),
            row("3", 3, "NV136603", intervalFrom = "4", intervalTo = "6")
        )
        assertEquals("6", suggestIntervalFrom(rows, newNumberInWell = 4))
    }

    @Test
    fun suggestIntervalFrom_skipsBlanks() {
        val rows = listOf(
            row("1", 1, "NV136601", intervalFrom = "0", intervalTo = "2"),
            row("2", 2, "NV136602", intervalFrom = "—", intervalTo = "—", isBlank = true),
            row("3", 3, "NV136603", intervalFrom = "2", intervalTo = "4")
        )
        assertEquals("4", suggestIntervalFrom(rows, newNumberInWell = 4))
    }

    // ================================================================
    // findConflict
    // ================================================================

    @Test
    fun findConflict_exactMatch() {
        val rows = listOf(
            row("1", 1, "NV136601", intervalFrom = "0", intervalTo = "2"),
            row("2", 2, "NV136602", intervalFrom = "2", intervalTo = "4")
        )
        val conflict = findConflict(rows, "NV136602")
        assertNotNull(conflict)
        assertEquals("NV136602", conflict!!.existingSampleNumber)
        assertEquals(2, conflict.existingNumberInWell)
        assertEquals("2", conflict.existingIntervalFrom)
        assertEquals("4", conflict.existingIntervalTo)
    }

    @Test
    fun findConflict_noMatch_returnsNull() {
        val rows = listOf(
            row("1", 1, "NV136601")
        )
        assertNull(findConflict(rows, "NV136699"))
    }
}