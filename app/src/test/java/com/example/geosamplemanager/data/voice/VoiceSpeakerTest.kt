package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4e-bundle/5:
 * Тесты мимикрии озвучки — spellOut(groups).
 *
 * Проверяем:
 *  - префикс одним словом («капэдэ», не «ка пэ дэ»);
 *  - без запятых между группами;
 *  - «W» → «даблю»;
 *  - пустой список → пустая строка;
 *  - числа и группы слов.
 */
class VoiceSpeakerTest {

    @Test
    fun prefixKpdOneWord() {
        val groups = listOf(
            DigitGroup("KPD", GroupKind.PREFIX),
            DigitGroup("109", GroupKind.PLAIN),
            DigitGroup("00", GroupKind.LEADING_ZERO),
            DigitGroup("31", GroupKind.PLAIN)
        )
        val result = VoiceSpeaker.spellOut(groups)
        assertEquals("капэдэ сто девять ноль ноль тридцать один", result)
    }

    @Test
    fun noCommasBetweenGroups() {
        val groups = listOf(
            DigitGroup("109", GroupKind.PLAIN),
            DigitGroup("00", GroupKind.LEADING_ZERO),
            DigitGroup("31", GroupKind.PLAIN)
        )
        val result = VoiceSpeaker.spellOut(groups)
        assertEquals("сто девять ноль ноль тридцать один", result)
    }

    @Test
    fun singleDigitSpelledAsWord() {
        val groups = listOf(DigitGroup("7", GroupKind.SINGLE))
        assertEquals("семь", VoiceSpeaker.spellOut(groups))
    }

    @Test
    fun plainTwoDigits() {
        val groups = listOf(DigitGroup("15", GroupKind.PLAIN))
        assertEquals("пятнадцать", VoiceSpeaker.spellOut(groups))
    }

    @Test
    fun plainThreeDigits() {
        val groups = listOf(DigitGroup("109", GroupKind.PLAIN))
        assertEquals("сто девять", VoiceSpeaker.spellOut(groups))
    }

    @Test
    fun wLetterAsDablyu() {
        val groups = listOf(DigitGroup("W", GroupKind.PREFIX))
        assertEquals("даблю", VoiceSpeaker.spellOut(groups))
    }

    @Test
    fun leadingZeroDigitByDigit() {
        val groups = listOf(DigitGroup("00", GroupKind.LEADING_ZERO))
        assertEquals("ноль ноль", VoiceSpeaker.spellOut(groups))
    }

    @Test
    fun emptyGroups() {
        assertEquals("", VoiceSpeaker.spellOut(emptyList()))
    }

    @Test
    fun prefixNvOneWord() {
        val groups = listOf(
            DigitGroup("NV", GroupKind.PREFIX),
            DigitGroup("1524", GroupKind.PLAIN),
            DigitGroup("01", GroupKind.PLAIN)
        )
        val result = VoiceSpeaker.spellOut(groups)
        assertEquals("энвэ пятнадцать двадцать четыре ноль один", result)
    }
}