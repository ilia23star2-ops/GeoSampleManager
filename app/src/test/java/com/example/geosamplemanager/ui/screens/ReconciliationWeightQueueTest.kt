package com.example.geosamplemanager.ui.screens

import com.example.geosamplemanager.data.voice.WeightQueueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-weight-queue:
 * Тесты построения очереди веса.
 */
class ReconciliationWeightQueueTest {

    private fun row(
        sampleNumber: String = "NV136601",
        ordinal: Int = 1,
        found: Boolean = false,
        isBlank: Boolean = false,
        weightControl: Boolean = false,
        weight: Double? = null,
        controlWeight: Double? = null,
        hasImportError: Boolean = false
    ): SampleRow = SampleRow(
        id = "id_$sampleNumber",
        groupId = "g1",
        serialNumber = ordinal,
        wellNumber = "NV1366",
        sampleNumber = sampleNumber,
        numberInWell = ordinal,
        intervalFrom = "",
        intervalTo = "",
        weight = weight,
        controlWeight = controlWeight,
        type = SampleType.AUGER,
        status = if (isBlank) SampleStatus.BLANK else SampleStatus.NORMAL,
        characteristic = "",
        found = found,
        postponed = false,
        weightControl = weightControl,
        hasNote = false,
        hasPhoto = false,
        hasImportError = hasImportError
    )

    // ================================================================

    @Test
    fun emptyWhenNoRows() {
        assertEquals(emptyList<Any>(), buildWeightQueue(emptyList()))
    }

    @Test
    fun skipsAlreadyFoundRows() {
        val rows = listOf(
            row(sampleNumber = "A", ordinal = 1, isBlank = true, found = true),
            row(sampleNumber = "B", ordinal = 2, weightControl = true, found = true)
        )
        assertEquals(emptyList<Any>(), buildWeightQueue(rows))
    }

    @Test
    fun skipsImportErrorRows() {
        val rows = listOf(
            row(
                sampleNumber = "A",
                ordinal = 1,
                isBlank = true,
                hasImportError = true
            )
        )
        assertEquals(emptyList<Any>(), buildWeightQueue(rows))
    }

    @Test
    fun skipsNormalRows() {
        val rows = listOf(row(sampleNumber = "A", ordinal = 1))
        assertEquals(emptyList<Any>(), buildWeightQueue(rows))
    }

    @Test
    fun skipsBlankWithWeight() {
        val rows = listOf(
            row(sampleNumber = "A", ordinal = 1, isBlank = true, weight = 2.5)
        )
        assertEquals(emptyList<Any>(), buildWeightQueue(rows))
    }

    @Test
    fun skipsControlWithWeight() {
        val rows = listOf(
            row(
                sampleNumber = "A",
                ordinal = 1,
                weightControl = true,
                controlWeight = 2.5
            )
        )
        assertEquals(emptyList<Any>(), buildWeightQueue(rows))
    }

    // ================================================================

    @Test
    fun blankWithoutWeightAdded() {
        val rows = listOf(
            row(sampleNumber = "NV136605", ordinal = 5, isBlank = true)
        )
        val q = buildWeightQueue(rows)
        assertEquals(1, q.size)
        assertEquals("NV136605", q[0].sampleNumber)
        assertEquals(5, q[0].ordinal)
        assertEquals(WeightQueueKind.BLANK, q[0].kind)
    }

    @Test
    fun controlWithoutWeightAdded() {
        val rows = listOf(
            row(sampleNumber = "NV136609", ordinal = 9, weightControl = true)
        )
        val q = buildWeightQueue(rows)
        assertEquals(1, q.size)
        assertEquals(WeightQueueKind.WEIGHT_CONTROL, q[0].kind)
    }

    @Test
    fun mixedRowsInOrder() {
        val rows = listOf(
            row(sampleNumber = "NV136601", ordinal = 1),
            row(sampleNumber = "NV136602", ordinal = 2, isBlank = true),
            row(sampleNumber = "NV136603", ordinal = 3),
            row(sampleNumber = "NV136604", ordinal = 4, isBlank = true, weight = 2.5),
            row(sampleNumber = "NV136605", ordinal = 5, isBlank = true),
            row(sampleNumber = "NV136606", ordinal = 6, weightControl = true),
            row(sampleNumber = "NV136607", ordinal = 7, weightControl = true, controlWeight = 2.6)
        )
        val q = buildWeightQueue(rows)
        assertEquals(3, q.size)
        assertEquals(2, q[0].ordinal)
        assertEquals(WeightQueueKind.BLANK, q[0].kind)
        assertEquals(5, q[1].ordinal)
        assertEquals(WeightQueueKind.BLANK, q[1].kind)
        assertEquals(6, q[2].ordinal)
        assertEquals(WeightQueueKind.WEIGHT_CONTROL, q[2].kind)
    }

    @Test
    fun orderPreserved() {
        val rows = listOf(
            row(sampleNumber = "A", ordinal = 10, isBlank = true),
            row(sampleNumber = "B", ordinal = 2, isBlank = true),
            row(sampleNumber = "C", ordinal = 7, isBlank = true)
        )
        val q = buildWeightQueue(rows)
        assertEquals(listOf(10, 2, 7), q.map { it.ordinal })
    }

    @Test
    fun noQueueWhenAllMarkable() {
        val rows = listOf(
            row(sampleNumber = "A", ordinal = 1),
            row(sampleNumber = "B", ordinal = 2),
            row(sampleNumber = "C", ordinal = 3, isBlank = true, weight = 2.5)
        )
        assertTrue(buildWeightQueue(rows).isEmpty())
    }
}
