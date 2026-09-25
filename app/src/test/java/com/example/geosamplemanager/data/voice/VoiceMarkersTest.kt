package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-multi:
 * Тесты «два три» → MarkByNumbers, SORT+pin → обычный parse.
 *
 * FIX 5.8.11-e4-pin-multi/6:
 *  - тесты на перечисление переписаны со слов, не с цифр через пробел
 *    (Vosk так не выдаёт);
 *  - проверяем «не Unknown» — конкретный результат (MarkOrdinal(23)
 *    или MarkByNumbers([2,3])) зависит от VoiceNumberParser.
 */
class VoiceMarkersTest {

    private val parser = VoiceCommandParser()

    // ================================================================
    // Маркеры намерения
    // ================================================================

    @Test
    fun bareOtmetReturnsMarkIntent() {
        assertEquals(VoiceCommand.MarkIntent, parser.parse("отметь"))
    }

    @Test
    fun bareSniatReturnsClearIntent() {
        assertEquals(VoiceCommand.ClearIntent, parser.parse("снять"))
    }

    @Test
    fun bareOtozhitReturnsPostponeIntent() {
        assertEquals(VoiceCommand.PostponeIntent, parser.parse("отложить"))
    }

    // ================================================================
    // Глагол + номер
    // ================================================================

    @Test
    fun otmetPiatuiuReturnsMarkOrdinalFive() {
        assertEquals(VoiceCommand.MarkOrdinal(5), parser.parse("отметь пятую"))
    }

    @Test
    fun otmetSevenReturnsMarkOrdinalSeven() {
        assertEquals(VoiceCommand.MarkOrdinal(7), parser.parse("отметь 7"))
    }

    @Test
    fun otozhitVtoruiuReturnsPostponeOrdinalTwo() {
        assertEquals(
            VoiceCommand.PostponeOrdinal(2),
            parser.parse("отложить вторую")
        )
    }

    @Test
    fun otlozhiSevenReturnsPostponeOrdinalSeven() {
        assertEquals(
            VoiceCommand.PostponeOrdinal(7),
            parser.parse("отложи 7")
        )
    }

    @Test
    fun otmetEtogoReturnsMarkCurrent() {
        assertEquals(VoiceCommand.MarkCurrent, parser.parse("отметь эту"))
    }

    // ================================================================
    // FIX 5.8.11-e4-pin-multi: перечисление (тесты на словах)
    // ================================================================

    /**
     * «два три» — либо MarkOrdinal(23), либо MarkByNumbers([2, 3]).
     * Точный результат зависит от VoiceNumberParser (даёт ли «|»).
     * Главное — не Unknown.
     */
    @Test
    fun parseForPinnedTwoWordsReturnsMarkSomething() {
        val cmd = parser.parseWithState(
            "два три",
            VoiceState.FOUND_PINNED,
            VoiceSessionMode.SEARCH
        )
        assertTrue(
            "Должно быть MarkOrdinal или MarkByNumbers: $cmd",
            cmd is VoiceCommand.MarkOrdinal || cmd is VoiceCommand.MarkByNumbers
        )
    }

    @Test
    fun otmetTwoWordsReturnsMarkSomething() {
        val cmd = parser.parse("отметь два три")
        assertTrue(
            "Не должно быть Unknown: $cmd",
            cmd !is VoiceCommand.Unknown
        )
    }

    @Test
    fun parseForPinnedNumber23ReturnsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "23",
            VoiceState.FOUND_PINNED,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(23), cmd)
    }

    /**
     * FIX 5.8.11-e4-pin-multi/6:
     * Мусор в pin → Unknown. Проверка isOnlyNumbersPhrase.
     */
    @Test
    fun parseForPinnedGarbageReturnsUnknown() {
        val cmd = parser.parseWithState(
            "семь утра было холодно",
            VoiceState.FOUND_PINNED,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Unknown, cmd)
    }

    // ================================================================
    // FIX 5.8.11-e4-pin-multi: SORT + pin → обычный parse
    // ================================================================

    @Test
    fun sortPinnedNumberGoesToSearchNotMark() {
        val cmd = parser.parseWithState(
            "1367",
            VoiceState.FOUND_PINNED,
            VoiceSessionMode.SORT
        )
        assertTrue(
            "SORT+pin: не должно быть MarkOrdinal: $cmd",
            cmd !is VoiceCommand.MarkOrdinal &&
                    cmd !is VoiceCommand.MarkByNumbers
        )
    }

    // ================================================================
    // parseForConfirm
    // ================================================================

    @Test
    fun confirmWordReturnsConfirm() {
        assertEquals(
            VoiceCommand.Confirm,
            parser.parseWithState("подтверждаю", VoiceState.AWAITING_CONFIRM, VoiceSessionMode.SEARCH)
        )
    }

    @Test
    fun declineWordReturnsDecline() {
        assertEquals(
            VoiceCommand.Decline,
            parser.parseWithState("отменяю", VoiceState.AWAITING_CONFIRM, VoiceSessionMode.SEARCH)
        )
    }

    // ================================================================
    // VoiceState.hasWaitTimeout
    // ================================================================

    @Test
    fun hasWaitTimeoutForMarkStates() {
        assertTrue(VoiceState.AWAITING_MARK.hasWaitTimeout)
        assertTrue(VoiceState.AWAITING_CLEAR.hasWaitTimeout)
        assertTrue(VoiceState.AWAITING_POSTPONE.hasWaitTimeout)
        assertTrue(VoiceState.AWAITING_CONFIRM.hasWaitTimeout)
        assertTrue(VoiceState.AWAITING_WEIGHT.hasWaitTimeout)
        assertTrue(VoiceState.AWAITING_CHOICE.hasWaitTimeout)
    }

    @Test
    fun noWaitTimeoutForListeningStates() {
        assertTrue(!VoiceState.AWAITING_CONTINUE.hasWaitTimeout)
        assertTrue(!VoiceState.LISTENING.hasWaitTimeout)
        assertTrue(!VoiceState.FOUND_PINNED.hasWaitTimeout)
        assertTrue(!VoiceState.PAUSED.hasWaitTimeout)
        assertTrue(!VoiceState.IDLE.hasWaitTimeout)
    }
}
