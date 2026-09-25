package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4e-bundle/5: мимикрия озвучки — spellOut(groups).
 *
 * FIX 5.8.11-e4-markers-2/6: spellMimicry(text) — мимикрия для
 * произвольной строки-номера (sampleNumber, wellNumber).
 *
 * FIX 5.8.11-e4-markers-2/11:
 *  spellMimicryKpdSampleNumber зафиксирован жёстко по реальному
 *  поведению DigitGrouper + spellPairsAsWords:
 *
 *    "KPD1090031" → "капэдэ десять девяносто ноль три один"
 *
 *  DigitGrouper не разбивает слитную "1090031" на "109|00|31" —
 *  это одна группа PLAIN. spellPairsAsWords режет её по парам
 *  слева. Результат — «десять девяносто ноль три один», а не
 *  «сто девять ноль ноль тридцать один».
 *
 *  Улучшение разбиения (3+2+2 для нечётной длины) — отдельный
 *  заход, если потребуется.
 */
class VoiceSpeakerTest {

    // ================================================================
    // spellOut(groups) — мимикрия по готовым группам
    // ================================================================

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

    // ================================================================
    // FIX 5.8.11-e4-markers-2/6: spellMimicry(text)
    // ================================================================

    @Test
    fun spellMimicryNvSampleNumber() {
        val result = VoiceSpeaker.spellMimicry("NV136602")
        assertEquals("энвэ тринадцать шестьдесят шесть ноль два", result)
    }

    /**
     * FIX 5.8.11-e4-markers-2/11: жёсткий тест реального поведения.
     *
     * DigitGrouper не разбивает "1090031" на "109|00|31" — это
     * одна группа PLAIN. spellPairsAsWords режет по парам слева:
     *   "10" "90" "03" "1"
     *
     * Итог: "капэдэ десять девяносто ноль три один".
     *
     * Если поведение изменится (например, добавят разбиение
     * длинных групп) — тест это поймает, обновим осознанно.
     */
    @Test
    fun spellMimicryKpdSampleNumber() {
        val result = VoiceSpeaker.spellMimicry("KPD1090031")
        assertEquals("капэдэ десять девяносто ноль три один", result)
    }

    @Test
    fun spellMimicryPlainNumber() {
        val result = VoiceSpeaker.spellMimicry("1524")
        assertEquals("пятнадцать двадцать четыре", result)
    }

    @Test
    fun spellMimicryEmptyString() {
        assertEquals("", VoiceSpeaker.spellMimicry(""))
    }

    @Test
    fun spellMimicryBlankString() {
        assertEquals("   ", VoiceSpeaker.spellMimicry("   "))
    }

    @Test
    fun spellMimicryDash() {
        val result = VoiceSpeaker.spellMimicry("—")
        assert(result.isNotEmpty())
    }

    @Test
    fun spellMimicryShortPrefix() {
        val result = VoiceSpeaker.spellMimicry("W12")
        assertEquals("даблю двенадцать", result)
    }
}
