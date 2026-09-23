package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-c (SEARCH_MODEL §5.4):
 * Построение кандидатов для поиска из [DigitGroup].
 *
 * Вход: список групп (сохранённая структура ввода).
 * Выход: список строк-кандидатов в порядке приоритета.
 *
 * Правила:
 *   1. Слитно — все цифры без разделителей: «1090031».
 *   2. По группам — разделитель «|»: «109|00|31».
 *   3. По парам справа — «1|09|00|31» (классическая советская запись).
 *
 * Префикс — отдельная группа. Учитывается во всех вариантах:
 *   [PREFIX(KPD), 109, 00, 31] → [KPD1090031, KPD109|00|31, KPD|109|00|31]
 *
 * Порядок: первый кандидат — самый «точный по вводу», остальные —
 * альтернативы. UnifiedSearch пробует их по порядку.
 */
object GroupToCandidates {

    private const val SEP = "|"

    fun toCandidates(groups: List<DigitGroup>): List<String> {
        if (groups.isEmpty()) return emptyList()

        val prefix = groups.filter { it.kind == GroupKind.PREFIX }
            .joinToString("") { it.value }
        val digits = groups.filter { it.kind != GroupKind.PREFIX }

        if (digits.isEmpty()) {
            // Только префикс — это не кандидат поиска.
            return emptyList()
        }

        val result = LinkedHashSet<String>()

        // 1) Слитно по группам (без «|» между группами).
        //    «109 00 31» → «1090031».
        //    «15 24 01»  → «152401».
        val glued = digits.joinToString("") { it.value }
        result.add(prefix + glued)

        // 2) Каждая группа отделена «|».
        //    «109 00 31» → «109|00|31».
        if (digits.size > 1) {
            val grouped = digits.joinToString(SEP) { it.value }
            result.add(prefix + grouped)
        }

        // 3) Разбивка слитной строки по парам справа.
        //    «1090031» → «1|09|00|31».
        //    Только если групп больше одной или одна длинная (>= 4).
        val pairsRight = splitByPairsRight(glued)
        if (pairsRight != glued && pairsRight.contains(SEP)) {
            result.add(prefix + pairsRight)
        }

        return result.toList()
    }

    /**
     * Разбивает слитную строку цифр на группы по 2 справа.
     * Если длина <= 2 — возвращает как есть (без разделителей).
     *
     * «1090031» → «1|09|00|31»
     * «1524»    → «15|24»
     * «152401»  → «15|24|01»
     * «7»       → «7»
     */
    private fun splitByPairsRight(s: String): String {
        if (s.length <= 2) return s

        // Идём с конца: последние 2, потом ещё 2, пока не останется 1–2.
        val parts = mutableListOf<String>()
        var end = s.length
        while (end > 0) {
            val start = (end - 2).coerceAtLeast(0)
            parts.add(0, s.substring(start, end))
            end = start
        }
        return parts.joinToString(SEP)
    }
}
