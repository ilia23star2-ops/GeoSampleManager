package com.example.geosamplemanager.data.voice

import kotlin.math.roundToInt

/**
 * Произношение номеров, весов и счётных фраз для TTS.
 *
 * FIX 5.8.11-e4-speak-1:
 * - splitLikeHuman(digits) — разбивает слитную строку цифр так,
 *   как сказал бы человек: 1366 → 13|66, 1090031 → 109|00|31,
 *   109003101 → 109|00|31|01.
 * - spellMimicry(text) переписан: сначала простая ветка
 *   «префикс + цифры» без разделителей, потом fallback через
 *   QueryTokenizer + DigitGrouper.
 *
 * FIX 5.8.11-e4-prefix-1:
 * - spellLetters разделяет буквы пробелом: «KPD» → «ка пэ дэ».
 *   Vosk в грамматике учит эти же звуки по отдельности, поэтому
 *   речь TTS и распознавание симметричны.
 * - Одна буква пробелом не округляется: «W» → «даблю».
 */
object VoiceSpeaker {

    private val letterToSound: Map<Char, String> = mapOf(
        'A' to "а", 'B' to "бэ", 'C' to "цэ", 'D' to "дэ", 'E' to "е",
        'F' to "эф", 'G' to "гэ", 'H' to "аш", 'I' to "и", 'J' to "жэ",
        'K' to "ка", 'L' to "эль", 'M' to "эм", 'N' to "эн", 'O' to "о",
        'P' to "пэ", 'Q' to "ку", 'R' to "эр", 'S' to "эс", 'T' to "тэ",
        'U' to "у", 'V' to "вэ", 'W' to "даблю", 'X' to "икс",
        'Y' to "игрек", 'Z' to "зэт"
    )

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

    private val unitsMasculine = arrayOf(
        "ноль", "один", "два", "три", "четыре",
        "пять", "шесть", "семь", "восемь", "девять"
    )

    private val unitsFeminine = arrayOf(
        "ноль", "одна", "две", "три", "четыре",
        "пять", "шесть", "семь", "восемь", "девять"
    )

    private val teens = arrayOf(
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    )

