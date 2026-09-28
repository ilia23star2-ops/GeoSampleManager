package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-tests/3:
 * Тесты состояния PAUSED — parseWithState.
 *
 * Покрывают закрытый заход e4g: фикс опечатки «хатит» → «хватит».
 * До фикса в PAUSED не работали «стоп» и «хватит» — ГП отвечал
 * «Пауза. Скажите продолжить или стоп», хотя пользователь уже
 * сказал «стоп».
 *
 * Правила:
 *  - в PAUSED работают только Resume и Stop;
 *  - всё остальное → Unknown.
 */
class VoiceCommandParserPausedTest {

    private val parser = VoiceCommandParser()

    @Test
    fun pausedStop() {
        val cmd = parser.parseWithState(
            "стоп", VoiceState.PAUSED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Stop, cmd)
    }

    @Test
    fun pausedHvatit() {
        val cmd = parser.parseWithState(
            "хватит", VoiceState.PAUSED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Stop, cmd)
    }

    @Test
    fun pausedResume() {
        val cmd = parser.parseWithState(
            "продолжить", VoiceState.PAUSED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Resume, cmd)
    }

    @Test
    fun pausedResumeProdolzhay() {
        val cmd = parser.parseWithState(
            "продолжай", VoiceState.PAUSED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Resume, cmd)
    }

    @Test
    fun pausedOtherIsUnknown() {
        val cmd = parser.parseWithState(
            "семь", VoiceState.PAUSED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Unknown, cmd)
    }

    @Test
    fun pausedSearchIsUnknown() {
        val cmd = parser.parseWithState(
            "1524", VoiceState.PAUSED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.Unknown, cmd)
    }
}