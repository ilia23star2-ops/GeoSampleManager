package com.example.geosamplemanager.data.excel

/**
 * Логика автоопределения заголовков и колонок.
 */
object ExcelAnalyzer {

    private val KEYWORDS = mapOf(
        "serial"    to listOf("п/п", "№ п/п", "номер п/п", "n", "no", "serial", "индекс"),
        "well"      to listOf("скважина", "well", "№ скважины", "номер скважины"),
        "sample"    to listOf("проба", "sample", "№ пробы", "номер пробы", "шифр"),
        "int_from"  to listOf("от", "с", "from", "глубина от", "интервал от"),
        "int_to"    to listOf("до", "по", "to", "глубина до", "интервал до"),
        "weight"    to listOf("вес", "масса", "weight", "кг"),
        "type"      to listOf("тип пробы", "тип", "sample type"),
        "workings"  to listOf("выработка", "гор. выработка", "workings")
    )

    private val EXCLUDED_SHEETS = listOf(
        "титул", "оглавление", "содержание", "cover", "toc"
    )

    fun analyze(sheets: List<SheetData>): SheetAnalysis? {
        val candidates = sheets.filter { sheet ->
            EXCLUDED_SHEETS.none { excl -> sheet.name.lowercase().contains(excl) }
        }
        if (candidates.isEmpty()) return null

        var best: SheetAnalysis? = null
        for (sheet in candidates) {
            for (rowIdx in 0 until minOf(20, sheet.rows.size)) {
                val row = sheet.rows[rowIdx]
                val mapping = matchRow(row)
                val score = mapping.values.count { it != null }

                if (score >= 3 && mapping["well"] != null && mapping["sample"] != null) {
                    if (best == null || score > best.score) {
                        best = SheetAnalysis(
                            sheetName = sheet.name,
                            headerRowIndex = rowIdx,
                            mapping = mapping,
                            score = score,
                            rows = sheet.rows
                        )
                    }
                }
            }
        }
        return best
    }

    private fun matchRow(row: List<String>): Map<String, Int?> {
        val result = mutableMapOf<String, Int?>()
        val normalized = row.map { it.trim().lowercase() }

        for ((role, words) in KEYWORDS) {
            var found: Int? = null
            for ((i, cell) in normalized.withIndex()) {
                if (cell.isEmpty()) continue
                if (words.any { w -> cell == w }) {
                    found = i
                    break
                }
            }
            if (found == null) {
                for ((i, cell) in normalized.withIndex()) {
                    if (cell.isEmpty()) continue
                    if (words.any { w -> cell.contains(w) }) {
                        found = i
                        break
                    }
                }
            }
            result[role] = found
        }
        return result
    }

    fun parseNumber(s: String): Double? {
        if (s.isBlank()) return null
        val cleaned = s.replace(',', '.').trim()
        return cleaned.toDoubleOrNull()
    }
}