package com.example.geosamplemanager.data.logs

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus
import com.example.geosamplemanager.ui.screens.SampleType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-logs-4b:
 * Проверяем формирование фразы с изменениями пробы.
 * Используем реальный SampleRow. Сигнатура конструктора
 * известна из компилятора:
 *   (id, groupId, serialNumber, wellNumber, sampleNumber,
 *    numberInWell, intervalFrom, intervalTo, weight, controlWeight,
 *    type, status, characteristic, found, postponed, weightControl,
 *    hasNote, hasPhoto, hasImportError)
 */
class SampleRowDiffTest {

    // ============================================================
    // Helpers
    // ============================================================

    private fun row(
        sampleNumber: String = "152401",
        wellNumber: String = "1524",
        intervalFrom: String = "1.0",
        intervalTo: String = "2.0",
        weight: Double? = 2.5,
        controlWeight: Double? = null,
        characteristic: String = "Суглинок",
        type: SampleType = SampleType.values().first(),
        status: SampleStatus = SampleStatus.values().first(),
        found: Boolean = false,
        postponed: Boolean = false,
        weightControl: Boolean = false
    ): SampleRow = SampleRow(
        id = "1",
        groupId = "1",
        wellNumber = wellNumber,
        sampleNumber = sampleNumber,
        numberInWell = 1,
        intervalFrom = intervalFrom,
        intervalTo = intervalTo,
        weight = weight,
        controlWeight = controlWeight,
        type = type,
        status = status,
        characteristic = characteristic,
        found = found,
        postponed = postponed,
        weightControl = weightControl,
        hasNote = false,
        hasPhoto = false,
        hasImportError = false
    )

    // ============================================================
    // Без изменений
    // ============================================================

    @Test
    fun noChangesReturnsNull() {
        val a = row()
        val b = row()
        assertNull(SampleRowDiff.diff(a, b))
    }

    // ============================================================
    // Одиночные изменения
    // ============================================================

    @Test
    fun sampleNumberChange() {
        val diff = SampleRowDiff.diff(row(sampleNumber = "03"), row(sampleNumber = "05"))
        assertNotNull(diff)
        assertTrue(diff!!.contains("номер"))
        assertTrue(diff.contains("03"))
        assertTrue(diff.contains("05"))
    }

    @Test
    fun wellNumberChange() {
        val diff = SampleRowDiff.diff(row(wellNumber = "1524"), row(wellNumber = "1525"))
        assertNotNull(diff)
        assertTrue(diff!!.contains("скважина"))
        assertTrue(diff.contains("1524"))
        assertTrue(diff.contains("1525"))
    }

    @Test
    fun weightChange() {
        val diff = SampleRowDiff.diff(row(weight = 2.5), row(weight = 3.0))
        assertNotNull(diff)
        assertTrue(diff!!.contains("вес"))
        assertTrue(diff.contains("2,5"))
        assertTrue(diff.contains("3"))
    }

    @Test
    fun foundChange() {
        val diff = SampleRowDiff.diff(row(found = false), row(found = true))
        assertNotNull(diff)
        assertTrue(diff!!.contains("отметка установлена"))
    }

    @Test
    fun foundRemovedChange() {
        val diff = SampleRowDiff.diff(row(found = true), row(found = false))
        assertNotNull(diff)
        assertTrue(diff!!.contains("отметка снята"))
    }

    @Test
    fun postponedChange() {
        val diff = SampleRowDiff.diff(row(postponed = false), row(postponed = true))
        assertNotNull(diff)
        assertTrue(diff!!.contains("проба отложена"))
    }

    @Test
    fun weightControlChange() {
        val diff = SampleRowDiff.diff(row(weightControl = false), row(weightControl = true))
        assertNotNull(diff)
        assertTrue(diff!!.contains("ВК установлен"))
    }

    @Test
    fun characteristicChange() {
        val diff = SampleRowDiff.diff(
            row(characteristic = "Суглинок"),
            row(characteristic = "Песок")
        )
        assertNotNull(diff)
        assertTrue(diff!!.contains("характеристика"))
        assertTrue(diff.contains("Суглинок"))
        assertTrue(diff.contains("Песок"))
    }

    // ============================================================
    // Множественные изменения
    // ============================================================

    @Test
    fun multipleChangesJoinedByComma() {
        val diff = SampleRowDiff.diff(
            row(sampleNumber = "03", weight = 2.5),
            row(sampleNumber = "05", weight = 3.0)
        )
        assertNotNull(diff)
        assertTrue(diff!!.contains("номер"))
        assertTrue(diff.contains("вес"))
        assertTrue(diff.contains(", "))
    }

    @Test
    fun emptyStringsShowAsPusto() {
        val diff = SampleRowDiff.diff(
            row(characteristic = "Суглинок"),
            row(characteristic = "")
        )
        assertNotNull(diff)
        assertTrue(diff!!.contains("(пусто)"))
    }

    @Test
    fun nullWeightShowsAsPusto() {
        val diff = SampleRowDiff.diff(row(weight = 2.5), row(weight = null))
        assertNotNull(diff)
        assertTrue(diff!!.contains("(пусто)"))
    }
}