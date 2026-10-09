package com.example.geosamplemanager.ui.screens.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.10-stat-admin-v2-time-filter:
 * Тесты парсера фильтра времени. Имена тестов — латиница.
 */
class TimeFilterParserTest {

    // ============================================================
    // Empty
    // ============================================================

    @Test
    fun empty_isEmpty() {
        assertTrue(TimeFilterParser.parse("") is TimeFilterParseResult.Empty)
    }

    @Test
    fun whitespace_isEmpty() {
        assertTrue(TimeFilterParser.parse("   ") is TimeFilterParseResult.Empty)
    }

    // ============================================================
    // Point
    // ============================================================

    @Test
    fun hour_only_isPoint() {
        val r = TimeFilterParser.parse("12")
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Point(720)),
            r
        )
    }

    @Test
    fun hour_with_minute_isPoint() {
        val r = TimeFilterParser.parse("12:30")
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Point(750)),
            r
        )
    }

    @Test
    fun midnight_isPoint0() {
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Point(0)),
            TimeFilterParser.parse("0")
        )
    }

    @Test
    fun lastMinute_isPoint1439() {
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Point(1439)),
            TimeFilterParser.parse("23:59")
        )
    }

    @Test
    fun leadingZeroHour_isPoint() {
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Point(480)),
            TimeFilterParser.parse("08")
        )
    }

    // ============================================================
    // Range
    // ============================================================

    @Test
    fun rangeHours_isRange() {
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Range(720, 780)),
            TimeFilterParser.parse("12-13")
        )
    }

    @Test
    fun rangeWithMinutes_isRange() {
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Range(720, 810)),
            TimeFilterParser.parse("12:00-13:30")
        )
    }

    @Test
    fun rangeWithSpaces_isRange() {
        assertEquals(
            TimeFilterParseResult.Ok(TimeFilter.Range(720, 780)),
            TimeFilterParser.parse("12 - 13")
        )
    }

    // ============================================================
    // Invalid
    // ============================================================

    @Test
    fun hourAbove23_invalid() {
        assertTrue(TimeFilterParser.parse("25") is TimeFilterParseResult.Invalid)
    }

    @Test
    fun minuteAbove59_invalid() {
        assertTrue(TimeFilterParser.parse("12:60") is TimeFilterParseResult.Invalid)
    }

    @Test
    fun garbage_invalid() {
        assertTrue(TimeFilterParser.parse("abc") is TimeFilterParseResult.Invalid)
    }

    @Test
    fun endBeforeStart_invalid() {
        assertTrue(TimeFilterParser.parse("13-12") is TimeFilterParseResult.Invalid)
    }

    @Test
    fun endEqualsStart_invalid() {
        assertTrue(TimeFilterParser.parse("12-12") is TimeFilterParseResult.Invalid)
    }

    @Test
    fun threeParts_invalid() {
        assertTrue(TimeFilterParser.parse("12-13-14") is TimeFilterParseResult.Invalid)
    }

    @Test
    fun rangeWithGarbageSecond_invalid() {
        assertTrue(TimeFilterParser.parse("12-abc") is TimeFilterParseResult.Invalid)
    }

    @Test
    fun dotSeparator_invalid() {
        assertTrue(TimeFilterParser.parse("12.30") is TimeFilterParseResult.Invalid)
    }
}