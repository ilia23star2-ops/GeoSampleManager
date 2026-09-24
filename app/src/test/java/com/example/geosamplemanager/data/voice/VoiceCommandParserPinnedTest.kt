package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-1:
 * Тесты состояния FOUND_PINNED (скважина закреплена).
 *
 * Проверяем:
 *  - голое число (цифрой) → MarkOrdinal;
 *  - голое слово-числительное → MarkOrdinal;
 *  - «следующая» → Next;
 *  - «отмена» → Undo;
 *  - «найди X» → Find;
 *  - обычные команды в этом состоянии — без изменений.
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

    @Test
    fun pinnedBigNumberIsSearch() {
        val cmd = parser.parseWithState(
            "1524", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        // 1524 не влезает в 1..99 → обычный Search.
        assertEquals(VoiceCommand.Search("1524"), cmd)
    }
}