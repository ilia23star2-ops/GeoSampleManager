package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.8.11-e4-tests/1:
 * Тесты разбора веса — parseWeightAnswer и parseWithState.
 *
 * Покрывают закрытые заходы:
 *  - e4a — SetWeight в AWAITING_WEIGHT.
 *  - e4b — фильтр мусора (isCleanWeightPhrase).
 *  - e4g — Pause в AWAITING_WEIGHT.
 */
class VoiceCommandParserWeightsTest {

    private val parser = VoiceCommandParser()

    private val delta = 0.0001

    // ================================================================
    // parseWeightAnswer — чистые формы
    // ================================================================

    @Test
    fun weightSimpleNumber() {
        assertEquals(7.0, parser.parseWeightAnswer("семь")!!, delta)
    }

    @Test
    fun weightTwoAndSix() {
        assertEquals(2.6, parser.parseWeightAnswer("два и шесть")!!, delta)
    }

    @Test
    fun weightPoltora() {
        assertEquals(1.5, parser.parseWeightAnswer("полтора")!!, delta)
    }

    @Test
    fun weightDecimalDot() {
        assertEquals(2.6, parser.parseWeightAnswer("2.6")!!, delta)
    }

    @Test
    fun weightDecimalComma() {
        assertEquals(2.6, parser.parseWeightAnswer("2,6")!!, delta)
    }

    @Test
    fun weightTwoCelyhShestDesyatyh() {
        assertEquals(2.6, parser.parseWeightAnswer("две целых шесть десятых")!!, delta)
    }

    @Test
    fun weightSixSotyh() {
        assertEquals(0.06, parser.parseWeightAnswer("шесть сотых")!!, delta)
    }

    @Test
    fun weightHalf() {
        assertEquals(2.5, parser.parseWeightAnswer("два с половиной")!!, delta)
    }

    // ================================================================
    // parseWeightAnswer — мусор (e4b)
    // ================================================================

    @Test
    fun weightGarbageReturnsNull() {
        assertNull(parser.parseWeightAnswer("семь утра было холодно"))
    }

    @Test
    fun weightGarbageProbaOdnaReturnsNull() {
        assertNull(parser.parseWeightAnswer("семь проба одна"))
    }

    @Test
    fun weightEmptyReturnsNull() {
        assertNull(parser.parseWeightAnswer(""))
    }

    // ================================================================
    // parseWithState — AWAITING_WEIGHT (e4g, e4a)
    // ================================================================

    @Test
    fun stateWeightNumber() {
        val cmd = parser.parseWithState(
            "семь", VoiceState.AWAITING_WEIGHT, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.SetWeight(7.0), cmd)
    }

    @Test
    fun stateWeightStop() {
        val cmd = parser.parseWithState(
            "стоп", VoiceState.AWAITING_WEIGHT, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Stop, cmd)
    }

    @Test
    fun stateWeightHvatit() {
        val cmd = parser.parseWithState(
            "хватит", VoiceState.AWAITING_WEIGHT, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Stop, cmd)
    }

    @Test
    fun stateWeightUndo() {
        val cmd = parser.parseWithState(
            "отмена", VoiceState.AWAITING_WEIGHT, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Undo, cmd)
    }

    @Test
    fun stateWeightPause() {
        val cmd = parser.parseWithState(
            "пауза", VoiceState.AWAITING_WEIGHT, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Pause, cmd)
    }

    @Test
    fun stateWeightGarbageUnknown() {
        val cmd = parser.parseWithState(
            "семь утра было холодно",
            VoiceState.AWAITING_WEIGHT,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Unknown, cmd)
    }
}