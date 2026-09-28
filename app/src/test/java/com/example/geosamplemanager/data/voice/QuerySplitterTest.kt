package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-multi-query:
 * Тесты авто-разделения запросов.
 *
 * Правило: 2+ Number с длиной >= 4 цифр → два запроса.
 * Короткие числа (1–3 цифры) остаются одним запросом.
 */
class QuerySplitterTest {

    private val tokenizer = QueryTokenizer()

    private fun split(text: String): List<List<QueryToken>> =
        QuerySplitter.splitIntoRequests(tokenizer.tokenize(text))

    // ================================================================
    // Явный разделитель
    // ================================================================

    @Test
    fun explicitAndSeparator() {
        val result = split("1366 и 1367")
        assertEquals(2, result.size)
    }

    @Test
    fun explicitCommaSeparator() {
        val result = split("1366, 1367")
        assertEquals(2, result.size)
    }

    // ================================================================
    // Авто-разделение длинных чисел
    // ================================================================

    @Test
    fun twoFourDigitNumbersAutoSplit() {
        val result = split("1366 1367")
        assertEquals(2, result.size)
    }

    @Test
    fun twoSevenDigitNumbersAutoSplit() {
        val result = split("1090031 1090032")
        assertEquals(2, result.size)
    }

    @Test
    fun fourAndSevenDigitNumbersAutoSplit() {
        val result = split("1366 1090031")
        assertEquals(2, result.size)
    }

    @Test
    fun threeLongNumbersAutoSplit() {
        val result = split("1366 1367 1368")
        assertEquals(3, result.size)
    }

    // ================================================================
    // Префиксы
    // ================================================================

    @Test
    fun twoPrefixNumbersAutoSplit() {
        val result = split("KPD1090031 NV1366")
        assertEquals(2, result.size)
        // Первый запрос содержит Prefix(KPD) и Number(1090031)
        assertEquals(2, result[0].size)
        // Второй — Prefix(NV) и Number(1366)
        assertEquals(2, result[1].size)
    }

    // ================================================================
    // Короткие числа — один запрос
    // ================================================================

    @Test
    fun threeShortNumbersStaysOne() {
        // «109 00 31» — это KPD1090031, один запрос.
        val result = split("109 00 31")
        assertEquals(1, result.size)
    }

    @Test
    fun twoShortNumbersStaysOne() {
        // «15 24» — это 1524, один запрос.
        val result = split("15 24")
        assertEquals(1, result.size)
    }

    @Test
    fun twoTwoDigitNumbersStaysOne() {
        // «12 13» — это 1213, один запрос.
        val result = split("12 13")
        assertEquals(1, result.size)
    }

    // ================================================================
    // Пустое / одно число
    // ================================================================

    @Test
    fun emptyInput() {
        val result = split("")
        assertEquals(0, result.size)
    }

    @Test
    fun singleNumber() {
        val result = split("1366")
        assertEquals(1, result.size)
    }

    // ================================================================
    // Смешанное
    // ================================================================

    @Test
    fun shortAndLongSplit() {
        // «13 1366» — короткое + длинное. По правилу нужно 2+ длинных.
        // «13» — 2 цифры, «1366» — 4. Длинное только одно → один запрос.
        val result = split("13 1366")
        assertEquals(1, result.size)
    }
}