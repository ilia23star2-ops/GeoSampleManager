package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-b (SEARCH_MODEL §4.3–4.4):
 * Разбиение нормализованной строки на типизированные токены.
 *
 * Вход: сырая строка (ручная или распознанная Vosk).
 * Выход: список [QueryToken].
 *
 * Токенизатор не решает «команда это или поиск» — это задача парсера.
 * Он только грубо классифицирует: префикс / цифры / ординал /
 * служебное слово / разделитель / неизвестное.
 *
 * Слитные префиксы разделяются:
 *   «KPD1090031» → [Prefix("KPD"), Number("1090031")]
 *
 * Словари:
 *   - команды — из [VoiceDictionary.commands] (общий набор);
 *   - разделители — [DEFAULT_SEPARATORS] (и / запятая / , / ;);
 *   - ординалы — через [VoiceOrdinals.match];
 *   - русские числительные — через [VoiceNumberParser] (FIX 5.8.11-e4c).
 *
 * FIX 5.8.11-e4c:
 * Раньше слова «один», «сто девять», «пятнадцать двадцать четыре»
 * уходили в Unknown и поиск не работал. Теперь они распознаются как
 * [QueryToken.Number] — по кандидату с разделителем «|» сохраняется
 * исходная структура ввода (109 00 31 → три токена, а не один слитый).
 */
class QueryTokenizer(
    private val commandWords: Set<String> = VoiceDictionary.commands,
    private val separatorWords: Set<String> = DEFAULT_SEPARATORS,
    private val numberParser: VoiceNumberParser = VoiceNumberParser()
) {

    companion object {
        val DEFAULT_SEPARATORS: Set<String> = setOf("и", "запятая", ",", ";")

        private val LATIN_PREFIX_WITH_NUMBER = Regex("^([a-z]+)(\\d+)$")
        private val LATIN_ONLY = Regex("^[a-z]+$")
        private val DIGITS_ONLY = Regex("^\\d+$")
        private val SPLIT_WHITESPACE = Regex("\\s+")
    }

    /**
     * Нормализует строку и разбивает её на токены.
     * Возвращает пустой список для пустой строки.
     */
    fun tokenize(raw: String): List<QueryToken> {
        val normalized = QueryNormalizer.normalize(raw)
        if (normalized.isEmpty()) return emptyList()

        val result = mutableListOf<QueryToken>()
        val parts = normalized.split(SPLIT_WHITESPACE).filter { it.isNotBlank() }

        var i = 0
        while (i < parts.size) {
            val part = parts[i]

            // FIX 5.8.11-e4c: русские числительные.
            // Идём по словам слева направо и собираем максимальный
            // прогон числительных в одну фразу. Парсим через
            // VoiceNumberParser — он знает про «сто девять» → 109,
            // «пятнадцать двадцать четыре» → 1524 и т.п.
            //
            // Если среди кандидатов есть вариант с «|» — используем
            // его: разбиваем по «|» и выпускаем несколько Number-токенов,
            // чтобы DigitGrouper сохранил структуру ввода.
            if (isRussianNumeral(part)) {
                val run = mutableListOf<String>()
                var j = i
                while (j < parts.size && isRussianNumeral(parts[j])) {
                    run.add(parts[j])
                    j++
                }
                val phrase = run.joinToString(" ")
                val parsed = numberParser.parse(phrase)
                val structured = parsed.candidates.firstOrNull { it.contains("|") }
                val chosen = structured ?: parsed.primary

                if (!chosen.isNullOrBlank()) {
                    val chunks = chosen.split("|").filter { it.isNotBlank() }
                    for (chunk in chunks) {
                        result.add(QueryToken.Number(chunk, phrase))
                    }
                    i = j
                    continue
                }
                // Не получилось — падаем на стандартную обработку.
            }

            appendTokens(part, result)
            i++
        }

        return result
    }

    /**
     * FIX 5.8.11-e4c: слово — русское количественное числительное?
     *
     * Проверяет по словарям VoiceDictionary. Порядковые («первая»)
     * сюда НЕ входят — они обрабатываются через VoiceOrdinals в
     * appendTokens.
     */
    private fun isRussianNumeral(word: String): Boolean =
        word in VoiceDictionary.singleDigits ||
                word in VoiceDictionary.teens ||
                word in VoiceDictionary.tens ||
                word in VoiceDictionary.hundreds ||
                word in VoiceDictionary.zeroWords

    private fun appendTokens(part: String, out: MutableList<QueryToken>) {
        // 1) Латинский префикс + число слитно: «kpd1090031»
        if (LATIN_PREFIX_WITH_NUMBER.matches(part)) {
            val match = LATIN_PREFIX_WITH_NUMBER.find(part)!!
            val prefix = match.groupValues[1].uppercase()
            val number = match.groupValues[2]
            out.add(QueryToken.Prefix(prefix, part))
            out.add(QueryToken.Number(number, part))
            return
        }

        // 2) Только латиница: «kpd» / «nv»
        if (LATIN_ONLY.matches(part)) {
            out.add(QueryToken.Prefix(part.uppercase(), part))
            return
        }

        // 3) Только цифры: «1524» / «109» / «31»
        if (DIGITS_ONLY.matches(part)) {
            out.add(QueryToken.Number(part, part))
            return
        }

        // 4) Порядковое числительное: «первая» → 1
        val ord = VoiceOrdinals.match(part)
        if (ord != null) {
            out.add(QueryToken.Ordinal(ord, part))
            return
        }

        // 5) Служебное слово команды: «стоп» / «отмена»
        if (part in commandWords) {
            out.add(QueryToken.CommandWord(part, part))
            return
        }

        // 6) Разделитель: «и» / «запятая»
        if (part in separatorWords) {
            out.add(QueryToken.Separator(part, part))
            return
        }

        // 7) Ничего не подошло — Unknown
        out.add(QueryToken.Unknown(part))
    }
}
