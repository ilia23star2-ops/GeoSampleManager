package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow

/**
 * FIX 5.9-report-xlsx / подзаход 4 (xlsx-links):
 * Добавлен стиль LINK — синий текст с подчёркиванием.
 *
 * FIX 5.9-xlsx-formatting:
 *  - fillMap — уникальные цвета заливки → индекс fill'а в styles.xml.
 *  - Раньше fillIdFor хардкодил индексы 2..7, но фактический порядок
 *    fills в styles.xml был сдвинут на -1 (из-за пропуска idx == 0).
 *    Все fillId были неверные → Excel молча рисовал белый фон.
 */

/** Определение стиля ячейки. */
data class StyleDef(
    val bold: Boolean = false,
    val fillColor: String? = null,
    val border: Boolean = false,
    val fontColor: String? = null,
    val underline: Boolean = false
)

object XlsxStyles {

    const val DEFAULT = 0
    const val BOLD = 1
    const val HEADER = 2
    const val FOUND = 3
    const val ERROR = 4
    const val POSTPONED = 5
    const val BLANK = 6
    const val CONTROL = 7
    const val LINK = 8

    /** Все определения по порядку индексов (индексы = индекс в cellXfs). */
    val all: List<StyleDef> = listOf(
        StyleDef(),                                        // 0 DEFAULT
        StyleDef(bold = true),                             // 1 BOLD
        StyleDef(bold = true, fillColor = "F0F0F0", border = true), // 2 HEADER
        StyleDef(fillColor = "A5D6A7", border = true),     // 3 FOUND
        StyleDef(fillColor = "EF9A9A", border = true),     // 4 ERROR
        StyleDef(fillColor = "90CAF9", border = true),     // 5 POSTPONED
        StyleDef(fillColor = "FFF59D", border = true),     // 6 BLANK
        StyleDef(fillColor = "CE93D8", border = true),     // 7 CONTROL
        StyleDef(fontColor = "1976D2", underline = true, border = true) // 8 LINK
    )

    /**
     * Уникальные непустые цвета заливки → индекс fill'а в styles.xml.
     * Начинается с 2, потому что 0 (none) и 1 (gray125) зарезервированы.
     *
     * Порядок — по первому появлению в [all].
     */
    val fillMap: Map<String, Int> by lazy {
        val map = LinkedHashMap<String, Int>()
        var idx = 2
        all.forEach { def ->
            val c = def.fillColor ?: return@forEach
            if (c !in map) {
                map[c] = idx
                idx++
            }
        }
        map
    }

    /**
     * Стиль строки данных пробы.
     * Порядок проверок — как в ReportHtmlGenerator.rowCssClass.
     */
    fun styleForRow(row: SampleRow): Int = when {
        row.found -> FOUND
        row.hasImportError -> ERROR
        row.postponed -> POSTPONED
        row.isBlank -> BLANK
        row.weightControl -> CONTROL
        else -> DEFAULT
    }

    /** Индекс шрифта для стиля: 0 — обычный, 1 — жирный, 2 — ссылка. */
    internal fun fontIdFor(def: StyleDef): Int = when {
        def.fontColor != null || def.underline -> 2
        def.bold -> 1
        else -> 0
    }

    /** Индекс fill для стиля. 0 — нет заливки. */
    internal fun fillIdFor(def: StyleDef): Int =
        def.fillColor?.let { fillMap[it] } ?: 0
}
