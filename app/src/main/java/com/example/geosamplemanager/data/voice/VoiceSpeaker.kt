package com.example.geosamplemanager.data.voice

/**
 * Произношение номеров для TTS.
 *
 *  • spellOut — для номеров с буквами (KPD1090031 → «ка пэ дэ один ноль ...»).
 *  • spellNumber — для чисел: короткие читаются как число, длинные — по цифрам.
 */
object VoiceSpeaker {

    private val letterNames: Map<Char, String> = mapOf(
        'A' to "а", 'B' to "бэ", 'C' to "цэ", 'D' to "дэ", 'E' to "е",
        'F' to "эф", 'G' to "жэ", 'H' to "аш", 'I' to "и", 'J' to "йот",
        'K' to "ка", 'L' to "эль", 'M' to "эм", 'N' to "эн", 'O' to "о",
        'P' to "пэ", 'Q' to "ку", 'R' to "эр", 'S' to "эс", 'T' to "тэ",
        'U' to "у", 'V' to "вэ", 'W' to "дубль-вэ", 'X' to "икс",
        'Y' to "игрек", 'Z' to "зэт"
    )

    private val digitNames: Map<Char, String> = mapOf(
        '0' to "ноль", '1' to "один", '2' to "два", '3' to "три",
        '4' to "четыре", '5' to "пять", '6' to "шесть", '7' to "семь",
        '8' to "восемь", '9' to "девять"
    )

    /**
     * Произнести по буквам и цифрам: «KPD1090031» → «ка пэ дэ один ноль …».
     */
    fun spellOut(text: String): String {
        if (text.isBlank()) return text
        val parts = mutableListOf<String>()
        for (c in text) {
            val upper = c.uppercaseChar()
            when {
                c.isDigit() -> digitNames[c]?.let { parts.add(it) }
                upper in letterNames -> letterNames[upper]?.let { parts.add(it) }
                c == '-' || c == ' ' -> parts.add(" ")
                else -> parts.add(c.toString())
            }
        }
        return parts.joinToString(" ")
    }

    /**
     * Произнести число.
     *
     * Короткие числа (< 10000) возвращаются как есть — TTS читает
     * их словами («10» → «десять»). Длинные — по цифрам, иначе TTS
     * может прочитать «1090031» как «один миллион …».
     */
    fun spellNumber(value: Int): String {
        return if (value in 0..9999) {
            value.toString()
        } else {
            spellOut(value.toString())
        }
    }
}