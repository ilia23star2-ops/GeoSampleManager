package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    private val parser = VoiceCommandParser()

    @Test
    fun markOrdinalFirst() {
        val c = parser.parse("первая")
        assertTrue(c is VoiceCommand.MarkOrdinal)
        assertEquals(1, (c as VoiceCommand.MarkOrdinal).ordinal)
    }

    @Test
    fun markOrdinalTwentyFirst() {
        val c = parser.parse("двадцать первая")
        assertTrue(c is VoiceCommand.MarkOrdinal)
        assertEquals(21, (c as VoiceCommand.MarkOrdinal).ordinal)
    }

    @Test
    fun clearOrdinalFirst() {
        val c = parser.parse("снять первую")
        assertTrue(c is VoiceCommand.ClearOrdinal)
        assertEquals(1, (c as VoiceCommand.ClearOrdinal).ordinal)
    }

    @Test
    fun clearAll() {
        assertEquals(VoiceCommand.ClearAll, parser.parse("снять все"))
    }

    @Test
    fun weightDirectNumber() {
        val c = parser.parse("вес 2.5")
        assertTrue(c is VoiceCommand.SetWeight)
        assertEquals(2.5, (c as VoiceCommand.SetWeight).value, 0.001)
    }

    @Test
    fun weightWordsTwoFive() {
        val c = parser.parse("вес два пять")
        assertTrue(c is VoiceCommand.SetWeight)
        assertEquals(2.5, (c as VoiceCommand.SetWeight).value, 0.001)
    }

    @Test
    fun stopCommand() {
        assertEquals(VoiceCommand.Stop, parser.parse("стоп"))
        assertEquals(VoiceCommand.Stop, parser.parse("хватит"))
    }

    @Test
    fun nextCommand() {
        assertEquals(VoiceCommand.Next, parser.parse("следующая"))
    }

    @Test
    fun searchPlainNumber() {
        val c = parser.parse("1524")
        assertTrue(c is VoiceCommand.Search)
        assertEquals("1524", (c as VoiceCommand.Search).query)
    }

    @Test
    fun sortTwoNumbers() {
        val c = parser.parse("1524 и 1525")
        assertTrue(c is VoiceCommand.Sort)
        assertEquals(listOf("1524", "1525"), (c as VoiceCommand.Sort).queries)
    }

    @Test
    fun helpCommand() {
        assertEquals(VoiceCommand.Help, parser.parse("помощь"))
    }

    @Test
    fun undoAndRedo() {
        assertEquals(VoiceCommand.Undo, parser.parse("отмена"))
        assertEquals(VoiceCommand.Undo, parser.parse("отменить"))
        assertEquals(VoiceCommand.Undo, parser.parse("верни"))
        assertEquals(VoiceCommand.Undo, parser.parse("назад"))

        assertEquals(VoiceCommand.Redo, parser.parse("вперёд"))
        assertEquals(VoiceCommand.Redo, parser.parse("вперед"))

        // FIX 5.8.6-5c: «повтори» убран из активных команд,
        // потому что Vosk часто распознаёт его как «три».
        assertEquals(VoiceCommand.Unknown, parser.parse("повтори"))
    }
}
