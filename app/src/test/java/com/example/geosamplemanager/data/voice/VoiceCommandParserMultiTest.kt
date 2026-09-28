package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-sort-fix-3:
 * Авто-разделитель без «и» отключён. Vosk не даёт маркеров границ —
 * «13 60 6 109 00 31» фонетически не отличить от «1366061 090031».
 * Все попытки угадать сплит по длине групп ненадёжны.
 *
 * Явный разделитель («и», запятая) — работает: Sort.
 * Без разделителя — Search (одна строка уходит в поиск).
 */
class VoiceCommandParserMultiTest {

    private val parser = VoiceCommandParser()

    // ================================================================
    // Явный разделитель — Sort
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
    // Без разделителя — Search (авто-split отключён)
    // ================================================================

    @Test
    fun twoFourDigitNumbersNoSeparatorIsSearch() {
        val cmd = parser.parse("1366 1367")
        assertEquals(VoiceCommand.Search("1366 1367"), cmd)
    }

    @Test
    fun twoSevenDigitNumbersNoSeparatorIsSearch() {
        val cmd = parser.parse("1090031 1090032")
        assertEquals(VoiceCommand.Search("1090031 1090032"), cmd)
    }

    @Test
    fun fourAndSevenDigitNumbersNoSeparatorIsSearch() {
        val cmd = parser.parse("1366 1090031")
        assertEquals(VoiceCommand.Search("1366 1090031"), cmd)
    }

    @Test
    fun threeLongNumbersIsSearch() {
        val cmd = parser.parse("1366 1367 1368")
        assertEquals(VoiceCommand.Search("1366 1367 1368"), cmd)
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