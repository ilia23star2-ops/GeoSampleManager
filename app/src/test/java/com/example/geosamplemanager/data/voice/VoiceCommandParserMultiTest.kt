package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-sort-fix:
 * Авто-разделитель для голосового ввода без «и».
 *
 * Если QuerySplitter видит 2+ самостоятельных запроса (числа >= 4 цифр),
 * парсер возвращает Sort с этим списком. Работает и в SEARCH, и в SORT.
 */
class VoiceCommandParserMultiTest {

    private val parser = VoiceCommandParser()

    // ================================================================
    // Явный разделитель
    // ================================================================

    @Test
    fun twoNumbersWithI() {
        val cmd = parser.parse("1366 и 1367")
        assertEquals(VoiceCommand.Sort(listOf("1366", "1367")), cmd)
    }

    @Test
    fun twoNumbersWithComma() {
        val cmd = parser.parse("1366, 1367")
        assertEquals(VoiceCommand.Sort(listOf("1366", "1367")), cmd)
    }

    // ================================================================
    // Авто-разделение (без «и»)
    // ================================================================

    @Test
    fun twoFourDigitNumbersNoSeparator() {
        val cmd = parser.parse("1366 1367")
        assertEquals(VoiceCommand.Sort(listOf("1366", "1367")), cmd)
    }

    @Test
    fun twoSevenDigitNumbersNoSeparator() {
        val cmd = parser.parse("1090031 1090032")
        assertEquals(VoiceCommand.Sort(listOf("1090031", "1090032")), cmd)
    }

    @Test
    fun fourAndSevenDigitNumbersNoSeparator() {
        val cmd = parser.parse("1366 1090031")
        assertEquals(VoiceCommand.Sort(listOf("1366", "1090031")), cmd)
    }

    @Test
    fun threeLongNumbers() {
        val cmd = parser.parse("1366 1367 1368")
        assertEquals(VoiceCommand.Sort(listOf("1366", "1367", "1368")), cmd)
    }

    // ================================================================
    // НЕ должно разбиваться
    // ================================================================

    @Test
    fun shortNumbersStayOne() {
        // «109 00 31» — это KPD1090031, один запрос.
        val cmd = parser.parse("109 00 31")
        // Не Sort — иначе сломается длинный номер.
        assert(cmd !is VoiceCommand.Sort) {
            "«109 00 31» не должно разбиваться: $cmd"
        }
    }

    @Test
    fun twoShortNumbersStayOne() {
        // «15 24» — это 1524, один запрос.
        val cmd = parser.parse("15 24")
        assert(cmd !is VoiceCommand.Sort) {
            "«15 24» не должно разбиваться: $cmd"
        }
    }

    @Test
    fun singleLongNumberStaysSearch() {
        val cmd = parser.parse("1090031")
        assert(cmd !is VoiceCommand.Sort) {
            "Одно число — не Sort: $cmd"
        }
    }
}