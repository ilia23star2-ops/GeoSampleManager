package com.example.geosamplemanager.data.voice

import android.util.Log
import kotlin.math.abs

data class VoiceSampleHit(
    val sampleId: Long,
    val sampleNumber: String,
    val wellNumber: String,
    val orderId: Long,
    val orderNumber: String,
    val areaTitle: String
)

interface VoiceSampleSource {
    suspend fun loadAll(): List<VoiceSampleHit>
}

sealed class VoiceSearchResult {
    data class FoundOne(
        val hit: VoiceSampleHit,
        val candidate: String,
        val level: Int,
        val isSample: Boolean
    ) : VoiceSearchResult()

    data class FoundMany(
        val hits: List<VoiceSampleHit>,
        val candidate: String,
        val level: Int
    ) : VoiceSearchResult()

    data object NotFound : VoiceSearchResult()
}

class VoiceSearch(private val source: VoiceSampleSource) {

    suspend fun search(candidates: List<String>): VoiceSearchResult {
        if (candidates.isEmpty()) return VoiceSearchResult.NotFound

        val all = source.loadAll()
        Log.i(TAG, "search: загружено проб=${all.size}")
        if (all.isEmpty()) return VoiceSearchResult.NotFound

        for (candidate in candidates) {
            val clean = candidate.replace("|", "").trim()
            if (clean.isEmpty()) continue
            Log.i(TAG, "search: кандидат «$clean»")

            // L1: sample_number == кандидат → ПРОБА.
            run {
                val hits = all.filter { it.sampleNumber == clean }
                Log.d(TAG, "  L1 (sample==): ${hits.size}")
                if (hits.size == 1)
                    return VoiceSearchResult.FoundOne(hits[0], clean, 1, isSample = true)
                if (hits.size > 1)
                    return VoiceSearchResult.FoundMany(hits, clean, 1)
            }

            // L2: well_number == кандидат → СКВАЖИНА.
            run {
                val hits = all.filter { it.wellNumber == clean }
                Log.d(TAG, "  L2 (well==): ${hits.size}")
                if (hits.isNotEmpty()) {
                    // FIX И-14: проверяем уникальность пары (orderId, wellNumber)
                    val uniqueWells = hits.distinctBy { it.orderId to it.wellNumber }
                    if (uniqueWells.size == 1) {
                        return VoiceSearchResult.FoundOne(hits.first(), clean, 2, isSample = false)
                    }
                    return VoiceSearchResult.FoundMany(hits, clean, 2)
                }
            }

            // L3: суффикс well_number → СКВАЖИНА.
            run {
                val hits = all.filter { it.wellNumber.endsWith(clean) }
                Log.d(TAG, "  L3 (well.endsWith): ${hits.size}")
                if (hits.isNotEmpty()) {
                    // FIX И-14: уникальность пары (orderId, wellNumber)
                    val uniqueWells = hits.distinctBy { it.orderId to it.wellNumber }
                    if (uniqueWells.size == 1) {
                        return VoiceSearchResult.FoundOne(hits.first(), clean, 3, isSample = false)
                    }
                    return VoiceSearchResult.FoundMany(hits, clean, 3)
                }
            }

            // L4: суффикс sample_number → ПРОБА (одна).
            run {
                val hits = all.filter { it.sampleNumber.endsWith(clean) }
                Log.d(TAG, "  L4 (sample.endsWith): ${hits.size}")
                if (hits.size == 1)
                    return VoiceSearchResult.FoundOne(hits[0], clean, 4, isSample = true)
                if (hits.size > 1)
                    return VoiceSearchResult.FoundMany(hits, clean, 4)
            }

            // L5: нормализация нулей.
            run {
                val normalized = normalizeZeroes(clean)
                val hits = all.filter {
                    normalizeZeroes(it.sampleNumber) == normalized ||
                            normalizeZeroes(it.wellNumber) == normalized
                }
                Log.d(TAG, "  L5 (norm zeroes): ${hits.size}")
                if (hits.isNotEmpty()) {
                    val uniqueWells = hits.distinctBy { it.orderId to it.wellNumber }
                    if (uniqueWells.size == 1 && hits.size == 1) {
                        val h = hits[0]
                        val isSample = h.sampleNumber == clean || h.sampleNumber.endsWith(clean)
                        return VoiceSearchResult.FoundOne(h, clean, 5, isSample)
                    }
                    return VoiceSearchResult.FoundMany(hits, clean, 5)
                }
            }
        }

        // L6: fuzzy.
        for (candidate in candidates) {
            val clean = candidate.replace("|", "").trim()
            if (clean.isEmpty()) continue
            val hits = all.filter {
                fuzzyMatch(it.sampleNumber, clean) || fuzzyMatch(it.wellNumber, clean)
            }
            Log.d(TAG, "  L6 (fuzzy «$clean»): ${hits.size}")
            if (hits.isNotEmpty()) {
                val uniqueWells = hits.distinctBy { it.orderId to it.wellNumber }
                if (uniqueWells.size == 1 && hits.size == 1) {
                    val h = hits[0]
                    val isSample = fuzzyMatch(h.sampleNumber, clean)
                    return VoiceSearchResult.FoundOne(h, clean, 6, isSample)
                }
                return VoiceSearchResult.FoundMany(hits, clean, 6)
            }
        }

        return VoiceSearchResult.NotFound
    }

    companion object {
        private const val TAG = "VoiceSearch"

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
    }
}