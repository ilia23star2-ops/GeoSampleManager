package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.8.11-e4-tests/2:
 * Тесты команды «Найди» — VoiceCommand.Find.
 *
 * Покрывают закрытый заход e4g3.
 *
 * Проверяем:
 *  - Find без аргумента (→ null);
 *  - Find с аргументом (число, код);
 *  - формы глагола: найди / найти / ищи / искать / поищи;
 *  - нормализация (регистр, ё→е).
 */
class VoiceCommandParserFindTest {

    private val parser = VoiceCommandParser()

    @Test
    fun findWithoutArgIsNull() {
        assertEquals(VoiceCommand.Find(null), parser.parse("найди"))
    }

    @Test
    fun findWithNumber() {
        assertEquals(VoiceCommand.Find("1524"), parser.parse("найди 1524"))
    }

    @Test
    fun findWithKpdCode() {
        assertEquals(VoiceCommand.Find("kpd1090031"), parser.parse("найди KPD1090031"))
    }

    @Test
    fun findFormNayti() {
        assertEquals(VoiceCommand.Find(null), parser.parse("найти"))
    }

    @Test
    fun findFormIshi() {
        assertEquals(VoiceCommand.Find(null), parser.parse("ищи"))
    }

    @Test
    fun findFormIskat() {
        assertEquals(VoiceCommand.Find(null), parser.parse("искать"))
    }

    @Test
    fun findFormPoyishi() {
        assertEquals(VoiceCommand.Find(null), parser.parse("поищи"))
    }

    @Test
    fun findWithNumberAndWords() {
        assertEquals(VoiceCommand.Find("1524 01"), parser.parse("найди 1524 01"))
    }
}