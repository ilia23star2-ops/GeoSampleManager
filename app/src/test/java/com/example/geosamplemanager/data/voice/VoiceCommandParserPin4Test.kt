package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-pin-4:
 * В FOUND_PINNED фраза из числительных склеивается в одно число.
 */
class VoiceCommandParserPin4Test {

    private val parser = VoiceCommandParser()

    @Test
    fun pinnedMultiWordNumber() {
        val cmd = parser.parseWithState(
            "тринадцать шестьдесят семь",
            VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(1367), cmd)
    }

    @Test
    fun pinnedMultiWordNumberWithI() {
        val cmd = parser.parseWithState(
            "тринадцать и шестьдесят семь",
            VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(1367), cmd)
    }

    @Test
    fun pinnedFourteenStillMarkOrdinal() {
        val cmd = parser.parseWithState(
            "четырнадцать",
            VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(14), cmd)
    }

    @Test
    fun pinnedFourMarkOrdinal() {
        val cmd = parser.parseWithState(
            "четыре",
            VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(4), cmd)
    }

    @Test
    fun pinnedFourOrdinalWordMarkOrdinal() {
        val cmd = parser.parseWithState(
            "четвёртая",
            VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(4), cmd)
    }

    @Test
    fun pinnedBigNumberInWords() {
        val cmd = parser.parseWithState(
            "тысяча пятьсот двадцать четыре",
            VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        assertEquals(VoiceCommand.MarkOrdinal(1524), cmd)
    }

    @Test
    fun pinnedGarbageStillUnknown() {
        val cmd = parser.parseWithState(
            "семь утра было холодно",
            VoiceState.FOUND_PINNED, VoiceSessionMode.SEARCH
        )
        // Не числительные, не команды → Unknown.
        assertEquals(VoiceCommand.Unknown, cmd)
    }
}