package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-5:
 * Тесты fallback порядковых номеров пробы.
 *
 * FIX 5.8.11-e4-pin-6:
 * Добавлены тесты isSubstituted() — индикатора подмены номера.
 */
class VoiceMarkOrdinalFallbackTest {

    // ================================================================
    // candidatesFor — список альтернатив
    // ================================================================

    @Test
    fun candidatesForFourHundredContainsFour() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(400)
        assertTrue("400 → должен быть кандидат 4, получено $c", c.contains(4))
    }

    @Test
    fun candidatesForFiveHundredContainsFive() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(500)
        assertTrue("500 → должен быть кандидат 5, получено $c", c.contains(5))
    }

    @Test
    fun candidatesForFortyContainsFour() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(40)
        assertTrue("40 → должен быть кандидат 4, получено $c", c.contains(4))
    }

    @Test
    fun candidatesForNinetyContainsNine() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(90)
        assertTrue("90 → должен быть кандидат 9, получено $c", c.contains(9))
    }

    @Test
    fun candidatesForFourteenContainsFour() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(14)
        assertTrue("14 → должен быть кандидат 4, получено $c", c.contains(4))
    }

    @Test
    fun candidatesForFourContainsFourteen() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(4)
        assertTrue("4 → должен быть кандидат 14, получено $c", c.contains(14))
    }

    @Test
    fun candidatesForNormalOrdinalIsEmpty() {
        // 25 — не круглая сотня, не круглый десяток, не 1..9 с +10.
        val c = VoiceMarkOrdinalFallback.candidatesFor(25)
        assertTrue("25 → кандидатов быть не должно, получено $c", c.isEmpty())
    }

    // ================================================================
    // resolve — подбор с учётом наличия
    // ================================================================

    @Test
    fun resolveExactMatchReturnsOrdinal() {
        val available = setOf(1, 2, 3, 4, 5)
        val result = VoiceMarkOrdinalFallback.resolve(4) { it in available }
        assertEquals(4, result)
    }

    @Test
    fun resolveFourHundredToFour() {
        val available = setOf(1, 2, 3, 4, 5)
        val result = VoiceMarkOrdinalFallback.resolve(400) { it in available }
        assertEquals(4, result)
    }

    @Test
    fun resolveFortyToFour() {
        val available = setOf(1, 2, 3, 4, 5)
        val result = VoiceMarkOrdinalFallback.resolve(40) { it in available }
        assertEquals(4, result)
    }

    @Test
    fun resolveFourteenToFour() {
        val available = setOf(1, 2, 3, 4, 5)
        val result = VoiceMarkOrdinalFallback.resolve(14) { it in available }
        assertEquals(4, result)
    }

    @Test
    fun resolveFourToFourteenWhenNoFour() {
        val available = setOf(11, 12, 13, 14, 15)
        val result = VoiceMarkOrdinalFallback.resolve(4) { it in available }
        assertEquals(14, result)
    }

    @Test
    fun resolveNothingFoundReturnsNull() {
        val available = setOf(7, 8, 9)
        val result = VoiceMarkOrdinalFallback.resolve(400) { it in available }
        assertNull(result)
    }

    // ================================================================
    // FIX 5.8.11-e4-pin-6: isSubstituted
    // ================================================================

    @Test
    fun isSubstituted_trueWhenDifferent() {
        val r = VoiceExecResult.Marked(
            sampleNumber = "NV136604",
            ordinal = 4,
            isWeightControl = false,
            needsWeight = false,
            recognizedOrdinal = 14
        )
        assertTrue("14 → 4: подмена должна быть true", r.isSubstituted())
    }

    @Test
    fun isSubstituted_falseWhenSame() {
        val r = VoiceExecResult.Marked(
            sampleNumber = "NV136604",
            ordinal = 4,
            isWeightControl = false,
            needsWeight = false,
            recognizedOrdinal = 4
        )
        assertFalse("4 → 4: подмены не было", r.isSubstituted())
    }

    @Test
    fun isSubstituted_falseWhenNull() {
        val r = VoiceExecResult.Marked(
            sampleNumber = "NV136604",
            ordinal = 4,
            isWeightControl = false,
            needsWeight = false,
            recognizedOrdinal = null
        )
        assertFalse("null: подмены не было", r.isSubstituted())
    }
}
