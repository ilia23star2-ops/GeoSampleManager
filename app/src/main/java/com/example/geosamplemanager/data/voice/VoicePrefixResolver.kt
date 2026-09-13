package com.example.geosamplemanager.data.voice

/**
 * Резолвер префиксов участков.
 *
 * Преобразует произношение префикса («капэдэ», «энвэ») в канонический
 * вид (KPD, NV). Источники:
 *  1. Пользовательские произношения из VoiceSettings (приоритет).
 *  2. Встроенный словарь VoiceLetterSounds.
 *
 * Если получившийся префикс не входит в knownPrefixes — он не считается
 * распознанным (защита от случайных совпадений).
 */
class VoicePrefixResolver(
    knownPrefixes: Set<String>,
    customPronunciations: Map<String, String> = emptyMap()
) {

    private val canonicalPrefixes: Set<String> =
        knownPrefixes.map { it.uppercase() }.toSet()

    private val custom: Map<String, String> =
        customPronunciations
            .filterKeys { it.isNotBlank() }
            .mapKeys { normalize(it.key) }
            .mapValues { it.value.uppercase() }

    /**
     * Результат извлечения префикса.
     */
    data class Extraction(
        val prefix: String?,
        val remainder: String,
        val matchedFromCustom: Boolean
    )

    /**
     * Извлечь префикс с начала строки.
     *
     * Если префикс не распознан — prefix = null, remainder = вся строка.
     */
    fun extract(rawText: String): Extraction {
        val text = normalize(rawText)
        if (text.isEmpty()) return Extraction(null, "", false)

        // 1. Пользовательские произношения — самое длинное совпадение.
        val customMatch = custom.entries
            .filter { text.startsWith(it.key) }
            .maxByOrNull { it.key.length }
        if (customMatch != null) {
            val remainder = text.removePrefix(customMatch.key).trim()
            return Extraction(customMatch.value, remainder, true)
        }

        // 2. Буквенный парсер. Минимум 2 буквы и префикс должен быть
        //    в knownPrefixes.
        val parsed = parseLetters(text)
        if (parsed.prefix.length >= 2 && parsed.prefix in canonicalPrefixes) {
            return Extraction(parsed.prefix, parsed.remainder, false)
        }

        return Extraction(null, text, false)
    }

    private fun parseLetters(text: String): LetterParse {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.isWhitespace()) { i++; continue }

            var bestLen = 0
            var bestLetter: Char? = null
            for ((sound, letter) in VoiceLetterSounds.sounds) {
                if (text.startsWith(sound, i) && sound.length > bestLen) {
                    bestLen = sound.length
                    bestLetter = letter
                }
            }
            if (bestLetter == null) break
            sb.append(bestLetter)
            i += bestLen
        }
        return LetterParse(sb.toString(), text.substring(i).trim())
    }

    private data class LetterParse(val prefix: String, val remainder: String)

    private fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е').trim()
}