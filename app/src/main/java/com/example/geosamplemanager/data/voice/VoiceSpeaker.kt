package com.example.geosamplemanager.data.voice

/**
 * Произношение номеров для TTS.
 *
 * Основная идея: длинные номера вслух диктуют **парами цифр** — так же,
 * как Vosk их слышит («15 24» → «пятнадцать двадцать четыре»).
 *
 *  • spellOut — для номеров с буквами (KPD1090031 → «ка пэ дэ 10 90 03 1»).
 *  • spellNumber — для чисел: короткие читаются как число, длинные — по парам.
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
     * Произнести номер: буквы — по буквам, цифры — парами через пробел.
     *
     * «NV1524» → «эн вэ 15 24» → TTS: «эн вэ пятнадцать двадцать четыре»
     * «KPD1090031» → «ка пэ дэ 10 90 03 1»
     * «1524» → «15 24»
     */
    fun spellOut(text: String): String {
        if (text.isBlank()) return text
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isDigit()) {
                // Собираем все цифры подряд и разбиваем на пары.
                var j = i
                while (j < text.length && text[j].isDigit()) j++
                val digits = text.substring(i, j)
                sb.append(breakIntoPairs(digits))
                if (j < text.length) sb.append(' ')
                i = j
            } else {
                val upper = c.uppercaseChar()
                when {
                    upper in letterNames -> {
                        sb.append(letterNames[upper])
                        sb.append(' ')
                    }
                    c == '-' || c == ' ' -> sb.append(' ')
                    else -> {
                        sb.append(c)
                        sb.append(' ')
                    }
                }
                i++
            }
        }
        return sb.toString().trim().replace(Regex(" +"), " ")
    }

    /**
     * Разбить строку цифр на пары слева направо, разделяя пробелом.
     * Если последняя группа содержит одну цифру — оставить её как есть.
     *
     * «1524» → «15 24»
     * «152401» → «15 24 01»
     * «1090031» → «10 90 03 1»
     * «5» → «5»
     */
    private fun breakIntoPairs(digits: String): String {
        if (digits.length <= 2) return digits
        val sb = StringBuilder()
        var i = 0
        while (i < digits.length) {
            val end = minOf(i + 2, digits.length)
            sb.append(digits.substring(i, end))
            i = end
            if (i < digits.length) sb.append(' ')
        }
        return sb.toString()
    }

    /**
     * Произнести число.
     *
     * Короткие числа (< 10000) возвращаются как есть — TTS читает
     * их словами («10» → «десять», «2265» → «две тысячи двести шестьдесят
     * пять»). Длинные — по парам, как spellOut.
     */
    fun spellNumber(value: Int): String {
        return if (value in 0..9999) {
            value.toString()
        } else {
            spellOut(value.toString())
        }
    }

    /**
     * Произнести по одной цифре (старый способ).
     * Используется как запасной вариант, если пары почему-то не подходят.
     */
    fun spellByDigits(text: String): String {
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
}