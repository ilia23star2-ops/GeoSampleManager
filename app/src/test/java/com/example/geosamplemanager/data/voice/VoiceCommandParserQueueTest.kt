package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-2:
 * Тесты «дальше» в FOUND_PINNED.
 *
 * Проверяем:
 *  - «дальше» → NextInQueue;
 *  - «следующая» / «далее» → Next (выход из очереди);
 *  - числа всё ещё MarkOrdinal;
 *  - «отмена» → Undo.
 */
class VoiceCommandParserQueueTest {

    private val parser = VoiceCommandParser()

    @Test
    fun pinnedDalsheIsNextInQueue() {
        val cmd = parser.parseWithState(
            "дальше", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.NextInQueue, cmd)
    }

    @Test
    fun pinnedSleduyushchayaIsNext() {
        val cmd = parser.parseWithState(
            "следующая", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
    }

    @Test
    fun pinnedDaleeIsNext() {
        val cmd = parser.parseWithState(
            "далее", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
    }

    @Test
    fun pinnedNumberStillMarkOrdinal() {
        val cmd = parser.parseWithState(
            "4", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(4), cmd)
    }

    @Test
    fun pinnedCancelIsUndo() {
        val cmd = parser.parseWithState(
            "отмена", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Undo, cmd)
    }

    @Test
    fun listenDalsheIsUnknown() {
        val cmd = parser.parseWithState(
            "дальше", VoiceState.LISTENING, VoiceSessionMode.SEARCH
        )
        // В LISTENING «дальше» — не команда.
        assertEquals(VoiceCommand.Unknown, cmd)
    }
}