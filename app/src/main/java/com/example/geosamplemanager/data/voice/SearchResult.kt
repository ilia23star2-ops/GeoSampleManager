package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-d (SEARCH_MODEL §6.2):
 * Единый результат поиска. Заменяет UnifiedSearchResult на верхнем
 * уровне — с добавлением структуры запроса.
 *
 * Ключевое отличие от UnifiedSearchResult: хранит **и попадания,
 * и структуру запроса** (groups + queryTokens). Это позволяет
 * презентеру построить UI и TTS из одного объекта, не теряя
 * исходную структуру номера при озвучке.
 *
 * Строится SearchService из UnifiedSearchResult + входных данных.
 */
sealed interface SearchResult {

    /**
     * Что-то нашли.
     *
     * @param hits          пробы, совпавшие с запросом
     * @param matchedKind   тип совпадения: WELL / SAMPLE / NONE
     * @param matchedValue  что именно нашли: «NV1524» / «NV152401»
     * @param level         уровень совпадения 0..6 (см. UnifiedSearch)
     * @param isUnique      один наряд в попаданиях?
     * @param groups        структура ввода для озвучки
     * @param queryTokens   исходные токены (для презентера)
     */
    data class Found(
        val hits: List<VoiceSampleHit>,
        val matchedKind: UnifiedMatchKind,
        val matchedValue: String,
        val level: Int,
        val isUnique: Boolean,
        val groups: List<DigitGroup> = emptyList(),
        val queryTokens: List<QueryToken> = emptyList()
    ) : SearchResult

    /**
     * Ничего не нашли. Номера нет в БД.
     */
    data object NotFound : SearchResult

    /**
     * Ошибка поиска: исключение, недоступный источник и т.п.
     * Отличается от NotFound только логом — пользователю показывается
     * как «не найдено».
     */
    data class Failed(val error: String) : SearchResult

    // ================================================================
    // Утилиты
    // ================================================================

    val isFound: Boolean get() = this is Found
    val isNotFound: Boolean get() = this is NotFound || this is Failed

    /** Первое попадание (если есть). */
    val firstHit: VoiceSampleHit?
        get() = (this as? Found)?.hits?.firstOrNull()
}
