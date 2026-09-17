package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSearchTest {

    // ================================================================
    // Фикстуры
    // ================================================================

    private fun hit(
        sampleId: Long,
        sampleNumber: String,
        wellNumber: String,
        orderId: Long = 1L,
        orderNumber: String = "1",
        areaTitle: String = "Test"
    ) = VoiceSampleHit(
        sampleId = sampleId,
        sampleNumber = sampleNumber,
        wellNumber = wellNumber,
        orderId = orderId,
        orderNumber = orderNumber,
        areaTitle = areaTitle
    )

    // ================================================================
    // L1 — точное sample_number
    // ================================================================

    @Test
    fun exactSampleNumberMatches() {
        val scope = listOf(
            hit(1, "NV152601", "NV1526"),
            hit(2, "NV152602", "NV1526")
        )
        val r = UnifiedSearch.search(scope, listOf("152601"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(UnifiedMatchKind.SAMPLE, r.matchedKind)
        assertEquals(1, r.level)
        assertTrue(r.isUnique)
        assertEquals(1, r.hits.size)
        assertEquals("NV152601", r.matchedValue)
    }

    // ================================================================
    // L2 — точное well_number
    // ================================================================

    @Test
    fun exactWellNumberMatchesAllSamples() {
        val scope = listOf(
            hit(1, "NV152601", "NV1526"),
            hit(2, "NV152602", "NV1526"),
            hit(3, "NV152603", "NV1526")
        )
        val r = UnifiedSearch.search(scope, listOf("1526"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(UnifiedMatchKind.WELL, r.matchedKind)
        assertEquals(2, r.level)
        assertTrue(r.isUnique)
        assertEquals(3, r.hits.size)
        assertEquals("NV1526", r.matchedValue)
    }

    @Test
    fun twoWellsWithSameNumberIsNotUnique() {
        val scope = listOf(
            hit(1, "NV152601", "NV1526", orderId = 1L),
            hit(2, "NV152601", "NV1526", orderId = 2L)
        )
        val r = UnifiedSearch.search(scope, listOf("1526"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(UnifiedMatchKind.WELL, r.matchedKind)
        assertFalse(r.isUnique)
    }

    // ================================================================
    // L3 — суффикс well_number
    // ================================================================

    @Test
    fun wellSuffixMatches() {
        val scope = listOf(hit(1, "NV152601", "NV1526"))
        val r = UnifiedSearch.search(scope, listOf("526"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(UnifiedMatchKind.WELL, r.matchedKind)
        assertEquals(3, r.level)
    }

    @Test
    fun shortSuffixIsIgnored() {
        val scope = listOf(hit(1, "NV152601", "NV1526"))
        val r = UnifiedSearch.search(scope, listOf("26"))
        assertTrue(r is UnifiedSearchResult.NotFound)
    }

    // ================================================================
    // L4 — суффикс sample_number
    // ================================================================

    @Test
    fun sampleSuffixMatches() {
        val scope = listOf(hit(1, "NV152601", "NV1526"))
        val r = UnifiedSearch.search(scope, listOf("601"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(UnifiedMatchKind.SAMPLE, r.matchedKind)
        assertEquals(4, r.level)
    }

    // ================================================================
    // L5 — нормализация нулей
    // ================================================================

    @Test
    fun zeroNormalizationMatches() {
        val scope = listOf(hit(1, "NV1524001", "NV1524"))
        val r = UnifiedSearch.search(scope, listOf("152401"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(UnifiedMatchKind.SAMPLE, r.matchedKind)
        assertEquals(5, r.level)
    }

    // ================================================================
    // L6 — fuzzy
    // ================================================================

    @Test
    fun fuzzyMatchesOneExtraZero() {
        val scope = listOf(hit(1, "NV1502", "NV15"))
        val r = UnifiedSearch.search(scope, listOf("152"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(6, r.level)
    }

    // ================================================================
    // L0 — prefix в filterMode
    // ================================================================

    @Test
    fun filterModePrefixMatches() {
        val scope = listOf(
            hit(1, "NV152401", "NV1524"),
            hit(2, "NV152501", "NV1525"),
            hit(3, "NV152601", "NV1526")
        )
        val r = UnifiedSearch.search(scope, listOf("1524"), filterMode = true)
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(0, r.level)
        assertEquals(1, r.hits.size)
        assertEquals("NV152401", r.hits.first().sampleNumber)
    }

    @Test
    fun filterModeShortPrefixMatches() {
        val scope = listOf(hit(1, "NV152401", "NV1524"))
        val r = UnifiedSearch.search(scope, listOf("1"), filterMode = true)
        assertTrue(r is UnifiedSearchResult.Found)
    }

    @Test
    fun prefixDoesNotMatchWithoutFilterMode() {
        val scope = listOf(hit(1, "NV152401", "NV1524"))
        val r = UnifiedSearch.search(scope, listOf("15"), filterMode = false)
        assertTrue(r is UnifiedSearchResult.NotFound)
    }

    // ================================================================
    // Кросс-префиксный поиск
    // ================================================================

    @Test
    fun candidateWithPrefixMatchesDigitsOnly() {
        val scope = listOf(hit(1, "NV152601", "NV1526"))
        val r = UnifiedSearch.search(scope, listOf("NV1526"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(UnifiedMatchKind.WELL, r.matchedKind)
        assertEquals("NV1526", r.matchedValue)
    }

    // ================================================================
    // Краевые случаи
    // ================================================================

    @Test
    fun notFoundWhenNothingMatches() {
        val scope = listOf(hit(1, "NV152601", "NV1526"))
        val r = UnifiedSearch.search(scope, listOf("999999"))
        assertTrue(r is UnifiedSearchResult.NotFound)
    }

    @Test
    fun emptyScopeReturnsNotFound() {
        val r = UnifiedSearch.search(emptyList(), listOf("1526"))
        assertTrue(r is UnifiedSearchResult.NotFound)
    }

    @Test
    fun emptyCandidatesReturnsNotFound() {
        val scope = listOf(hit(1, "NV152601", "NV1526"))
        val r = UnifiedSearch.search(scope, emptyList())
        assertTrue(r is UnifiedSearchResult.NotFound)
    }

    @Test
    fun candidateWithoutDigitsIsSkipped() {
        val scope = listOf(hit(1, "NV152601", "NV1526"))
        val r = UnifiedSearch.search(scope, listOf("abc", "1526"))
        assertTrue(r is UnifiedSearchResult.Found)
        r as UnifiedSearchResult.Found
        assertEquals(2, r.level)
    }

    // ================================================================
    // Утилиты — отдельно
    // ================================================================

    @Test
    fun normalizeZeroesCollapsesRuns() {
        assertEquals("1031", UnifiedSearch.normalizeZeroes("100031"))
        assertEquals("1524", UnifiedSearch.normalizeZeroes("1524"))
        assertEquals("10", UnifiedSearch.normalizeZeroes("1000"))
    }

    @Test
    fun fuzzyMatchAcceptsOneExtraZero() {
        assertTrue(UnifiedSearch.fuzzyMatch("1524", "15240"))
        assertFalse(UnifiedSearch.fuzzyMatch("1524", "1525"))
        assertFalse(UnifiedSearch.fuzzyMatch("1524", "152"))
    }
}
