package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-sort-normalize:
 * Регрессия на фразу Vosk, из-за которой SORT писал в поле фонетику.
 * Парсер должен собрать «2850022»: 285 + два нуля + 22.
 */
class VoiceNumberParserSortTest {

    private val parser = VoiceNumberParser()

    @Test
    fun phrase_285_two_nulls_22_primary_is_2850022() {
        val result = parser.parse("двести восемьдесят пять два ноля двадцать два")
        assertEquals("2850022", result.primary)
    }

    @Test
    fun phrase_285_two_nulls_22_candidates_contain_2850022() {
        val result = parser.parse("двести восемьдесят пять два ноля двадцать два")
        assertTrue("2850022" in result.candidates)
    }

    @Test
    fun phrase_285_two_nulls_22_no_separators_in_primary() {
        val result = parser.parse("двести восемьдесят пять два ноля двадцать два")
        assertTrue(!(result.primary ?: "").contains('|'))
    }

    @Test
    fun phrase_285_null_null_22_also_gives_2850022() {
        val result = parser.parse("двести восемьдесят пять ноль ноль двадцать два")
        assertEquals("2850022", result.primary)
    }
}