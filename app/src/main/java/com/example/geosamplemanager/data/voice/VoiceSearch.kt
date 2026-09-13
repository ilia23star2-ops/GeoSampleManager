package com.example.geosamplemanager.data.voice

import kotlin.math.abs

/**
 * Проба, найденная голосовым поиском.
 */
data class VoiceSampleHit(
    val sampleId: Long,
    val sampleNumber: String,
    val wellNumber: String,
    val orderId: Long,
    val orderNumber: String,
    val areaTitle: String
)

/**
 * Источник данных для голосового поиска.
 * В проде — VoiceSearchRepository; в тестах — фейковый список.
 */
interface VoiceSampleSource {
    suspend fun loadAll(): List<VoiceSampleHit>
}

/**
 * Результат поиска.
 */
sealed class VoiceSearchResult {
    data class FoundOne(
        val hit: VoiceSampleHit,
        val candidate: String,
        val level: Int
    ) : VoiceSearchResult()

    data class FoundMany(
        val hits: List<VoiceSampleHit>,
        val candidate: String,
        val level: Int
    ) : VoiceSearchResult()

    data object NotFound : VoiceSearchResult()
}

/**
 * Голосовой поиск.
 *
 * 5 уровней из §6 VOICE.md:
 *   1. sample_number = кандидат (точное).
 *   2. well_number = кандидат (все пробы скважины).
 *   3. нормализация нулей («10900031» ≈ «1090031»).
 *   4. well + суффикс — отложено до 5.8.4 (нужен контекст сессии).
 *   5. fuzzy — по §7.
 *
 * Первый найденный результат — победитель.
 */
class VoiceSearch(private val source: VoiceSampleSource) {

    suspend fun search(candidates: List<String>): VoiceSearchResult {
        if (candidates.isEmpty()) return VoiceSearchResult.NotFound

        val all = source.loadAll()
        if (all.isEmpty()) return VoiceSearchResult.NotFound

        for (candidate in candidates) {
            val clean = candidate.replace("|", "").trim()
            if (clean.isEmpty()) continue

            // Уровень 1: точное совпадение sample_number.
            val bySample = all.filter { it.sampleNumber == clean }
            when (bySample.size) {
                0 -> Unit
                1 -> return VoiceSearchResult.FoundOne(bySample[0], clean, 1)
                else -> return VoiceSearchResult.FoundMany(bySample, clean, 1)
            }

            // Уровень 2: точное совпадение well_number.
            val byWell = all.filter { it.wellNumber == clean }
            when (byWell.size) {
                0 -> Unit
                1 -> return VoiceSearchResult.FoundOne(byWell[0], clean, 2)
                else -> return VoiceSearchResult.FoundMany(byWell, clean, 2)
            }

            // Уровень 3: нормализация нулей.
            val normalized = normalizeZeroes(clean)
            val byNorm = all.filter { normalizeZeroes(it.sampleNumber) == normalized }
            when (byNorm.size) {
                0 -> Unit
                1 -> return VoiceSearchResult.FoundOne(byNorm[0], clean, 3)
                else -> return VoiceSearchResult.FoundMany(byNorm, clean, 3)
            }
        }

        // Уровень 5: fuzzy.
        for (candidate in candidates) {
            val clean = candidate.replace("|", "").trim()
            if (clean.isEmpty()) continue
            val fuzzy = all.filter { fuzzyMatch(it.sampleNumber, clean) }
            when (fuzzy.size) {
                0 -> Unit
                1 -> return VoiceSearchResult.FoundOne(fuzzy[0], clean, 5)
                else -> return VoiceSearchResult.FoundMany(fuzzy, clean, 5)
            }
        }

        return VoiceSearchResult.NotFound
    }

    companion object {

        /**
         * Сжимает кратные нули: «10900031» → «109031».
         */
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

        /**
         * Fuzzy-совпадение по §7 VOICE.md:
         *  • ненулевые цифры точно совпадают;
         *  • разница длин ≤ 1;
         *  • Левенштейн полной строки ≤ 1.
         */
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
                    cur[j] = minOf(
                        prev[j] + 1,
                        cur[j - 1] + 1,
                        prev[j - 1] + cost
                    )
                }
                System.arraycopy(cur, 0, prev, 0, cur.size)
            }
            return prev[b.length]
        }
    }
}