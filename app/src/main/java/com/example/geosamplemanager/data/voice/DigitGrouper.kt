package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-c (SEARCH_MODEL §5.3):
 * Группировка токенов в [DigitGroup].
 *
 * Вход: список [QueryToken] после токенизации.
 * Выход: список [DigitGroup] — сохранённая структура ввода.
 *
 * Правила:
 *   1. Number длиной 2+ → новая группа PLAIN.
 *   2. Number длиной 1 (не ноль) → продолжение текущей группы.
 *      «1 5 2 4» → одна группа «1524».
 *   3. Два и более Number("0") подряд → LEADING_ZERO.
 *      «0 0» → группа «00», «0 0 0» → группа «000».
 *   4. Одиночный Number("0") → продолжение текущей группы.
 *   5. Prefix → отдельная группа PREFIX. Прерывает текущую PLAIN.
 *   6. Ordinal, CommandWord, Separator, Unknown → прерывают текущую
 *      группу и сами в результат не попадают (обрабатываются парсером).
 *
 * Примеры:
 *   [15, 24, 01]      → [PLAIN("15"), PLAIN("24"), PLAIN("01")]
 *   [109, 0, 0, 31]   → [PLAIN("109"), LEADING_ZERO("00"), PLAIN("31")]
 *   [1, 5, 2, 4]      → [PLAIN("1524")]
 *   [Prefix(KPD), 1090031] → [PREFIX("KPD"), PLAIN("1090031")]
 */
object DigitGrouper {

    fun group(tokens: List<QueryToken>): List<DigitGroup> {
        if (tokens.isEmpty()) return emptyList()

        val result = mutableListOf<DigitGroup>()
        val current = StringBuilder()
        var currentIsSingle = false

        fun flush() {
            if (current.isNotEmpty()) {
                val value = current.toString()
                val kind = if (currentIsSingle && value.length == 1) {
                    GroupKind.SINGLE
                } else {
                    GroupKind.PLAIN
                }
                result.add(DigitGroup(value = value, kind = kind))
                current.clear()
                currentIsSingle = false
            }
        }

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when (token) {
                is QueryToken.Prefix -> {
                    flush()
                    result.add(
                        DigitGroup(value = token.value, kind = GroupKind.PREFIX)
                    )
                }

                is QueryToken.Number -> {
                    val v = token.value

                    // Случай группы нулей: Number("0") подряд 2+ раза.
                    if (v == "0") {
                        var zeroCount = 1
                        var j = i + 1
                        while (j < tokens.size) {
                            val next = tokens[j]
                            if (next is QueryToken.Number && next.value == "0") {
                                zeroCount++
                                j++
                            } else break
                        }

                        if (zeroCount >= 2) {
                            flush()
                            result.add(
                                DigitGroup(
                                    value = "0".repeat(zeroCount),
                                    kind = GroupKind.LEADING_ZERO
                                )
                            )
                            i = j
                            continue
                        } else {
                            // Одиночный ноль — часть текущей группы.
                            if (current.isEmpty()) currentIsSingle = true
                            current.append("0")
                        }
                    } else if (v.length == 1) {
                        // Одиночная цифра (1..9) — продолжение текущей.
                        if (current.isEmpty()) currentIsSingle = true
                        current.append(v)
                    } else {
                        // 2+ цифр — новая группа PLAIN.
                        flush()
                        current.append(v)
                        currentIsSingle = false
                    }
                }

                is QueryToken.Ordinal,
                is QueryToken.CommandWord,
                is QueryToken.Separator,
                is QueryToken.Unknown -> {
                    // Прерывают текущую группу. Сами не входят.
                    flush()
                }
            }
            i++
        }

        flush()
        return result
    }
}
