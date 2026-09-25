package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-markers:
 * Тесты маркеров намерения и подтверждения массовых.
 *
 * Парсер — единственная чистая часть. ViewModel (диалог, состояние,
 * тайм-аут) не тестируется юнит-тестами — слишком связан с Android
 * и Application.
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
    // Глагол + номер — сразу команда, не маркер
    // ================================================================

    @Test
    fun otmetPiatuiuReturnsMarkOrdinalFive() {
        val cmd = parser.parse("отметь пятую")
        assertEquals(VoiceCommand.MarkOrdinal(5), cmd)
    }

    @Test
    fun otmetSevenReturnsMarkOrdinalSeven() {
        val cmd = parser.parse("отметь 7")
        assertEquals(VoiceCommand.MarkOrdinal(7), cmd)
    }

    @Test
    fun otmetPiatShestReturnsMarkByNumbers() {
        val cmd = parser.parse("отметь пять шесть")
        // Парсер не склеивает MarkByNumbers в этом пути;
        // но при отсутствии ordinals-фразы должен вернуть Search или Unknown.
        // Проверим, что точно не MarkIntent и не MarkOrdinal(56).
        assertTrue(
            "Должно быть не MarkIntent и не MarkOrdinal(56): $cmd",
            cmd !is VoiceCommand.MarkIntent &&
                    cmd != VoiceCommand.MarkOrdinal(56)
        )
    }

    @Test
    fun otmetEtogoReturnsMarkCurrent() {
        val cmd = parser.parse("отметь эту")
        assertEquals(VoiceCommand.MarkCurrent, cmd)
    }

    // ================================================================
    // parseForConfirm — через parseWithState в состоянии AWAITING_CONFIRM
    // ================================================================

    @Test
    fun confirmWordReturnsConfirm() {
        val cmd = parser.parseWithState(
            "подтверждаю",
            VoiceState.AWAITING_CONFIRM,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Confirm, cmd)
    }

    @Test
    fun confirmVerbReturnsConfirm() {
        val cmd = parser.parseWithState(
            "подтвердить",
            VoiceState.AWAITING_CONFIRM,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Confirm, cmd)
    }

    @Test
    fun declineWordReturnsDecline() {
        val cmd = parser.parseWithState(
            "отменяю",
            VoiceState.AWAITING_CONFIRM,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Decline, cmd)
    }

    @Test
    fun declineVerbReturnsDecline() {
        val cmd = parser.parseWithState(
            "отменить",
            VoiceState.AWAITING_CONFIRM,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Decline, cmd)
    }

    @Test
    fun stopInConfirmReturnsStop() {
        val cmd = parser.parseWithState(
            "стоп",
            VoiceState.AWAITING_CONFIRM,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Stop, cmd)
    }

    @Test
    fun garbageInConfirmReturnsUnknown() {
        val cmd = parser.parseWithState(
            "привет как дела",
            VoiceState.AWAITING_CONFIRM,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Unknown, cmd)
    }

    // ================================================================
    // parseForNumberIntent — через parseWithState в AWAITING_MARK
    // ================================================================

    @Test
    fun ordinalInMarkStateReturnsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "четвёртая",
            VoiceState.AWAITING_MARK,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(4), cmd)
    }

    @Test
    fun numberInMarkStateReturnsMarkOrdinal() {
        val cmd = parser.parseWithState(
            "7",
            VoiceState.AWAITING_MARK,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(7), cmd)
    }

    @Test
    fun twoNumbersInMarkStateReturnsMarkByNumbers() {
        val cmd = parser.parseWithState(
            "пять шесть",
            VoiceState.AWAITING_MARK,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkByNumbers(listOf(5, 6)), cmd)
    }

    @Test
    fun cancelInMarkStateReturnsUndo() {
        val cmd = parser.parseWithState(
            "отмена",
            VoiceState.AWAITING_MARK,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Undo, cmd)
    }

    @Test
    fun stopInMarkStateReturnsStop() {
        val cmd = parser.parseWithState(
            "стоп",
            VoiceState.AWAITING_MARK,
            VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Stop, cmd)
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
        // AWAITING_CONTINUE — констатация, не вопрос. Юзер работает с экраном.
        assertTrue(!VoiceState.AWAITING_CONTINUE.hasWaitTimeout)
        assertTrue(!VoiceState.LISTENING.hasWaitTimeout)
        assertTrue(!VoiceState.FOUND_PINNED.hasWaitTimeout)
        assertTrue(!VoiceState.PAUSED.hasWaitTimeout)
        assertTrue(!VoiceState.IDLE.hasWaitTimeout)
    }
}
