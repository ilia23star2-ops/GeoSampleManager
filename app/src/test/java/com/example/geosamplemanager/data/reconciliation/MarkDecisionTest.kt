package com.example.geosamplemanager.data.reconciliation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты доменного ядра отметки.
 *
 * FIX 5.8.9d-3a: чистая логика — без Android, Compose, ViewModel.
 */
class MarkDecisionTest {

    // ================================================================
    // Фабрика контекста
    // ================================================================

    private fun ctx(
        found: Boolean = false,
        hasImportError: Boolean = false,
        postponed: Boolean = false,
        weightControl: Boolean = false,
        controlWeight: Double? = null,
        isBlank: Boolean = false,
        weight: Double? = null,
        ordinal: Int = 1,
        sampleNumber: String = "NV152401",
        blankWeightMode: BlankWeightPolicy = BlankWeightPolicy.MANUAL,
        fixedWeight: Double? = null,
        averageWeight: Double? = null
    ): MarkContext = MarkContext(
        hasImportError = hasImportError,
        found = found,
        postponed = postponed,
        weightControl = weightControl,
        controlWeight = controlWeight,
        isBlank = isBlank,
        weight = weight,
        ordinal = ordinal,
        sampleNumber = sampleNumber,
        blankWeightMode = blankWeightMode,
        fixedWeight = fixedWeight,
        averageWeight = averageWeight
    )

    // ================================================================
    // analyzeMark — порядок и приоритеты
    // ================================================================

    @Test
    fun found_returnsAlreadyFound() {
        val d = analyzeMark(ctx(found = true))
        assertTrue(d is MarkDecision.AlreadyFound)
    }

    @Test
    fun found_overridesImportError() {
        val d = analyzeMark(ctx(found = true, hasImportError = true))
        assertTrue(d is MarkDecision.AlreadyFound)
    }

    @Test
    fun importError_returnsImportError() {
        val d = analyzeMark(ctx(hasImportError = true))
        assertTrue(d is MarkDecision.ImportError)
    }

    @Test
    fun postponed_returnsPostponed() {
        val d = analyzeMark(ctx(postponed = true))
        assertTrue(d is MarkDecision.Postponed)
    }

    @Test
    fun importError_overridesPostponed() {
        val d = analyzeMark(ctx(hasImportError = true, postponed = true))
        assertTrue(d is MarkDecision.ImportError)
    }

    // ================================================================
    // Весовой контроль
    // ================================================================

    @Test
    fun controlWithoutWeight_returnsNeedsControlWeight() {
        val d = analyzeMark(ctx(weightControl = true, controlWeight = null))
        assertTrue(d is MarkDecision.NeedsControlWeight)
    }

    @Test
    fun controlWithWeight_returnsCanMark() {
        val d = analyzeMark(ctx(weightControl = true, controlWeight = 2.5))
        assertTrue(d is MarkDecision.CanMark)
    }

    // ================================================================
    // Холостые
    // ================================================================

    @Test
    fun blankManualWithoutWeight_returnsNeedsBlankWeight() {
        val d = analyzeMark(
            ctx(isBlank = true, weight = null,
                blankWeightMode = BlankWeightPolicy.MANUAL)
        )
        assertTrue(d is MarkDecision.NeedsBlankWeight)
    }

    @Test
    fun blankFixedWithValue_returnsMarkWithWeight() {
        val d = analyzeMark(
            ctx(isBlank = true, weight = null,
                blankWeightMode = BlankWeightPolicy.FIXED,
                fixedWeight = 2.5)
        )
        assertTrue(d is MarkDecision.MarkWithWeight)
        assertEquals(2.5, (d as MarkDecision.MarkWithWeight).weight, 0.0001)
    }

    @Test
    fun blankFixedWithoutValue_returnsNeedsBlankWeight() {
        val d = analyzeMark(
            ctx(isBlank = true, weight = null,
                blankWeightMode = BlankWeightPolicy.FIXED,
                fixedWeight = null)
        )
        assertTrue(d is MarkDecision.NeedsBlankWeight)
    }

    @Test
    fun blankAverageWithValue_returnsMarkWithWeight() {
        val d = analyzeMark(
            ctx(isBlank = true, weight = null,
                blankWeightMode = BlankWeightPolicy.AVERAGE,
                averageWeight = 3.0)
        )
        assertTrue(d is MarkDecision.MarkWithWeight)
        assertEquals(3.0, (d as MarkDecision.MarkWithWeight).weight, 0.0001)
    }

