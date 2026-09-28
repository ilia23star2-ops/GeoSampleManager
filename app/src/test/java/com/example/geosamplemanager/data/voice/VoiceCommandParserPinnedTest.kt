package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-1 / pin-3:
 * Тесты состояния FOUND_PINNED (скважина закреплена).
 *
 * FIX 5.8.11-e4-pin-3: изменено поведение —
 * любое голое число (не только 1..99) → MarkOrdinal.
 * Раньше >99 уходило в Search. Теперь — нет.
 */
class VoiceCommandParserPinnedTest {

    private val parser = VoiceCommandParser()

    @Test
    fun pinnedDigitIsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "4", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(4), cmd)
    }

    @Test
    fun pinnedWordIsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "семь", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(7), cmd)
    }

    @Test
    fun pinnedOrdinalWordIsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "первая", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(1), cmd)
    }

    @Test
    fun pinnedNextStaysNext() {
        val cmd = parser.parseWithState(
            "следующая", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
    }

    @Test
    fun pinnedCancelIsUndo() {
        val cmd = parser.parseWithState(
            "отмена", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Undo, cmd)
    }

    @Test
    fun pinnedFindWithNumber() {
        val cmd = parser.parseWithState(
            "найди 1524", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Find("1524"), cmd)
    }

    @Test
    fun pinnedStopIsStop() {
        val cmd = parser.parseWithState(
            "стоп", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Stop, cmd)
    }

    /**
     * FIX 5.8.11-e4-pin-3:
     * В FOUND_PINNED любое число → MarkOrdinal, а не Search.
     * Выход из pin — только «следующая».
     */
    @Test
    fun pinnedBigNumberIsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "1524", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(1524), cmd)
    }
}