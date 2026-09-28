package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-multi-query:
 * Разбиение токенов запроса на отдельные запросы.
 *
 * Логика:
 *  1. Сначала по явному разделителю (Separator: «и», запятая, «;»).
 *  2. Если разделителей нет, но в одном блоке 2+ Number с длиной >= 4
 *     (или Number после Prefix) — авто-разделение. Каждый длинный
 *     Number становится отдельным запросом.
 *
 * Зачем: пользователь пишет «1366 1367» без «и» — ждёт два запроса.
 * А «109 00 31» — это ОДИН запрос (KPD1090031, разбитый по группам).
 *
 * Примеры:
 *   «1366 1367»           → [[Number(1366)], [Number(1367)]]
 *   «1090031 1090032»     → [[Number(1090031)], [Number(1090032)]]
 *   «1366 1090031»        → [[Number(1366)], [Number(1090031)]]
 *   «KPD1090031 NV1366»   → [[Prefix, 1090031], [Prefix, 1366]]
 *   «109 00 31»           → [[Number(109), Number(00), Number(31)]]
 *   «15 24»               → [[Number(15), Number(24)]]
 *   «1366 и 1367»         → [[Number(1366)], [Number(1367)]]
 */
object QuerySplitter {

    /**
     * Минимальная длина Number, чтобы считать его самостоятельным
     * запросом при авто-разделении. Короткие числа (1–3 цифры)
     * могут быть частью одного длинного номера.
     */
    private const val LONG_NUMBER_THRESHOLD = 4

    /**
     * Минимальное количество длинных Number, чтобы запустить
     * авто-разделение.
     */
    private const val MIN_LONG_NUMBERS = 2

    fun splitIntoRequests(tokens: List<QueryToken>): List<List<QueryToken>> {
        if (tokens.isEmpty()) return emptyList()

        val bySeparator = splitBySeparators(tokens)

        if (bySeparator.size >= 2) return bySeparator

        val single = bySeparator.firstOrNull() ?: return emptyList()

        val numbers = single.filterIsInstance<QueryToken.Number>()
        val longNumbers = numbers.filter { it.value.length >= LONG_NUMBER_THRESHOLD }

        if (longNumbers.size < MIN_LONG_NUMBERS) {
            return bySeparator
        }

        return splitSingleIntoNumbers(single)
    }

    /**
     * Классическое разбиение по Separator.
     */
    private fun splitBySeparators(tokens: List<QueryToken>): List<List<QueryToken>> {
        val result = mutableListOf<List<QueryToken>>()
        val current = mutableListOf<QueryToken>()

        for (t in tokens) {
            if (t is QueryToken.Separator) {
                if (current.isNotEmpty()) {
                    result.add(current.toList())
                    current.clear()
                }
            } else {
                current.add(t)
            }
        }
        if (current.isNotEmpty()) result.add(current.toList())

        return result
    }

    /**
     * Авто-разделение: каждый Number становится отдельным запросом.
     * Если перед Number был Prefix — он приклеивается к своему Number.
     *
     * Всё, что не Prefix и не Number (Ordinal, CommandWord, Unknown) —
     * не учитываем при авто-разделении, но возвращаем как есть
     * (в контексте одного Number, если он есть).
     */
    private fun splitSingleIntoNumbers(tokens: List<QueryToken>): List<List<QueryToken>> {
        val result = mutableListOf<List<QueryToken>>()
        var prefixBuffer: QueryToken.Prefix? = null
        var otherBuffer: MutableList<QueryToken> = mutableListOf()

        for (t in tokens) {
            when (t) {
                is QueryToken.Prefix -> {
                    prefixBuffer = t
                }
                is QueryToken.Number -> {
                    val item = mutableListOf<QueryToken>()
                    otherBuffer.forEach { item.add(it) }
                    otherBuffer.clear()
                    prefixBuffer?.let { item.add(it) }
                    item.add(t)
                    result.add(item)
                    prefixBuffer = null
                }
                else -> {
                    otherBuffer.add(t)
                }
            }
        }

        // Остаток без Number — присоединим к последнему запросу, если есть.
        if (otherBuffer.isNotEmpty() && result.isNotEmpty()) {
            val last = result.last().toMutableList()
            last.addAll(otherBuffer)
            result[result.size - 1] = last
        } else if (otherBuffer.isNotEmpty()) {
            result.add(otherBuffer.toList())
        }

        return result
    }
}