    @Test
    fun blankAverageWithoutValue_returnsNeedsBlankWeight() {
        val d = analyzeMark(
            ctx(isBlank = true, weight = null,
                blankWeightMode = BlankWeightPolicy.AVERAGE,
                averageWeight = null)
        )
        assertTrue(d is MarkDecision.NeedsBlankWeight)
    }

    @Test
    fun blankWithWeight_returnsCanMark() {
        val d = analyzeMark(ctx(isBlank = true, weight = 2.5))
        assertTrue(d is MarkDecision.CanMark)
    }

    // ================================================================
    // Обычная проба
    // ================================================================

    @Test
    fun normalSample_returnsCanMark() {
        val d = analyzeMark(ctx())
        assertTrue(d is MarkDecision.CanMark)
    }

    // ================================================================
    // Контекст в решении
    // ================================================================

    @Test
    fun decisionCarriesOrdinalAndSampleNumber() {
        val d = analyzeMark(
            ctx(found = true, ordinal = 5, sampleNumber = "NV152405")
        )
        assertEquals(5, d.ordinal)
        assertEquals("NV152405", d.sampleNumber)
    }

    @Test
    fun canMarkCarriesContext() {
        val d = analyzeMark(ctx(ordinal = 3, sampleNumber = "NV152403"))
        assertEquals(3, d.ordinal)
        assertEquals("NV152403", d.sampleNumber)
    }

    // ================================================================
    // validateWeight
    // ================================================================

    @Test
    fun weightValid_returnsOk() {
        val r = validateWeight(2.6)
        assertTrue(r is WeightValidation.Ok)
        assertEquals(2.6, (r as WeightValidation.Ok).value, 0.0001)
    }

    @Test
    fun weightNull_returnsInvalidNull() {
        val r = validateWeight(null)
        assertTrue(r is WeightValidation.Invalid)
        assertEquals(WeightInvalidReason.NULL, (r as WeightValidation.Invalid).reason)
    }

    @Test
    fun weightZero_returnsInvalidNotPositive() {
        val r = validateWeight(0.0)
        assertTrue(r is WeightValidation.Invalid)
        assertEquals(
            WeightInvalidReason.NOT_POSITIVE,
            (r as WeightValidation.Invalid).reason
        )
    }

    @Test
    fun weightNegative_returnsInvalidNotPositive() {
        val r = validateWeight(-1.5)
        assertTrue(r is WeightValidation.Invalid)
        assertEquals(
            WeightInvalidReason.NOT_POSITIVE,
            (r as WeightValidation.Invalid).reason
        )
    }

    @Test
    fun weightNaN_returnsInvalidNotFinite() {
        val r = validateWeight(Double.NaN)
        assertTrue(r is WeightValidation.Invalid)
        assertEquals(
            WeightInvalidReason.NOT_FINITE,
            (r as WeightValidation.Invalid).reason
        )
    }

    @Test
    fun weightPositiveInfinity_returnsInvalidNotFinite() {
        val r = validateWeight(Double.POSITIVE_INFINITY)
        assertTrue(r is WeightValidation.Invalid)
        assertEquals(
            WeightInvalidReason.NOT_FINITE,
            (r as WeightValidation.Invalid).reason
        )
    }

    @Test
    fun weightAboveMax_returnsInvalidTooLarge() {
        val r = validateWeight(101.0)
        assertTrue(r is WeightValidation.Invalid)
        assertEquals(
            WeightInvalidReason.TOO_LARGE,
            (r as WeightValidation.Invalid).reason
        )
    }

    @Test
    fun weightAtMax_returnsOk() {
        val r = validateWeight(100.0)
        assertTrue(r is WeightValidation.Ok)
        assertEquals(100.0, (r as WeightValidation.Ok).value, 0.0001)
    }

    @Test
    fun weightRounds_2_678_to_2_68() {
        val r = validateWeight(2.678)
        assertTrue(r is WeightValidation.Ok)
        assertEquals(2.68, (r as WeightValidation.Ok).value, 0.0001)
    }

    @Test
    fun weightRounds_2_6_stays_2_6() {
        val r = validateWeight(2.6)
        assertTrue(r is WeightValidation.Ok)
        assertEquals(2.6, (r as WeightValidation.Ok).value, 0.0001)
    }

    @Test
    fun weightCustomMax_usesCustomBound() {
        val r = validateWeight(15.0, maxWeight = 10.0)
        assertTrue(r is WeightValidation.Invalid)
        assertEquals(
            WeightInvalidReason.TOO_LARGE,
            (r as WeightValidation.Invalid).reason
        )
    }
}
