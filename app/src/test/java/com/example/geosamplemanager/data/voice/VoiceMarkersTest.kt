package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-multi/8:
 *  - «два три» → MarkByNumbers([2, 3]).
 *  - «двадцать три» → MarkOrdinal(23).
 *  - мусор в pin → Unknown.
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
    fun otmetEtogoReturnsMarkCurrent() {
        assertEquals(VoiceCommand.MarkCurrent, parser.parse("отметь эту"))
    }

    // ================================================================
    // FIX 5.8.11-e4-pin-multi/8: перечисление
    // ================================================================

    @Test
    fun parseForPinnedTwoWordsReturnsMarkByNumbers() {
        val cmd = parser.parseWithState(
            "два три",
            VoiceState.FOUND_PINNED,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkByNumbers(listOf(2, 3)), cmd)
    }

    @Test
    fun otmetTwoWordsReturnsMarkByNumbers() {
        val cmd = parser.parse("отметь два три")
        assertEquals(VoiceCommand.MarkByNumbers(listOf(2, 3)), cmd)
    }

    @Test
    fun otmetFourWordsReturnsMarkByNumbers() {
        val cmd = parser.parse("отметь один два три четыре")
        assertEquals(VoiceCommand.MarkByNumbers(listOf(1, 2, 3, 4)), cmd)
    }

    @Test
    fun parseForPinnedNumber23ReturnsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "двадцать три",
            VoiceState.FOUND_PINNED,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(23), cmd)
    }

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
