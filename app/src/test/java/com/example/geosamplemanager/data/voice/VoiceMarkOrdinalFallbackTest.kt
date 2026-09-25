package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-7:
 * Тесты подсказок при промахе по номеру пробы.
 *
 * Раньше здесь был fallback (14 → 4). Теперь — подсказка: «Пробы №14
 * нет, если нужна №4 — скажите „четыре"». Пользователь уточняет
 * числом — количественные Vosk не путает.
 */
class VoiceMarkOrdinalFallbackTest {

    // ================================================================
    // candidatesFor
    // ================================================================

    @Test
    fun candidatesForFourteenContainsFour() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(14)
        assertTrue("14 → должен быть 4, получено $c", c.contains(4))
    }

    @Test
    fun candidatesForFifteenContainsFive() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(15)
        assertTrue("15 → должен быть 5, получено $c", c.contains(5))
    }

    @Test
    fun candidatesForNineteenContainsNine() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(19)
        assertTrue("19 → должен быть 9, получено $c", c.contains(9))
    }

    @Test
    fun candidatesForFortyContainsFour() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(40)
        assertTrue("40 → должен быть 4, получено $c", c.contains(4))
    }

    @Test
    fun candidatesForNinetyContainsNine() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(90)
        assertTrue("90 → должен быть 9, получено $c", c.contains(9))
    }

    @Test
    fun candidatesForFourHundredContainsFour() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(400)
        assertTrue("400 → должен быть 4, получено $c", c.contains(4))
    }

    @Test
    fun candidatesForFiveHundredContainsFive() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(500)
        assertTrue("500 → должен быть 5, получено $c", c.contains(5))
    }

    @Test
    fun candidatesForFourThousandContainsFour() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(4000)
        assertTrue("4000 → должен быть 4, получено $c", c.contains(4))
    }

    @Test
    fun candidatesForFourContainsFourteenAndFortyAndMore() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(4)
        assertTrue("4 → должен быть 14, получено $c", c.contains(14))
        assertTrue("4 → должен быть 40, получено $c", c.contains(40))
        assertTrue("4 → должен быть 400, получено $c", c.contains(400))
        assertTrue("4 → должен быть 4000, получено $c", c.contains(4000))
    }

    @Test
    fun candidatesForTwentyFiveIsEmpty() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(25)
        assertTrue("25 → кандидатов быть не должно, получено $c", c.isEmpty())
    }

    @Test
    fun candidatesForTwentyIsEmpty() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(20)
        assertTrue("20 → кандидатов быть не должно, получено $c", c.isEmpty())
    }

    @Test
    fun candidatesForHundredIsEmpty() {
        // Круглые сотни 100, 200, 300 не «спорные» — Vosk их не путает
        // с 1, 2, 3.
        val c = VoiceMarkOrdinalFallback.candidatesFor(100)
        assertTrue("100 → кандидатов быть не должно, получено $c", c.isEmpty())
    }

    // ================================================================
    // hintFor
    // ================================================================

    @Test
    fun hintForFourteenIsFour() {
        assertEquals(4, VoiceMarkOrdinalFallback.hintFor(14))
    }

    @Test
    fun hintForFortyIsFour() {
        assertEquals(4, VoiceMarkOrdinalFallback.hintFor(40))
    }

    @Test
    fun hintForFourHundredIsFour() {
        assertEquals(4, VoiceMarkOrdinalFallback.hintFor(400))
    }

    @Test
    fun hintForFourThousandIsFour() {
        assertEquals(4, VoiceMarkOrdinalFallback.hintFor(4000))
    }

    @Test
    fun hintForFourIsFourteen() {
        // Для 4 первым кандидатом идёт 14 (самая частая путаница).
        assertEquals(14, VoiceMarkOrdinalFallback.hintFor(4))
    }

    @Test
    fun hintForTwentyFiveIsNull() {
        assertNull(VoiceMarkOrdinalFallback.hintFor(25))
    }

    @Test
    fun hintForTwentyIsNull() {
        assertNull(VoiceMarkOrdinalFallback.hintFor(20))
    }

    @Test
    fun hintForOneIsNull() {
        // 1..3 Vosk не путает с «-надцат» / «-десят».
        assertNull(VoiceMarkOrdinalFallback.hintFor(1))
    }
}
