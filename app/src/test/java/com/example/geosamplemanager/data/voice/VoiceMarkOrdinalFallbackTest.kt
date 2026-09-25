package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-5:
 * Тесты fallback порядковых номеров пробы.
 *
 * Vosk путает «четвёртая» с «четыреста» (4 ↔ 400), «сорок» (4 ↔ 40),
 * «четырнадцатая» (4 ↔ 14). Fallback пробует альтернативы.
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
        // 7 — не круглая сотня, не круглый десяток, не 1..9 + 10
        // наоборот: 7 в 1..9 → +10 = 17. Значит не пусто.
        // Проверим другой: 25 — вообще ничего.
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
}
