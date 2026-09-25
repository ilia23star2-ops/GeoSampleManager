package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-e4-pin-5:
 * Подбор альтернативного номера пробы, если Vosk распознал порядковый
 * номер неверно.
 *
 * Vosk путает:
 *   «четвёртая»  ↔ «четыреста»        (4 ↔ 400)
 *   «четвёртая»  ↔ «сорок»            (4 ↔ 40)
 *   «четвёртая»  ↔ «четырнадцатая»    (4 ↔ 14)
 *
 * FIX 5.8.11-e4-pin-6:
 * Рядом добавлена extension-функция [isSubstituted] для
 * VoiceExecResult.Marked — чтобы UI и озвучка могли явно показать
 * подмену пользователю.
 */
object VoiceMarkOrdinalFallback {

    /**
     * Список альтернативных номеров в порядке приоритета.
     * Может быть пустым.
     */
    fun candidatesFor(ordinal: Int): List<Int> {
        val result = mutableListOf<Int>()

        // 1) 14 ↔ 4: диапазон 10..19 → минус 10.
        if (ordinal in 10..19) {
            result.add(ordinal - 10)
        }
        // 1..9 → плюс 10.
        if (ordinal in 1..9) {
            result.add(ordinal + 10)
        }

        // 2) Круглые сотни: 400, 500, …, 900 → 4, 5, …, 9.
        if (ordinal in 400..900 && ordinal % 100 == 0) {
            result.add(ordinal / 100)
        }

        // 3) Круглые десятки: 40, 50, …, 90 → 4, 5, …, 9.
        if (ordinal in 40..90 && ordinal % 10 == 0) {
            result.add(ordinal / 10)
        }

        // 4) Круглые тысячи: 4000, 5000, …, 9000 → 4, 5, …, 9.
        if (ordinal in 4000..9000 && ordinal % 1000 == 0) {
            result.add(ordinal / 1000)
        }

        return result
    }

    /**
     * Найти подходящий номер пробы.
     */
    fun resolve(ordinal: Int, hasOrdinal: (Int) -> Boolean): Int? {
        if (hasOrdinal(ordinal)) return ordinal
        for (alt in candidatesFor(ordinal)) {
            if (hasOrdinal(alt)) return alt
        }
        return null
    }
}

/**
 * FIX 5.8.11-e4-pin-6:
 * Была ли подмена номера при отметке.
 *
 * true  — Vosk услышал один номер, а отметилась проба с другим
 *         (сработал fallback).
 * false — отметили ровно ту пробу, которую услышали.
 *
 * Используется в UI (VoiceDialog.describeResult) и в озвучке
 * (VoiceDialog.handleFeedback): при true добавляется префикс
 * «Распознано X → Y.»
 */
fun VoiceExecResult.Marked.isSubstituted(): Boolean =
    recognizedOrdinal != null && recognizedOrdinal != ordinal
