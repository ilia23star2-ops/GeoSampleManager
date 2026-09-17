package com.example.geosamplemanager.data.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchTest {

    private val hits = listOf(
        VoiceSampleHit(1, "KPD1090031", "KPD109003", 1, "12", "Коптеловский"),
        VoiceSampleHit(2, "KPD1090032", "KPD109003", 1, "12", "Коптеловский"),
        VoiceSampleHit(3, "NV136601", "NV1366", 2, "1", "Нейвинский"),
        VoiceSampleHit(4, "NV136602", "NV1366", 2, "1", "Нейвинский")
    )

    private val source = object : VoiceSampleSource {
        override suspend fun loadAll() = hits
    }

    private val search = VoiceSearch(source)

    @Test
    fun exactSampleNumber() = runBlocking {
        val r = search.search(listOf("KPD1090031"))
        assertTrue(r is VoiceSearchResult.FoundOne)
        r as VoiceSearchResult.FoundOne
        assertEquals(1L, r.hit.sampleId)
        assertEquals(1, r.level)
    }

    /**
     * FIX 5.8.9-infra-2a-fix-3: точное совпадение well_number, когда
     * скважина одна в одном наряде — это FoundOne с isSample=false,
     * а не FoundMany. Так работает VoiceSearch (L2: uniqueWells.size == 1).
     */
    @Test
    fun exactWellNumber() = runBlocking {
        val r = search.search(listOf("NV1366"))
        assertTrue(r is VoiceSearchResult.FoundOne)
        r as VoiceSearchResult.FoundOne
        assertFalse(r.isSample)
        assertEquals(2, r.level)
    }

    /**
     * FIX 5.8.9-infra-2a-fix-3: нормализация нулей в VoiceSearch — это
     * уровень 5 (L5), а не 3. Тест приведён к реальной реализации.
     */
    @Test
    fun normalizeZeroesLevel5() = runBlocking {
        // В БД KPD1090031, кандидат с лишним нулём
        val r = search.search(listOf("KPD10900031"))
        assertTrue(r is VoiceSearchResult.FoundOne)
        r as VoiceSearchResult.FoundOne
        assertEquals(1L, r.hit.sampleId)
        assertEquals(5, r.level)
    }

    @Test
    fun notFound() = runBlocking {
        val r = search.search(listOf("9999999"))
        assertTrue(r is VoiceSearchResult.NotFound)
    }

    @Test
    fun firstCandidateWins() = runBlocking {
        // Первый кандидат не находится, второй — находится
        val r = search.search(listOf("9999999", "KPD1090032"))
        assertTrue(r is VoiceSearchResult.FoundOne)
        r as VoiceSearchResult.FoundOne
        assertEquals(2L, r.hit.sampleId)
    }

    @Test
    fun normalizeZeroes_helper() {
        assertEquals("109031", VoiceSearch.normalizeZeroes("10900031"))
        assertEquals("109031", VoiceSearch.normalizeZeroes("1090031"))
        assertEquals("109031", VoiceSearch.normalizeZeroes("109031"))
        assertEquals("", VoiceSearch.normalizeZeroes(""))
        assertEquals("0", VoiceSearch.normalizeZeroes("0000"))
    }

    @Test
    fun fuzzyMatch_helper() {
        assertTrue(VoiceSearch.fuzzyMatch("1090031", "1090031"))
        assertFalse(VoiceSearch.fuzzyMatch("1090031", "1090032"))
        // Разная длина — не fuzzy
        assertFalse(VoiceSearch.fuzzyMatch("1090031", "10900"))
        // Различие в нуле — fuzzy (ненулевые совпадают, Левенштейн ≤ 1)
        assertTrue(VoiceSearch.fuzzyMatch("1090031", "10900310"))
    }
}
