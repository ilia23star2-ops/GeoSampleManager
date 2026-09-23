package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-d (SEARCH_MODEL §6):
 * Единый сервис поиска. Только in-memory (см. SEARCH_MODEL §6.1).
 *
 * Загружает все пробы через VoiceSampleSource.loadAll() и ищет через
 * UnifiedSearch. SQL-путь для поиска не используется — решено в
 * спецификации. Если реально упрёмся — добавим позже, отдельным заходом.
 *
 * Сервис **не решает**, что показать. Это работа презентера (заход d2).
 * Он возвращает SearchResult — единый объект со всей информацией.
 *
 * Использование:
 *   val service = SearchService(VoiceSearchRepository(context))
 *   val result = service.search(candidates, groups, tokens)
 *
 * Source передаётся снаружи — так проще тестировать (mock-источник).
 */
class SearchService(
    private val source: VoiceSampleSource
) {

    /**
     * Выполнить поиск по кандидатам.
     *
     * @param candidates   список строк-кандидатов (от GroupToCandidates)
     * @param groups       структура ввода для озвучки
     * @param queryTokens  исходные токены (для презентера)
     * @param scope        если задан — используется вместо loadAll()
     */
    suspend fun search(
        candidates: List<String>,
        groups: List<DigitGroup> = emptyList(),
        queryTokens: List<QueryToken> = emptyList(),
        scope: List<VoiceSampleHit>? = null
    ): SearchResult {
        if (candidates.isEmpty()) return SearchResult.NotFound

        return try {
            val all = scope ?: source.loadAll()
            if (all.isEmpty()) return SearchResult.NotFound

            val unified = UnifiedSearch.search(all, candidates, filterMode = false)

            when (unified) {
                UnifiedSearchResult.NotFound -> SearchResult.NotFound

                is UnifiedSearchResult.Found -> SearchResult.Found(
                    hits = unified.hits,
                    matchedKind = unified.matchedKind,
                    matchedValue = unified.matchedValue,
                    level = unified.level,
                    isUnique = unified.isUnique,
                    groups = groups,
                    queryTokens = queryTokens
                )
            }
        } catch (e: Exception) {
            SearchResult.Failed(e.message ?: "search failed")
        }
    }
}
