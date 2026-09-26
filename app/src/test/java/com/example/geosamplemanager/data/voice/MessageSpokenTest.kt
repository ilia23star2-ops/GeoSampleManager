package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX 5.8.11-sort-fix-3:
 * Проверка fallback `spoken ?: text` для VoiceExecResult.Message.
 */
class MessageSpokenTest {

    @Test
    fun messageWithoutSpokenUsesText() {
        val msg = VoiceExecResult.Message(text = "Слушаю следующую скважину.")
        assertEquals("Слушаю следующую скважину.", msg.text)
        assertNull(msg.spoken)
    }

    @Test
    fun messageWithSpokenReturnsSpoken() {
        val msg = VoiceExecResult.Message(
            text = "Скважина NV1366 → Наряд №1.",
            spoken = "Скважина эн вэ тринадцать шестьдесят шесть, Наряд №1."
        )
        assertEquals("Скважина NV1366 → Наряд №1.", msg.text)
        assertEquals("Скважина эн вэ тринадцать шестьдесят шесть, Наряд №1.", msg.spoken)
    }

    @Test
    fun fallbackSpokenOrText() {
        val a = VoiceExecResult.Message("текст")
        val b = VoiceExecResult.Message("текст", "спокен")
        assertEquals("текст", a.spoken ?: a.text)
        assertEquals("спокен", b.spoken ?: b.text)
    }
}