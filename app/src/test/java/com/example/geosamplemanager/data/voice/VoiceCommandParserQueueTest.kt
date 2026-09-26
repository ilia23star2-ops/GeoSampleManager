package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-2 / sort-fix-5:
 * Тесты синонимов Next.
 *
 * FIX 5.8.11-sort-fix-5:
 * Команда NextInQueue удалена. Все синонимы («дальше», «следующая»,
 * «далее») дают VoiceCommand.Next в любом состоянии. Разбор «идти
 * по очереди или сбросить контекст» — внутри voiceNext().
 */
class VoiceCommandParserQueueTest {

    private val parser = VoiceCommandParser()

    @Test
    fun pinnedDalsheIsNext() {
        val cmd = parser.parseWithState(
            "дальше", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
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

    /**
     * FIX 5.8.11-sort-fix-5:
     * «Дальше» теперь универсальная команда — в LISTENING тоже Next.
     * Раньше давало Unknown (не было в nextVerbWords). Логика voiceNext()
     * сама решит: сбросить контекст или идти по очереди.
     */
    @Test
    fun listenDalsheIsNext() {
        val cmd = parser.parseWithState(
            "дальше", VoiceState.LISTENING, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
    }

    @Test
    fun listenSleduyushchayaIsNext() {
        val cmd = parser.parseWithState(
            "следующая", VoiceState.LISTENING, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
    }
}