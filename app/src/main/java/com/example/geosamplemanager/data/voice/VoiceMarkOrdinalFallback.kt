package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-e4-pin-7:
 * Подсказки при промахе по номеру пробы.
 *
 * Vosk путает порядковые с общим корнем «-надцат» / «-десят» / «-сот»:
 *   «четвёртая»  ↔ «четырнадцатая»    (4 ↔ 14)
 *   «четвёртая»  ↔ «сороковая»        (4 ↔ 40)
 *   «четвёртая»  ↔ «четырёхсотая»     (4 ↔ 400)
 *   «четвёртая»  ↔ «четырёхтысячная»  (4 ↔ 4000)
 * То же для 5..9: 15/50/500/5000, 16/60/600/6000, …, 19/90/900/9000.
 *
 * Раньше (pin-5/pin-6) здесь жил fallback: увидели «14», отметили «4».
 * Это молчаливое предположение — иногда неверное.
 *
 * Теперь вместо fallback — подсказка пользователю: «Пробы №14 нет.
 * Если нужна №4 — скажите „четыре"». Количественные числа Vosk
 * распознаёт чётко, поэтому уточнение числом работает надёжно.
 *
 * Объект остался с прежним именем (VoiceMarkOrdinalFallback) — чтобы
 * не плодить git-переименования. По смыслу теперь это «hints».
 */
object VoiceMarkOrdinalFallback {

    /**
     * Все «спорные» двойники числа ordinal, по приоритету.
     * Может быть пустым.
     *
     * Примеры:
     *   candidatesFor(14)   → [4]
     *   candidatesFor(4)    → [14, 40, 400, 4000]
     *   candidatesFor(400)  → [4]
     *   candidatesFor(25)   → []
     */
    fun candidatesFor(ordinal: Int): List<Int> {
        val result = mutableListOf<Int>()

        // 14 ↔ 4, 15 ↔ 5, …, 19 ↔ 9.
        if (ordinal in 10..19) {
            result.add(ordinal - 10)
        }
        // 4 ↔ 14, 5 ↔ 15, …, 9 ↔ 19.
        if (ordinal in 1..9) {
            result.add(ordinal + 10)
        }

        // 4 ↔ 40, 5 ↔ 50, …, 9 ↔ 90.
        if (ordinal in 1..9) {
            result.add(ordinal * 10)
        }
        // 40 ↔ 4, 50 ↔ 5, …, 90 ↔ 9.
        if (ordinal in 40..90 && ordinal % 10 == 0) {
            result.add(ordinal / 10)
        }

        // 4 ↔ 400, 5 ↔ 500, …, 9 ↔ 900.
        if (ordinal in 1..9) {
            result.add(ordinal * 100)
        }
        // 400 ↔ 4, 500 ↔ 5, …, 900 ↔ 9.
        if (ordinal in 400..900 && ordinal % 100 == 0) {
            result.add(ordinal / 100)
        }

        // 4 ↔ 4000, 5 ↔ 5000, …, 9 ↔ 9000.
        if (ordinal in 1..9) {
            result.add(ordinal * 1000)
        }
        // 4000 ↔ 4, 5000 ↔ 5, …, 9000 ↔ 9.
        if (ordinal in 4000..9000 && ordinal % 1000 == 0) {
            result.add(ordinal / 1000)
        }

        return result.distinct()
    }

    /**
     * Какое число пользователь, вероятно, имел в виду.
     *
     * Возвращает первого кандидата из [candidatesFor] или null, если
     * число не «спорное» (например, 25) — тогда подсказки нет.
     */
    fun hintFor(ordinal: Int): Int? = candidatesFor(ordinal).firstOrNull()
}
