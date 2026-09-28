package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-3:
 * Тесты новых правил:
 *  - «следующая X» → Find(X) в любом состоянии;
 *  - вес «X сотни» → X.Y;
 *  - в FOUND_PINNED число → MarkOrdinal (без Search).
 *
 * FIX 5.8.11-sort-fix-5:
 * Команда NextInQueue удалена. «Дальше» в FOUND_PINNED теперь даёт
 * VoiceCommand.Next (унификация). Разбор «идти по очереди или сбросить
 * контекст» — внутри voiceNext().
 */
class VoiceCommandParserPin3Test {

    private val parser = VoiceCommandParser()
    private val delta = 0.0001

    // ================================================================
    // «Следующая X» → Find(X)
    // ================================================================

    @Test
    fun nextWithNumberInListening() {
        val cmd = parser.parseWithState(
            "следующая 1525", VoiceState.LISTENING, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Find("1525"), cmd)
    }

    @Test
    fun nextWithNumberInPinned() {
        val cmd = parser.parseWithState(
            "следующая 1525", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Find("1525"), cmd)
    }

    @Test
    fun nextWithoutNumberStaysNext() {
        val cmd = parser.parseWithState(
            "следующая", VoiceState.LISTENING, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
    }

    @Test
    fun daleeWithNumber() {
        val cmd = parser.parseWithState(
            "далее 1525", VoiceState.LISTENING, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Find("1525"), cmd)
    }

    // ================================================================
    // Вес «X сотни»
    // ================================================================

    @Test
    fun weightTwoSemSot() {
        assertEquals(2.7, parser.parseWeightAnswer("два семьсот")!!, delta)
    }

    @Test
    fun weightThreePyatSot() {
        assertEquals(3.5, parser.parseWeightAnswer("три пятьсот")!!, delta)
    }

    @Test
    fun weightDvaDvesti() {
        assertEquals(2.2, parser.parseWeightAnswer("два двести")!!, delta)
    }

    // ================================================================
    // FOUND_PINNED: любое число → MarkOrdinal
    // ================================================================

    @Test
    fun pinnedNumber4() {
        val cmd = parser.parseWithState(
            "4", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(4), cmd)
    }

    @Test
    fun pinnedNumber1524AlsoMarkOrdinal() {
        // Раньше было Search. Теперь всё → MarkOrdinal.
        val cmd = parser.parseWithState(
            "1524", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(1524), cmd)
    }

    /**
     * FIX 5.8.11-sort-fix-5:
     * «Дальше» в FOUND_PINNED теперь даёт VoiceCommand.Next.
     * NextInQueue удалён. Логика очереди — в voiceNext().
     */
    @Test
    fun pinnedDalsheIsNext() {
        val cmd = parser.parseWithState(
            "дальше", VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Next, cmd)
    }
}