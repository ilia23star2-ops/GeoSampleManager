package com.example.geosamplemanager.data.voice

import kotlin.math.abs

/**
 * Унифицированный поиск.
 *
 * FIX 5.8.9h-2b-i-fix-1: убран порог MAX_AMBIGUOUS — уровень
 * больше не пропускается при большом числе совпадений.
 * Точное совпадение / суффикс / fuzzy возвращают все хиты.
 *
 * Уровни (в порядке применения):
 *   0 — prefix (только в filterMode)   → SAMPLE
 *   1 — sample_number == candidate     → SAMPLE
 *   2 — well_number == candidate       → WELL
 *   3 — well_number.endsWith (≥3)      → WELL
 *   4 — sample_number.endsWith (≥3)    → SAMPLE
 *   5 — нормализация нулей             → WELL / SAMPLE
 *   6 — fuzzy (≥3)                     → WELL / SAMPLE
 */
object UnifiedSearch {

    fun search(
        scope: List<VoiceSampleHit>,
        candidates: List<String>,
        filterMode: Boolean = false
    ): UnifiedSearchResult {
        if (scope.isEmpty() || candidates.isEmpty()) return UnifiedSearchResult.NotFound

        for (raw in candidates) {
            val clean = digits(raw)
            if (clean.isEmpty()) continue

            // L0: prefix — только в filterMode.
            if (filterMode) {
                val hits = scope.filter { digits(it.sampleNumber).startsWith(clean) }
                if (hits.isNotEmpty()) {
                    return toResult(hits, clean, level = 0, kind = UnifiedMatchKind.SAMPLE)
                }
            }

            // L1: точное sample_number.
            run {
                val hits = scope.filter { digits(it.sampleNumber) == clean }
                if (hits.isNotEmpty()) {
                    return toResult(hits, clean, level = 1, kind = UnifiedMatchKind.SAMPLE)
                }
            }

            // L2: точное well_number.
            run {
                val hits = scope.filter { digits(it.wellNumber) == clean }
                if (hits.isNotEmpty()) {
                    return toResult(hits, clean, level = 2, kind = UnifiedMatchKind.WELL)
                }
            }

            // L3: суффикс well_number.
            if (clean.length >= MIN_SUFFIX_LEN) {
                val hits = scope.filter { digits(it.wellNumber).endsWith(clean) }
                if (hits.isNotEmpty()) {
                    return toResult(hits, clean, level = 3, kind = UnifiedMatchKind.WELL)
                }
            }

            // L4: суффикс sample_number.
            if (clean.length >= MIN_SUFFIX_LEN) {
                val hits = scope.filter { digits(it.sampleNumber).endsWith(clean) }
                if (hits.isNotEmpty()) {
                    return toResult(hits, clean, level = 4, kind = UnifiedMatchKind.SAMPLE)
                }
            }

            // L5: нормализация нулей.
            run {
                val norm = normalizeZeroes(clean)
                val sampleHits = scope.filter { normalizeZeroes(digits(it.sampleNumber)) == norm }
                val wellHits = scope.filter { normalizeZeroes(digits(it.wellNumber)) == norm }
                val hits = (sampleHits + wellHits).distinctBy { it.sampleId }
                if (hits.isNotEmpty()) {
                    val kind = if (sampleHits.isNotEmpty()) UnifiedMatchKind.SAMPLE
                    else UnifiedMatchKind.WELL
                    return toResult(hits, clean, level = 5, kind = kind)
                }
            }
        }

        // L6: fuzzy — отдельный проход по всем кандидатам.
        for (raw in candidates) {
            val clean = digits(raw)
            if (clean.length < MIN_SUFFIX_LEN) continue
            val hits = scope.filter {
                fuzzyMatch(digits(it.sampleNumber), clean) ||
                        fuzzyMatch(digits(it.wellNumber), clean)
            }
            if (hits.isNotEmpty()) {
                val sampleMatch = hits.any { fuzzyMatch(digits(it.sampleNumber), clean) }
                val kind = if (sampleMatch) UnifiedMatchKind.SAMPLE
                else UnifiedMatchKind.WELL
                return toResult(hits, clean, level = 6, kind = kind)
            }
        }

        return UnifiedSearchResult.NotFound
    }

    // ================================================================
    // Вспомогательные
    // ================================================================

    private fun toResult(
        hits: List<VoiceSampleHit>,
        candidate: String,
        level: Int,
        kind: UnifiedMatchKind
    ): UnifiedSearchResult {
        val uniqueWells = hits.distinctBy { it.orderId to it.wellNumber }
        val isUnique = uniqueWells.size == 1
        val matchedValue = when (kind) {
            UnifiedMatchKind.WELL -> hits.first().wellNumber
            UnifiedMatchKind.SAMPLE -> hits.first().sampleNumber
            UnifiedMatchKind.NONE -> candidate
        }
        return UnifiedSearchResult.Found(
            hits = hits,
            matchedKind = kind,
            matchedValue = matchedValue,
            level = level,
            isUnique = isUnique
        )
    }

    /** Только цифры. «NV1526» → «1526». */
    private fun digits(s: String): String = s.filter { it.isDigit() }

    /** Схлопывает подряд идущие нули: «1000031» → «1031». */
    fun normalizeZeroes(s: String): String {
        val sb = StringBuilder(s.length)
        var prevZero = false
        for (c in s) {
            if (c == '0') {
                if (!prevZero) sb.append(c)
                prevZero = true
            } else {
                sb.append(c)
                prevZero = false
            }
        }
        return sb.toString()
    }

    fun fuzzyMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (abs(a.length - b.length) > 1) return false
        val na = a.filter { it != '0' }
        val nb = b.filter { it != '0' }
        if (na != nb) return false
        return levenshtein(a, b) <= 1
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)

        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    private const val MIN_SUFFIX_LEN = 3
}

/**
 * Тип совпадения.
 */
enum class UnifiedMatchKind { NONE, WELL, SAMPLE }

/**
 * Результат унифицированного поиска.
 */
sealed class UnifiedSearchResult {
    data class Found(
        val hits: List<VoiceSampleHit>,
        val matchedKind: UnifiedMatchKind,
        val matchedValue: String,
        val level: Int,
        val isUnique: Boolean
    ) : UnifiedSearchResult()

    data object NotFound : UnifiedSearchResult()
}