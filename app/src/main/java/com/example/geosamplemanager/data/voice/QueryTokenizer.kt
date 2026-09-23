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
 *   - ординалы — через [VoiceOrdinals.match].
 */
class QueryTokenizer(
    private val commandWords: Set<String> = VoiceDictionary.commands,
    private val separatorWords: Set<String> = DEFAULT_SEPARATORS
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

        for (part in parts) {
            appendTokens(part, result)
        }

        return result
    }

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
