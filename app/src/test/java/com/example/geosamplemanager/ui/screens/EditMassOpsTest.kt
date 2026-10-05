package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.9-edit-mass-ops/2:
 *  - applyMassEditToRow синхронизирует status и weightControl:
 *    CONTROL → weightControl = true;
 *    NORMAL/BLANK → weightControl = false;
 *    null → weightControl не трогаем.
 */
class EditMassOpsTest {

    private fun row(
        characteristic: String = "Делювий",
        type: SampleType = SampleType.AUGER,
        status: SampleStatus = SampleStatus.NORMAL,
        weightControl: Boolean = false
    ): SampleRow = SampleRow(
        id = "1",
        groupId = "g1",
        serialNumber = 1,
        wellNumber = "NV1366",
        sampleNumber = "NV136601",
        numberInWell = 1,
        intervalFrom = "0",
        intervalTo = "2",
        weight = null,
        controlWeight = null,
        type = type,
        status = status,
        characteristic = characteristic,
        found = false,
        postponed = false,
        weightControl = weightControl,
        hasNote = false,
        hasPhoto = false,
        hasImportError = false
    )

    // ================================================================
    // hasAny
    // ================================================================

    @Test
    fun hasAny_empty() {
        assertEquals(false, MassEditFields().hasAny)
    }

    @Test
    fun hasAny_onlyCharacteristic() {
        assertEquals(true, MassEditFields(characteristic = "x").hasAny)
    }

    @Test
    fun hasAny_onlyType() {
        assertEquals(true, MassEditFields(type = SampleType.CHANNEL).hasAny)
    }

    @Test
    fun hasAny_onlyStatus() {
        assertEquals(true, MassEditFields(status = SampleStatus.BLANK).hasAny)
    }

    // ================================================================
    // applyMassEditToRow — базовые
    // ================================================================

    @Test
    fun edit_allNull_rowUnchanged() {
        val r = row(
            characteristic = "Элювий",
            type = SampleType.COBRA,
            status = SampleStatus.CONTROL,
            weightControl = true
        )
        val result = applyMassEditToRow(r, MassEditFields())
        assertEquals(r, result)
    }

    @Test
    fun edit_onlyCharacteristic() {
        val r = row()
        val result = applyMassEditToRow(r, MassEditFields(characteristic = "Новая"))
        assertEquals("Новая", result.characteristic)
        assertEquals(r.type, result.type)
        assertEquals(r.status, result.status)
        assertEquals(r.weightControl, result.weightControl)
    }

    @Test
    fun edit_onlyType() {
        val r = row()
        val result = applyMassEditToRow(r, MassEditFields(type = SampleType.CHANNEL))
        assertEquals(SampleType.CHANNEL, result.type)
        assertEquals(r.characteristic, result.characteristic)
        assertEquals(r.status, result.status)
        assertEquals(r.weightControl, result.weightControl)
    }

    @Test
    fun edit_allThreeNonControl() {
        val r = row()
        val result = applyMassEditToRow(
            r,
            MassEditFields(
                characteristic = "Дубль",
                type = SampleType.COBRA,
                status = SampleStatus.NORMAL
            )
        )
        assertEquals("Дубль", result.characteristic)
        assertEquals(SampleType.COBRA, result.type)
        assertEquals(SampleStatus.NORMAL, result.status)
    }

    @Test
    fun edit_preservesOtherFields() {
        val r = row(characteristic = "Старая")
        val result = applyMassEditToRow(r, MassEditFields(characteristic = "Новая"))
        assertEquals(r.id, result.id)
        assertEquals(r.groupId, result.groupId)
        assertEquals(r.sampleNumber, result.sampleNumber)
        assertEquals(r.wellNumber, result.wellNumber)
        assertEquals(r.numberInWell, result.numberInWell)
        assertEquals(r.intervalFrom, result.intervalFrom)
        assertEquals(r.intervalTo, result.intervalTo)
        assertEquals(r.weight, result.weight)
        assertEquals(r.found, result.found)
        assertEquals(r.postponed, result.postponed)
    }

    // ================================================================
    // Синхронизация status ↔ weightControl
    // ================================================================

    @Test
    fun statusControl_setsWeightControlTrue() {
        val r = row(status = SampleStatus.NORMAL, weightControl = false)
        val result = applyMassEditToRow(
            r,
            MassEditFields(status = SampleStatus.CONTROL)
        )
        assertEquals(SampleStatus.CONTROL, result.status)
        assertEquals(true, result.weightControl)
    }

    @Test
    fun statusNormal_clearsWeightControl() {
        val r = row(status = SampleStatus.CONTROL, weightControl = true)
        val result = applyMassEditToRow(
            r,
            MassEditFields(status = SampleStatus.NORMAL)
        )
        assertEquals(SampleStatus.NORMAL, result.status)
        assertEquals(false, result.weightControl)
    }

    @Test
    fun statusBlank_clearsWeightControl() {
        val r = row(status = SampleStatus.CONTROL, weightControl = true)
        val result = applyMassEditToRow(
            r,
            MassEditFields(status = SampleStatus.BLANK)
        )
        assertEquals(SampleStatus.BLANK, result.status)
        assertEquals(false, result.weightControl)
    }

    @Test
    fun statusNull_keepsWeightControl() {
        val r = row(status = SampleStatus.CONTROL, weightControl = true)
        val result = applyMassEditToRow(
            r,
            MassEditFields(characteristic = "x")
        )
        assertEquals(SampleStatus.CONTROL, result.status)
        assertEquals(true, result.weightControl)
    }

    @Test
    fun statusNull_keepsWeightControlFalse() {
        val r = row(status = SampleStatus.NORMAL, weightControl = false)
        val result = applyMassEditToRow(
            r,
            MassEditFields(type = SampleType.COBRA)
        )
        assertEquals(SampleStatus.NORMAL, result.status)
        assertEquals(false, result.weightControl)
    }
}