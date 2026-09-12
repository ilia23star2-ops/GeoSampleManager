package com.example.geosamplemanager.data.excel

import com.example.geosamplemanager.data.settings.ImportSettings
import java.util.Locale

/**
 * Решает, импортировать ли строку, и определяет тип/статус пробы.
 */
object SampleFilter {

    enum class Result {
        KEEP,           // импортируем
        SKIP_BLANK,     // бланк без данных — пропускаем
        SKIP_EMPTY      // пустая строка — пропускаем
    }

    /**
     * Проверяет строку и говорит, что с ней делать.
     */
    fun classify(row: List<String>, mapping: Map<String, Int?>, settings: ImportSettings): Result {
        val wellNum = getCell(row, mapping[ExcelAnalyzer.Roles.WELL]).trim()
        val sampleNum = getCell(row, mapping[ExcelAnalyzer.Roles.SAMPLE]).trim()

        // Пусто — вообще не наша строка
        if (wellNum.isEmpty() && sampleNum.isEmpty()) return Result.SKIP_EMPTY

        val typeText = getCell(row, mapping[ExcelAnalyzer.Roles.TYPE]).lowercase(Locale.ROOT)
        val intFrom = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.INT_FROM]))
        val intTo = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.INT_TO]))
        val weight = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.WEIGHT]))
        val hasData = intFrom != null || intTo != null || weight != null

        // 1) Холостая — всегда импортируем
        val isHollow = settings.effectiveTypeKeywords("hollow").any { typeText.contains(it) }
        if (isHollow) return Result.KEEP

        // 2) Бланк — пропускаем, если нет данных и включён флаг
        val isBlank = settings.blankKeywords.any { typeText.contains(it) }
        if (isBlank && !hasData && settings.skipBlanksWithoutData) return Result.SKIP_BLANK

        // 3) Остальное — импортируем
        return Result.KEEP
    }

    /**
     * Определяет тип пробы (auger/channel/cobra/hollow/duplicate) и статус (normal/blank/control).
     * Холостая всегда даёт type="auger" (значение не важно), status="blank".
     */
    fun classifyTypeAndStatus(
        row: List<String>,
        mapping: Map<String, Int?>,
        wellNum: String,
        settings: ImportSettings
    ): Pair<String, String> {
        val typeText = getCell(row, mapping[ExcelAnalyzer.Roles.TYPE]).lowercase(Locale.ROOT)

        // Приоритет 1: холостая
        if (settings.effectiveTypeKeywords("hollow").any { typeText.contains(it) }) {
            return "auger" to "blank"
        }
        // Приоритет 2: тип по тексту
        if (settings.effectiveTypeKeywords("channel").any { typeText.contains(it) }) {
            return "channel" to "normal"
        }
        if (settings.effectiveTypeKeywords("cobra").any { typeText.contains(it) }) {
            return "cobra" to "normal"
        }
        if (settings.effectiveTypeKeywords("auger").any { typeText.contains(it) }) {
            return "auger" to "normal"
        }
        // Приоритет 3: дубликат — оставляем как normal, тип из well
        // Приоритет 4: по префиксу скважины
        val well = wellNum.uppercase(Locale.ROOT)
        if (well.startsWith("KPD") || well.startsWith("KOP")) return "auger" to "normal"
        if (well.startsWith("KBK") || well.startsWith("KMB")) return "channel" to "normal"
        if (well.startsWith("ACD")) return "cobra" to "normal"
        // По умолчанию
        return "auger" to "normal"
    }

    private fun getCell(row: List<String>, idx: Int?): String {
        if (idx == null || idx < 0 || idx >= row.size) return ""
        return row[idx]
    }
}