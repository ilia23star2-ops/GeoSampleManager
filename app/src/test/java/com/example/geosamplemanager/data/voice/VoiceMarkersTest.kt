package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-markers-2:
 * Тесты маркеров намерения, отложения по номеру и подтверждения.
 */
class VoiceMarkersTest {

    private val parser = VoiceCommandParser()

    // ================================================================
    // Маркеры намерения — глагол без номера
    // ================================================================

    @Test
    fun bareOtmetReturnsMarkIntent() {
        assertEquals(VoiceCommand.MarkIntent, parser.parse("отметь"))
    }

    @Test
    fun bareOtmetitReturnsMarkIntent() {
        assertEquals(VoiceCommand.MarkIntent, parser.parse("отметить"))
    }

    @Test
    fun bareSniatReturnsClearIntent() {
        assertEquals(VoiceCommand.ClearIntent, parser.parse("снять"))
    }

    @Test
    fun bareUbratReturnsClearIntent() {
        assertEquals(VoiceCommand.ClearIntent, parser.parse("убрать"))
    }

    @Test
    fun bareOtozhitReturnsPostponeIntent() {
        assertEquals(VoiceCommand.PostponeIntent, parser.parse("отложить"))
    }

    @Test
    fun bareOtlozhiReturnsPostponeIntent() {
        assertEquals(VoiceCommand.PostponeIntent, parser.parse("отложи"))
    }

    // ================================================================
    // FIX 5.8.11-e4-markers-2: глагол + номер одной фразой
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
    fun perenestiTretiuReturnsPostponeOrdinalThree() {
        assertEquals(
            VoiceCommand.PostponeOrdinal(3),
            parser.parse("перенести третью")
        )
    }

    @Test
    fun perenesiFourReturnsPostponeOrdinalFour() {
        assertEquals(
            VoiceCommand.PostponeOrdinal(4),
            parser.parse("перенеси 4")
        )
    }

    @Test
    fun sniatVtoruiuReturnsClearOrdinalTwo() {
        assertEquals(VoiceCommand.ClearOrdinal(2), parser.parse("снять вторую"))
    }

    @Test
    fun sniatSevenReturnsClearOrdinalSeven() {
        assertEquals(VoiceCommand.ClearOrdinal(7), parser.parse("снять 7"))
    }

    @Test
    fun otmetEtogoReturnsMarkCurrent() {
        assertEquals(VoiceCommand.MarkCurrent, parser.parse("отметь эту"))
    }

    // ================================================================
    // parseForConfirm — через parseWithState в AWAITING_CONFIRM
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

    @Test
    fun stopInConfirmReturnsStop() {
        assertEquals(
            VoiceCommand.Stop,
            parser.parseWithState("стоп", VoiceState.AWAITING_CONFIRM, VoiceSessionMode.SEARCH)
        )
    }

    @Test
    fun garbageInConfirmReturnsUnknown() {
        assertEquals(
            VoiceCommand.Unknown,
            parser.parseWithState("привет как дела", VoiceState.AWAITING_CONFIRM, VoiceSessionMode.SEARCH)
        )
    }

    // ================================================================
    // parseForNumberIntent — через parseWithState в AWAITING_MARK
    // ================================================================

    @Test
    fun ordinalInMarkStateReturnsMarkOrdinal() {
        assertEquals(
            VoiceCommand.MarkOrdinal(4),
            parser.parseWithState("четвёртая", VoiceState.AWAITING_MARK, VoiceSessionMode.SEARCH)
        )
    }

    @Test
    fun twoNumbersInMarkStateReturnsMarkByNumbers() {
        assertEquals(
            VoiceCommand.MarkByNumbers(listOf(5, 6)),
            parser.parseWithState("пять шесть", VoiceState.AWAITING_MARK, VoiceSessionMode.SEARCH)
        )
    }

    @Test
    fun cancelInMarkStateReturnsUndo() {
        assertEquals(
            VoiceCommand.Undo,
            parser.parseWithState("отмена", VoiceState.AWAITING_MARK, VoiceSessionMode.SEARCH)
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
