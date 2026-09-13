package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePrefixResolverTest {

    private val known = setOf("KPD", "KOP", "NV", "ACD")

    @Test
    fun kpdFromSound() {
        val r = VoicePrefixResolver(known).extract("капэдэ сто")
        assertEquals("KPD", r.prefix)
        assertEquals("сто", r.remainder)
        assertEquals(false, r.matchedFromCustom)
    }

    @Test
    fun nvFromSound() {
        val r = VoicePrefixResolver(known).extract("энвэ тысяча")
        assertEquals("NV", r.prefix)
        assertEquals("тысяча", r.remainder)
    }

    @Test
    fun acdFromSound() {
        val r = VoicePrefixResolver(known).extract("а си ди")
        assertEquals("ACD", r.prefix)
        assertEquals("", r.remainder)
    }

    @Test
    fun noPrefixWhenNumber() {
        val r = VoicePrefixResolver(known).extract("сто двадцать")
        assertNull(r.prefix)
        assertEquals("сто двадцать", r.remainder)
    }

    @Test
    fun unknownPrefixRejected() {
        // «би си ди» → BCD, нет в known
        val r = VoicePrefixResolver(known).extract("би си ди сто")
        assertNull(r.prefix)
    }

    @Test
    fun customPronunciation() {
        val custom = mapOf("капэдэ" to "KPD", "энвэ" to "NV")
        val r = VoicePrefixResolver(known, custom).extract("капэдэ сто")
        assertEquals("KPD", r.prefix)
        assertTrue(r.matchedFromCustom)
    }

    @Test
    fun customPronunciationOverridesLetterParser() {
        val custom = mapOf("капэдэ" to "KOP")
        val r = VoicePrefixResolver(known, custom).extract("капэдэ сто")
        assertEquals("KOP", r.prefix)
        assertTrue(r.matchedFromCustom)
    }
}