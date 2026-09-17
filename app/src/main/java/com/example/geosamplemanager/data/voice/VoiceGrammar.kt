package com.example.geosamplemanager.data.voice

import com.google.gson.Gson

/**
 * Сборщик JSON-грамматики для Vosk.
 *
 * Грамматика — это список слов, которые Vosk может распознать.
 * Всё, что не в списке, — не распознаётся (заменяется на [unk]).
 *
 * Зачем: маленькая модель Vosk путается между похожими словами
 * («ноля» → «нала» → «налёт»). Ограничение словаря резко повышает
 * точность на числах.
 */
object VoiceGrammar {

    /**
     * FIX 5.8.9g-3: явные слова команд, которых нет в общем словаре,
     * но которые Vosk должен распознавать без подмены.
     *
     * FIX 5.8.9f-1b: добавлены слова для переключения режима —
     * «сортировка» / «поиск» / «режим». Без них Vosk подменяет
     * ближайшими.
     */
    private val explicitCommandWords = listOf(
        // 5.8.9g-3
        "все",
        "отметь",
        "отметьте",
        "отметить",
        // 5.8.9f-1b — переключение режима
        "сортировка",
        "сортировки",
        "сортировку",
        "поиск",
        "режим"
    )

    /**
     * Собрать грамматику.
     *
     * @param extra дополнительные слова (например, пользовательские
     *              префиксы участков).
     */
    fun build(extra: Collection<String> = emptyList()): String {
        val words = LinkedHashSet<String>(512)

        // Числительные — все формы.
        words.addAll(VoiceDictionary.singleDigits.keys)
        words.addAll(VoiceDictionary.teens.keys)
        words.addAll(VoiceDictionary.tens.keys)
        words.addAll(VoiceDictionary.hundreds.keys)
        words.addAll(VoiceDictionary.thousandWords)
        words.addAll(VoiceDictionary.millionWords)
        words.addAll(VoiceDictionary.zeroWords)

        // Порядковые (первая … тридцатая).
        words.addAll(VoiceOrdinals.map.keys)

        // Разделители.
        words.addAll(VoiceDictionary.separators)

        // Команды.
        words.addAll(VoiceDictionary.commands)

        // FIX 5.8.9g-3 + 5.8.9f-1b: явные слова.
        words.addAll(explicitCommandWords)

        // Дополнительные слова от вызывающей стороны.
        words.addAll(extra)

        // Обязательный маркер «неизвестное слово».
        // Без него Vosk пытается «впихнуть» шум в наше слово.
        words.add("[unk]")

        return Gson().toJson(words.toList())
    }
}