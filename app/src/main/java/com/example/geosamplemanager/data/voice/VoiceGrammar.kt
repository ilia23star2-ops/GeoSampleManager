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

        // Дополнительные слова от вызывающей стороны.
        words.addAll(extra)

        // Обязательный маркер «неизвестное слово».
        // Без него Vosk пытается «впихнуть» шум в наше слово.
        words.add("[unk]")

        return Gson().toJson(words.toList())
    }
}