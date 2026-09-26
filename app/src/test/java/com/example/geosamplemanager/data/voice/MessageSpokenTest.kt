package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.8.11-sort-fix-3/4:
 * Проверка полей Message: text, spoken, display.
 */
class MessageSpokenTest {

    @Test
    fun messageWithoutSpokenUsesText() {
        val msg = VoiceExecResult.Message(text = "Слушаю следующую скважину.")
        assertEquals("Слушаю следующую скважину.", msg.text)
        assertNull(msg.spoken)
        assertNull(msg.display)
    }

    @Test
    fun messageWithSpokenReturnsSpoken() {
        val msg = VoiceExecResult.Message(
            text = "Скважина NV1366 → Наряд №1.",
            spoken = "Скважина эн вэ тринадцать шестьдесят шесть, Наряд №1."
        )
        assertEquals("Скважина NV1366 → Наряд №1.", msg.text)
        assertEquals("Скважина эн вэ тринадцать шестьдесят шесть, Наряд №1.", msg.spoken)
        assertNull(msg.display)
    }

    @Test
    fun fallbackSpokenOrText() {
        val a = VoiceExecResult.Message("текст")
        val b = VoiceExecResult.Message("текст", "спокен")
        assertEquals("текст", a.spoken ?: a.text)
        assertEquals("спокен", b.spoken ?: b.text)
    }

    /**
     * FIX 5.8.11-sort-fix-4:
     * display — канонический номер для поля «Распознано» в SORT.
     */
    @Test
    fun messageWithDisplay() {
        val msg = VoiceExecResult.Message(
            text = "Скважина эн вэ тринадцать шестьдесят шесть, Наряд №1.",
            display = "NV1366"
        )
        assertEquals("NV1366", msg.display)
    }

    @Test
    fun fallbackDisplayOrText() {
        val withDisplay = VoiceExecResult.Message("a", null, "NV1366")
        val withoutDisplay = VoiceExecResult.Message("a")
        assertEquals("NV1366", withDisplay.display)
        assertNull(withoutDisplay.display)
    }
}