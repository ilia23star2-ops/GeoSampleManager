package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-7:
 * Тесты подсказок при промахе по номеру пробы.
 *
 * Раньше здесь был fallback (14 → 4). Теперь — подсказка:
 * «Пробы №14 нет, если нужна №4 — скажите „четыре"».
 *
 * Vosk путает порядковые с общим корнем «четыр» / «пят» / … / «девят»:
 * 4 ↔ 14 ↔ 40 ↔ 400 ↔ 4000. Для 1..3 корни разные («перв», «втор»,
 * «трет») — подсказок нет.
 */
class VoiceMarkOrdinalFallbackTest {

    // ================================================================
    // candidatesFor — прямые двойники (14..19 → 4..9)
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

    // ================================================================
    // candidatesFor — круглые десятки/сотни/тысячи → 4..9
    // ================================================================

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

    // ================================================================
    // candidatesFor — обратные двойники (4..9 → +10 / ×10 / ×100 / ×1000)
    // ================================================================

    @Test
    fun candidatesForFourContainsFourteenAndFortyAndMore() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(4)
        assertTrue("4 → должен быть 14, получено $c", c.contains(14))
        assertTrue("4 → должен быть 40, получено $c", c.contains(40))
        assertTrue("4 → должен быть 400, получено $c", c.contains(400))
        assertTrue("4 → должен быть 4000, получено $c", c.contains(4000))
    }

    @Test
    fun candidatesForNineContainsNineteenAndNinetyAndMore() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(9)
        assertTrue("9 → должен быть 19, получено $c", c.contains(19))
        assertTrue("9 → должен быть 90, получено $c", c.contains(90))
        assertTrue("9 → должен быть 900, получено $c", c.contains(900))
        assertTrue("9 → должен быть 9000, получено $c", c.contains(9000))
    }

    // ================================================================
    // candidatesFor — пустые случаи
    // ================================================================

    @Test
    fun candidatesForOneIsEmpty() {
        // «первая» Vosk не путает с «десятая»/«сотая» — корни разные.
        val c = VoiceMarkOrdinalFallback.candidatesFor(1)
        assertTrue("1 → кандидатов быть не должно, получено $c", c.isEmpty())
    }

    @Test
    fun candidatesForTwoIsEmpty() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(2)
        assertTrue("2 → кандидатов быть не должно, получено $c", c.isEmpty())
    }

    @Test
    fun candidatesForThreeIsEmpty() {
        val c = VoiceMarkOrdinalFallback.candidatesFor(3)
        assertTrue("3 → кандидатов быть не должно, получено $c", c.isEmpty())
    }

    @Test
    fun candidatesForTenIsEmpty() {
        // 10..13 — не спорные.
        val c = VoiceMarkOrdinalFallback.candidatesFor(10)
        assertTrue("10 → кандидатов быть не должно, получено $c", c.isEmpty())
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
        // 100, 200, 300 Vosk не путает с 1, 2, 3.
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
    fun hintForOneIsNull() {
        // 1 — не спорное, подсказки нет.
        assertNull(VoiceMarkOrdinalFallback.hintFor(1))
    }

    @Test
    fun hintForTwoIsNull() {
        assertNull(VoiceMarkOrdinalFallback.hintFor(2))
    }

    @Test
    fun hintForThreeIsNull() {
        assertNull(VoiceMarkOrdinalFallback.hintFor(3))
    }

    @Test
    fun hintForTwentyFiveIsNull() {
        assertNull(VoiceMarkOrdinalFallback.hintFor(25))
    }

    @Test
    fun hintForTwentyIsNull() {
        assertNull(VoiceMarkOrdinalFallback.hintFor(20))
    }
}
