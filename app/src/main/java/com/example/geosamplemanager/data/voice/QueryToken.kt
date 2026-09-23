package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-b (SEARCH_MODEL §4.3):
 * Тип токена после нормализации.
 *
 * Ручной и голосовой ввод дают одинаковый набор токенов. Отличие —
 * только в источнике текста (строка vs распознанная речь).
 *
 * Токены используются:
 *   - на этапе построения кандидатов (QueryToCandidates);
 *   - парсером для решения «команда или поиск» (заход 5.8.11-e).
 */
sealed interface QueryToken {

    /** Исходное слово, как оно пришло после нормализации. */
    val raw: String

    /**
     * Латинский префикс участка: `KPD`, `NV`, `ACD`.
     * Может идти отдельно (`KPD 109 00 31`) или слитно (`KPD1090031`).
     * При слитном написании даёт два токена: Prefix + Number.
     */
    data class Prefix(
        val value: String,
        override val raw: String
    ) : QueryToken

    /**
     * Числовая группа. Только цифры после нормализации.
     * `«1524»`, `«109»`, `«31»`.
     *
     * Слова («семь», «сто девять») сюда не попадают — они идут
     * как [Unknown] и конвертируются в числа отдельным шагом
     * (`VoiceNumberParser`), если это нужно для поиска.
     */
    data class Number(
        val value: String,
        override val raw: String
    ) : QueryToken

    /**
     * Порядковое числительное, распознанное через [VoiceOrdinals].
     * `«первая»` → 1, `«третья»` → 3, `«двадцать первая»` → 21.
     */
    data class Ordinal(
        val value: Int,
        override val raw: String
    ) : QueryToken

    /**
     * Служебное слово команды из [VoiceDictionary.commands].
     * `«стоп»`, `«отмена»`, `«продолжить»`.
     *
     * Полное распознавание — задача парсера, а не токенизатора.
     * Здесь только грубая отметка «это команда».
     */
    data class CommandWord(
        val value: String,
        override val raw: String
    ) : QueryToken

    /**
     * Разделитель между запросами: `«и»`, `«запятая»`, `,`, `;`.
     * Используется для сортировки (несколько номеров подряд).
     */
    data class Separator(
        val value: String,
        override val raw: String
    ) : QueryToken

    /**
     * Слово, не подошедшее ни под одну категорию.
     * `«семья»`, `«утра»`, `«холодно»`.
     *
     * Правило: если во фразе есть [Unknown] в середине —
     * вся фраза считается [Unknown] целиком.
     */
    data class Unknown(override val raw: String) : QueryToken
}