    private val tens = arrayOf(
        "", "", "двадцать", "тридцать", "сорок",
        "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    )

    private val hundreds = arrayOf(
        "", "сто", "двести", "триста", "четыреста",
        "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот"
    )

    // ================================================================
    // Мимикрия для строки-номера
    // ================================================================

    private val SIMPLE_NUMBER = Regex("^([A-Za-z]+)?\\s*(\\d+)$")

    fun spellMimicry(text: String): String {
        if (text.isBlank()) return text

        val trimmed = text.trim()
        val match = SIMPLE_NUMBER.find(trimmed)
        if (match != null) {
            val prefix = match.groupValues[1]
            val digits = match.groupValues[2]
            return spellPrefixAndDigits(prefix, digits)
        }

        // Fallback: старый путь через токенизацию.
        return try {
            val tokens = QueryTokenizer().tokenize(text)
            if (tokens.isEmpty()) return spellOut(text)

            val groups = DigitGrouper.group(tokens)
            if (groups.isEmpty()) return spellOut(text)

            spellOut(groups)
        } catch (_: Exception) {
            spellOut(text)
        }
    }

    private fun spellPrefixAndDigits(prefix: String, digits: String): String {
        val groups = mutableListOf<DigitGroup>()

        if (prefix.isNotEmpty()) {
            groups.add(DigitGroup(prefix.uppercase(), GroupKind.PREFIX))
        }

        for (g in splitLikeHuman(digits)) {
            val kind = when {
                g.length >= 2 && g.all { it == '0' } -> GroupKind.LEADING_ZERO
                g.length == 1 -> GroupKind.SINGLE
                else -> GroupKind.PLAIN
            }
            groups.add(DigitGroup(g, kind))
        }

        return spellOut(groups)
    }

    /**
     * FIX 5.8.11-e4-speak-1:
     * Разбить слитную строку цифр так, как сказал бы человек.
     *
     * Правило:
     *   длина ≤ 3   → как есть.
     *   чётная      → пары слева: 2+2+2+...
     *   нечётная    → первая 3, потом пары: 3+2+2+...
     */
    fun splitLikeHuman(digits: String): List<String> {
        if (digits.isEmpty()) return emptyList()
        if (digits.length <= 3) return listOf(digits)

        val firstLen = if (digits.length % 2 == 0) 2 else 3

        val result = mutableListOf<String>()
        result.add(digits.substring(0, firstLen))

        var i = firstLen
        while (i < digits.length) {
            val end = minOf(i + 2, digits.length)
            result.add(digits.substring(i, end))
            i = end
        }
        return result
    }

    // ================================================================
    // Произношение по группам
    // ================================================================

    fun spellOut(groups: List<DigitGroup>): String {
        if (groups.isEmpty()) return ""

        val parts = mutableListOf<String>()

        for (group in groups) {
            val text = when (group.kind) {
                GroupKind.PREFIX -> spellLetters(group.value)
                GroupKind.LEADING_ZERO -> spellDigitByDigit(group.value)
                GroupKind.SINGLE -> spellByDigitWord(group.value)
                GroupKind.PLAIN -> spellPlain(group.value)
            }
            if (text.isNotEmpty()) parts.add(text)
        }

        return parts.joinToString(" ")
    }

    /**
     * FIX 5.8.11-e4-prefix-1:
     * Буквы — пробел между ними: «KPD» → «ка пэ дэ».
     * Одна буква — без пробела: «W» → «даблю».
     *
     * Vosk в грамматике учит эти же звуки отдельными словами,
     * поэтому TTS и распознавание работают симметрично.
     */
    private fun spellLetters(text: String): String {
        val parts = mutableListOf<String>()
        for (c in text) {
            val upper = c.uppercaseChar()
            val sound = letterToSound[upper] ?: continue
            parts.add(sound)
        }
        return parts.joinToString(" ")
    }

    private fun spellDigitByDigit(digits: String): String {
        val words = mutableListOf<String>()
        for (c in digits) {
            val name = digitNames[c] ?: continue
            words.add(name)
        }
        return words.joinToString(" ")
    }

    private fun spellByDigitWord(digit: String): String {
        val d = digit.toIntOrNull() ?: return digit
        return unitsMasculine[d]
    }

    private fun spellPlain(value: String): String {
        if (value.isEmpty()) return ""

        if (value.length > 1 && value.startsWith("0")) {
            return spellDigitByDigit(value)
        }

        if (value.length in 1..3) {
            val n = value.toIntOrNull() ?: return value
            return numberWords(n)
        }

        return spellPairsAsWords(value)
    }

    private fun spellPairsAsWords(digits: String): String {
        val words = mutableListOf<String>()
        var i = 0
        while (i < digits.length) {
            val end = minOf(i + 2, digits.length)
            val pair = digits.substring(i, end)

            val text = if (pair.length > 1 && pair.startsWith("0")) {
                spellDigitByDigit(pair)
            } else {
                val n = pair.toIntOrNull() ?: return digits
                numberWords(n)
            }
            words.add(text)
            i = end
        }
        return words.joinToString(" ")
    }

    // ================================================================
    // Старый API: произношение строки
    // ================================================================

    fun spellOut(text: String): String {
        if (text.isBlank()) return text

        val sb = StringBuilder()
        var i = 0

        while (i < text.length) {
            val c = text[i]

            if (c.isDigit()) {
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
                        sb.append(letterNames.getValue(upper))
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

    fun spellNumber(value: Int): String {
        return if (value in 0..9999) {
            value.toString()
        } else {
            spellOut(value.toString())
        }
    }

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

    fun plural(
        n: Int,
        one: String,
        few: String,
        many: String
    ): String {
        val abs = if (n < 0) -n else n
        val mod10 = abs % 10
        val mod100 = abs % 100

        return when {
            mod10 == 1 && mod100 != 11 -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
    }

    fun countWithNoun(
        n: Int,
        one: String,
        few: String,
        many: String,
        feminine: Boolean = false
    ): String {
        val abs = if (n < 0) -n else n
        val form = plural(abs, one, few, many)
        val lastDigit = abs % 10
        val lastTwo = abs % 100
        val useFeminine = feminine && lastDigit == 1 && lastTwo != 11

        val words = numberWords(abs, useFeminine)
        return "$words $form"
    }

    fun samples(n: Int): String =
        countWithNoun(n, "проба", "пробы", "проб", feminine = true)

    fun wells(n: Int): String =
        countWithNoun(n, "скважина", "скважины", "скважин", feminine = true)

    fun orders(n: Int): String =
        countWithNoun(n, "наряд", "наряда", "нарядов", feminine = false)

    fun records(n: Int): String =
        countWithNoun(n, "запись", "записи", "записей", feminine = true)

    fun errors(n: Int): String =
        countWithNoun(n, "ошибка", "ошибки", "ошибок", feminine = true)

    fun numberWords(
        value: Int,
        feminineLast: Boolean = false
    ): String {
        if (value < 0) {
            return "минус " + numberWords(-value, feminineLast)
        }

        if (value == 0) return "ноль"
        if (value >= 1000) return value.toString()

        val parts = mutableListOf<String>()

        val h = value / 100
        val rem = value % 100

        if (h > 0) {
            parts += hundreds[h]
        }

        if (rem > 0) {
            when {
                rem < 10 -> {
                    parts += if (feminineLast) unitsFeminine[rem] else unitsMasculine[rem]
                }

                rem < 20 -> {
                    parts += teens[rem - 10]
                }

                else -> {
                    val t = rem / 10
                    val u = rem % 10

                    parts += tens[t]

                    if (u > 0) {
                        parts += if (feminineLast) unitsFeminine[u] else unitsMasculine[u]
                    }
                }
            }
        }

        return parts.joinToString(" ")
    }

    fun spokenWeight(value: Double): String {
        if (!value.isFinite()) return "не понял вес"

        val cents = (value * 100.0).roundToInt()

        if (cents <= 0) return "ноль"

        val intPart = cents / 100
        val frac = cents % 100

        if (frac == 0) {
            return numberWords(intPart)
        }

        if (intPart == 1 && frac == 50) {
            return "полтора"
        }

        if (frac == 50) {
            return "${numberWords(intPart)} с половиной"
        }

        if (frac == 25) {
            return "${numberWords(intPart)} с четвертью"
        }

        if (frac % 10 == 0) {
            val tenths = frac / 10
            return "${numberWords(intPart)} и ${unitsMasculine[tenths]}"
        }

        if (frac < 10) {
            return "${numberWords(intPart)} и ноль ${unitsMasculine[frac]}"
        }

        return "${numberWords(intPart)} и ${numberWords(frac)}"
    }
}
