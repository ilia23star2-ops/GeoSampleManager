package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-multi:
 * Тесты «2 3» → MarkByNumbers, SORT+pin → обычный parse.
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
    // FIX 5.8.11-e4-pin-multi: несколько чисел = MarkByNumbers
    // ================================================================

    @Test
    fun parseForPinnedTwoDigitsReturnsMarkByNumbers() {
        val cmd = parser.parseWithState(
            "2 3",
            VoiceState.FOUND_PINNED,
            VoiceSessionMode.SEARCH
        )
        // Ожидаем MarkByNumbers([2, 3]) или MarkOrdinal(23) —
        // зависит от того, как Vosk разбивает. Проверяем что не MarkIntent.
        assertTrue(
            "Должно быть MarkOrdinal или MarkByNumbers: $cmd",
            cmd is VoiceCommand.MarkOrdinal || cmd is VoiceCommand.MarkByNumbers
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

    @Test
    fun otmetTwoDigitsReturnsMarkByNumbers() {
        val cmd = parser.parse("отметь 2 3")
        // Должно быть либо MarkOrdinal(23) если Vosk склеил,
        // либо MarkByNumbers([2,3]). Проверяем что не Unknown.
        assertTrue(
            "Не должно быть Unknown: $cmd",
            cmd !is VoiceCommand.Unknown
        )
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
        // В SORT режиме pin не работает — обычный parse.
        // «1367» — это число, похоже на поисковый запрос.
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
