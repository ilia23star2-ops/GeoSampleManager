package com.example.geosamplemanager.data.voice

import kotlin.math.roundToInt

/**
 * Произношение номеров, весов и счётных фраз для TTS.
 *
 * Основная идея:
 * - длинные номера диктуются парами цифр: «15 24 01»;
 * - веса произносятся по-человечески: «два и шесть», «два с половиной»;
 * - счётные фразы используют русские склонения: «1 проба», «2 пробы», «5 проб».
 *
 * FIX 5.8.11-e4e-a (мимикрия):
 * - spellOut(groups) — без запятых, префикс одним словом;
 * - KPD → «капэдэ», W → «даблю».
 *
 * FIX 5.8.11-e4-markers-2/6:
 * - новый метод spellMimicry(text) — мимикрия для произвольной
 *   строки-номера (sampleNumber, wellNumber). Внутри — токенизация
 *   и группировка, потом spellOut(groups).
 * - Единая озвучка везде: и в voiceSearch, и в ответах ГП, и в
 *   UI-фолбэках.
 */
object VoiceSpeaker {

    private val letterToSound: Map<Char, String> = mapOf(
        'A' to "а",
        'B' to "бэ",
        'C' to "цэ",
        'D' to "дэ",
        'E' to "е",
        'F' to "эф",
        'G' to "гэ",
        'H' to "аш",
        'I' to "и",
        'J' to "жэ",
        'K' to "ка",
        'L' to "эль",
        'M' to "эм",
        'N' to "эн",
        'O' to "о",
        'P' to "пэ",
        'Q' to "ку",
        'R' to "эр",
        'S' to "эс",
        'T' to "тэ",
        'U' to "у",
        'V' to "вэ",
        'W' to "даблю",
        'X' to "икс",
        'Y' to "игрек",
        'Z' to "зэт"
    )

    private val letterNames: Map<Char, String> = mapOf(
        'A' to "а",
        'B' to "бэ",
        'C' to "цэ",
        'D' to "дэ",
        'E' to "е",
        'F' to "эф",
        'G' to "жэ",
        'H' to "аш",
        'I' to "и",
        'J' to "йот",
        'K' to "ка",
        'L' to "эль",
        'M' to "эм",
        'N' to "эн",
        'O' to "о",
        'P' to "пэ",
        'Q' to "ку",
        'R' to "эр",
        'S' to "эс",
        'T' to "тэ",
        'U' to "у",
        'V' to "вэ",
        'W' to "дубль-вэ",
        'X' to "икс",
        'Y' to "игрек",
        'Z' to "зэт"
    )

    private val digitNames: Map<Char, String> = mapOf(
        '0' to "ноль",
        '1' to "один",
        '2' to "два",
        '3' to "три",
        '4' to "четыре",
        '5' to "пять",
        '6' to "шесть",
        '7' to "семь",
        '8' to "восемь",
        '9' to "девять"
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
    // FIX 5.8.11-e4-markers-2/6: мимикрия для строки-номера
    // ================================================================

    /**
     * Произнести номер скважины или пробы «как человек».
     *
     * Единый путь для всех мест, где номер приходит строкой из БД
     * (sampleNumber, wellNumber), а не через VoiceNumberParser.
     *
     * Внутри:
     *   1. токенизация строки (QueryTokenizer);
     *   2. группировка токенов (DigitGrouper);
     *   3. произношение по группам (spellOut(groups)).
     *
     * Примеры:
     *   "NV136602"   → «энвэ тринадцать шестьдесят шесть ноль два»
     *   "KPD1090031" → «капэдэ сто девять ноль ноль тридцать один»
     *   "1524"       → «пятнадцать двадцать четыре»
     *   ""           → ""
     *   "—"          → "—"
     *
     * Fallback: если токенизация/группировка не удались — старый
     * spellOut(text) (по буквам и парам цифр).
     */
    fun spellMimicry(text: String): String {
        if (text.isBlank()) return text

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

    // ================================================================
    // Произношение по группам
    // ================================================================

    /**
     * Произнести номер по группам ввода.
     *
     * Правила:
     *   - PREFIX       → одним словом: «KPD» → «капэдэ».
     *   - PLAIN, 1–3   → число словами: «109» → «сто девять».
     *   - PLAIN, 4+    → по парам слева, каждая пара словами.
     *   - PLAIN с ведущим нулём → по цифрам.
     *   - LEADING_ZERO → по цифрам.
     *   - SINGLE       → цифра словом.
     *   - Между группами — пробел (без запятых).
     */
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

    private fun spellLetters(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            val upper = c.uppercaseChar()
            val sound = letterToSound[upper] ?: continue
            sb.append(sound)
        }
        return sb.toString()
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

    /**
     * Произнести номер: буквы — по буквам, цифры — парами через пробел.
     *
     * «NV1524» → «эн вэ 15 24»
     * «KPD1090031» → «ка пэ дэ 10 90 03 1»
     * «1524» → «15 24»
     *
     * Для мимикрии используйте [spellMimicry].
     */
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

    // ================================================================
    // Остальной API — без изменений
    // ================================================================

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
