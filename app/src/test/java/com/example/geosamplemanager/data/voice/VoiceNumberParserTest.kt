package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты парсера числа. Примеры из §17 VOICE.md.
 */
class VoiceNumberParserTest {

    private val parser = VoiceNumberParser()

    @Test
    fun nulls_twoZeroes_00() {
        assertEquals("00", parser.parse("два нуля").primary)
    }

    @Test
    fun nulls_hundredTwoZeroesThree_100003() {
        assertEquals("100003", parser.parse("сто два нуля три").primary)
    }

    @Test
    fun k_twoThousandFiveHundred_2500() {
        assertEquals("2500", parser.parse("две тысячи пятьсот").primary)
    }

    @Test
    fun k_hundredThousandTwenty_100020() {
        assertEquals("100020", parser.parse("сто тысяч двадцать").primary)
    }

    @Test
    fun k_thousandFiveSixtyTwo_1562() {
        assertEquals("1562", parser.parse("тысяча пятьсот шестьдесят два").primary)
    }

    @Test
    fun r_hundredTwentyFour_124() {
        assertEquals("124", parser.parse("сто двадцать четыре").primary)
    }

    @Test
    fun r_zeroZeroThree_003() {
        assertEquals("003", parser.parse("ноль ноль три").primary)
    }

    @Test
    fun r_thousandFiveHundredHundredSixtyTwo_1500162() {
        assertEquals("1500162", parser.parse("тысяча пятьсот сто шестьдесят два").primary)
    }

    @Test
    fun r_fiftyOne_51() {
        assertEquals("51", parser.parse("пятьдесят один").primary)
    }

    @Test
    fun sep_fiftyAndOne_hasCandidate_50_1() {
        val r = parser.parse("пятьдесят и один")
        assertTrue(
            "Ожидался кандидат «50|1», получено: ${r.candidates}",
            r.candidates.contains("50|1")
        )
    }

    @Test
    fun stop_hundredUmmTwenty_120() {
        assertEquals("120", parser.parse("сто эээ двадцать").primary)
    }

    @Test
    fun normalize_lowercaseAndYoToYe() {
        assertEquals("124", parser.parse("СТО ДВАДЦАТЬ ЧЕТЫРЕ").primary)
        assertEquals("4", parser.parse("четвёрка").primary)
    }
}