package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.8.9d-3c2a: тесты голосового парсера веса.
 */
class WeightVoiceParserTest {

    private val parser = VoiceCommandParser()

    // ================================================================
    // Простые целые
    // ================================================================

    @Test
    fun plainDigit_returnsValue() {
        assertEquals(2.0, parser.parseWeightAnswer("2")!!, 0.0001)
    }

    @Test
    fun plainWord_returnsValue() {
        assertEquals(2.0, parser.parseWeightAnswer("два")!!, 0.0001)
    }

    @Test
    fun decimal_returnsValue() {
        assertEquals(2.6, parser.parseWeightAnswer("2.6")!!, 0.0001)
    }

    @Test
    fun decimalWithComma_returnsValue() {
        assertEquals(2.6, parser.parseWeightAnswer("2,6")!!, 0.0001)
    }

    // ================================================================
    // «И» как десятичный разделитель
    // ================================================================

    @Test
    fun twoAndSix_returnsValue() {
        assertEquals(2.6, parser.parseWeightAnswer("два и шесть")!!, 0.0001)
    }

    // ================================================================
    // Явные дроби: «X целых Y десятых / сотых»
    // ================================================================

    @Test
    fun twoWholeSixTenths_returnsValue() {
        assertEquals(2.6, parser.parseWeightAnswer("две целых шесть десятых")!!, 0.0001)
    }

    @Test
    fun twoWholeSixHundredths_returnsValue() {
        assertEquals(2.06, parser.parseWeightAnswer("два целых шесть сотых")!!, 0.0001)
    }

    @Test
    fun zeroWholeSixTenths_returnsValue() {
        assertEquals(0.6, parser.parseWeightAnswer("ноль целых шесть десятых")!!, 0.0001)
    }

    // ================================================================
    // Дроби без целой части
    // ================================================================

    @Test
    fun sixTenths_returnsValue() {
        assertEquals(0.6, parser.parseWeightAnswer("шесть десятых")!!, 0.0001)
    }

    @Test
    fun sixHundredths_returnsValue() {
        assertEquals(0.06, parser.parseWeightAnswer("шесть сотых")!!, 0.0001)
    }

    // ================================================================
    // Суффиксы
    // ================================================================

    @Test
    fun twoAndHalf_returnsValue() {
        assertEquals(2.5, parser.parseWeightAnswer("два с половиной")!!, 0.0001)
    }

    @Test
    fun twoAndQuarter_returnsValue() {
        assertEquals(2.25, parser.parseWeightAnswer("два с четвертью")!!, 0.0001)
    }

    @Test
    fun poltora_returnsValue() {
        assertEquals(1.5, parser.parseWeightAnswer("полтора")!!, 0.0001)
    }

    @Test
    fun polkilo_returnsValue() {
        assertEquals(0.5, parser.parseWeightAnswer("полкило")!!, 0.0001)
    }

    // ================================================================
    // Единицы измерения отрезаются
    // ================================================================

    @Test
    fun twoKilogram_returnsValue() {
        assertEquals(2.0, parser.parseWeightAnswer("два килограмм")!!, 0.0001)
    }

    @Test
    fun twoKg_returnsValue() {
        assertEquals(2.0, parser.parseWeightAnswer("два кг")!!, 0.0001)
    }

    @Test
    fun twoKilo_returnsValue() {
        assertEquals(2.0, parser.parseWeightAnswer("два кило")!!, 0.0001)
    }

    // ================================================================
    // Пустое и мусор
    // ================================================================

    @Test
    fun emptyInput_returnsNull() {
        assertNull(parser.parseWeightAnswer(""))
    }

    @Test
    fun whitespaceInput_returnsNull() {
        assertNull(parser.parseWeightAnswer("   "))
    }
}
