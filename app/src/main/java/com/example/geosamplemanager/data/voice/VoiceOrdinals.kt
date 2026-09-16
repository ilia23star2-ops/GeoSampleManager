package com.example.geosamplemanager.data.voice

/**
 * Порядковые числительные 1..30.
 *
 * Все формы, которые можно услышать: «первая», «первый», «первое»,
 * «первую», «двадцать первая», «тридцатая».
 *
 * Особый случай — число 3. Формы «третья/третий/третье/третью»
 * не сводятся к «треть» + суффикс, поэтому они добавлены явно.
 */
object VoiceOrdinals {

    private val bases = listOf(
        1 to "перв",
        2 to "втор",
        // 3 — особые формы, см. ниже.
        4 to "четверт",
        5 to "пят",
        6 to "шест",
        7 to "седьм",
        8 to "восьм",
        9 to "девят",
        10 to "десят",
        11 to "одиннадцат",
        12 to "двенадцат",
        13 to "тринадцат",
        14 to "четырнадцат",
        15 to "пятнадцат",
        16 to "шестнадцат",
        17 to "семнадцат",
        18 to "восемнадцат",
        19 to "девятнадцат",
        20 to "двадцат"
    )

    private val suffixes = listOf("ая", "ый", "ое", "ую")

    val map: Map<String, Int> = buildMap {
        for ((i, base) in bases) {
            for (suffix in suffixes) {
                put("$base$suffix", i)
            }
        }

        // 3 — особые формы.
        put("третья", 3)
        put("третий", 3)
        put("третье", 3)
        put("третью", 3)

        // 21..30 — составные.
        put("двадцать первая", 21); put("двадцать первый", 21)
        put("двадцать второе", 22); put("двадцать вторая", 22); put("двадцать второй", 22)
        put("двадцать третья", 23); put("двадцать третий", 23); put("двадцать третье", 23)
        put("двадцать четвертая", 24); put("двадцать четвертый", 24)
        put("двадцать пятая", 25); put("двадцать пятый", 25)
        put("двадцать шестая", 26); put("двадцать шестой", 26)
        put("двадцать седьмая", 27); put("двадцать седьмой", 27)
        put("двадцать восьмая", 28); put("двадцать восьмой", 28)
        put("двадцать девятая", 29); put("двадцать девятый", 29)
        put("тридцатая", 30); put("тридцатый", 30)
    }

    /**
     * Ищет самое длинное совпадение с текстом.
     * Возвращает номер или null.
     */
    fun match(normalizedText: String): Int? {
        var best: Int? = null
        var bestLen = 0
        for ((word, value) in map) {
            if (word.length > bestLen && normalizedText.contains(word)) {
                best = value
                bestLen = word.length
            }
        }
        return best
    }

    /**
     * FIX 5.8.9g-1: находит ВСЕ порядковые в тексте слева направо.
     *
     * Работает жадно: на каждой позиции берём самое длинное
     * совпадение, сдвигаемся за него, продолжаем.
     *
     * Примеры:
     *   «пятая шестая седьмая» → [5, 6, 7]
     *   «двадцать первая двадцать третья» → [21, 23]
     *   «первая» → [1]
     *   «привет» → []
     */
    fun matchAll(normalizedText: String): List<Int> {
        if (normalizedText.isEmpty()) return emptyList()
        val result = mutableListOf<Int>()
        var i = 0
        while (i < normalizedText.length) {
            var bestValue: Int? = null
            var bestEnd = i
            for ((word, value) in map) {
                if (normalizedText.startsWith(word, i)) {
                    val end = i + word.length
                    // Границы слова: слева либо начало, либо не-буква;
                    // справа либо конец, либо не-буква.
                    val beforeOk = i == 0 || !normalizedText[i - 1].isLetter()
                    val afterOk = end == normalizedText.length ||
                            !normalizedText[end].isLetter()
                    if (beforeOk && afterOk && end > bestEnd) {
                        bestValue = value
                        bestEnd = end
                    }
                }
            }
            if (bestValue != null) {
                result.add(bestValue)
                i = bestEnd
            } else {
                i++
            }
        }
        return result
    }
}