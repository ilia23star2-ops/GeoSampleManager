package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-speak-1:
 * Тесты мимикрии и разбиения длинных номеров «как человек».
 */
class VoiceSpeakerTest {

    // ================================================================
    // spellOut(groups)
    // ================================================================

    @Test
    fun prefixKpdOneWord() {
        val groups = listOf(
            DigitGroup("KPD", GroupKind.PREFIX),
            DigitGroup("109", GroupKind.PLAIN),
            DigitGroup("00", GroupKind.LEADING_ZERO),
            DigitGroup("31", GroupKind.PLAIN)
        )
        assertEquals(
            "капэдэ сто девять ноль ноль тридцать один",
            VoiceSpeaker.spellOut(groups)
        )
    }

    @Test
    fun singleDigitSpelledAsWord() {
        val groups = listOf(DigitGroup("7", GroupKind.SINGLE))
        assertEquals("семь", VoiceSpeaker.spellOut(groups))
    }

    @Test
    fun wLetterAsDablyu() {
        val groups = listOf(DigitGroup("W", GroupKind.PREFIX))
        assertEquals("даблю", VoiceSpeaker.spellOut(groups))
    }

    @Test
    fun emptyGroups() {
        assertEquals("", VoiceSpeaker.spellOut(emptyList()))
    }

    // ================================================================
    // FIX 5.8.11-e4-speak-1: splitLikeHuman
    // ================================================================

    @Test
    fun splitLikeHumanFourDigits() {
        assertEquals(listOf("13", "66"), VoiceSpeaker.splitLikeHuman("1366"))
    }

    @Test
    fun splitLikeHumanSixDigits() {
        assertEquals(
            listOf("13", "66", "01"),
            VoiceSpeaker.splitLikeHuman("136601")
        )
    }

    @Test
    fun splitLikeHumanSevenDigits() {
        assertEquals(
            listOf("109", "00", "31"),
            VoiceSpeaker.splitLikeHuman("1090031")
        )
    }

    @Test
    fun splitLikeHumanNineDigits() {
        assertEquals(
            listOf("109", "00", "31", "01"),
            VoiceSpeaker.splitLikeHuman("109003101")
        )
    }

    @Test
    fun splitLikeHumanShortNumber() {
        assertEquals(listOf("7"), VoiceSpeaker.splitLikeHuman("7"))
        assertEquals(listOf("152"), VoiceSpeaker.splitLikeHuman("152"))
    }

    @Test
    fun splitLikeHumanEmpty() {
        assertEquals(emptyList<String>(), VoiceSpeaker.splitLikeHuman(""))
    }

    // ================================================================
    // spellMimicry(text)
    // ================================================================

    @Test
    fun spellMimicryNvFourDigits() {
        assertEquals(
            "энвэ тринадцать шестьдесят шесть",
            VoiceSpeaker.spellMimicry("NV1366")
        )
    }

    @Test
    fun spellMimicryNvSixDigits() {
        assertEquals(
            "энвэ тринадцать шестьдесят шесть ноль один",
            VoiceSpeaker.spellMimicry("NV136601")
        )
    }

    @Test
    fun spellMimicryKpdSevenDigits() {
        assertEquals(
            "капэдэ сто девять ноль ноль тридцать один",
            VoiceSpeaker.spellMimicry("KPD1090031")
        )
    }

    @Test
    fun spellMimicryPlainNineDigits() {
        assertEquals(
            "сто девять ноль ноль тридцать один ноль один",
            VoiceSpeaker.spellMimicry("109003101")
        )
    }

    @Test
    fun spellMimicryPlainFourDigits() {
        assertEquals(
            "пятнадцать двадцать четыре",
            VoiceSpeaker.spellMimicry("1524")
        )
    }

    @Test
    fun spellMimicryShortPrefix() {
        assertEquals("даблю двенадцать", VoiceSpeaker.spellMimicry("W12"))
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
    fun spellMimicryOnlyPrefix() {
        // Только буквы, без цифр — spellLetters.
        assertEquals("капэдэ", VoiceSpeaker.spellMimicry("KPD"))
    }
}
