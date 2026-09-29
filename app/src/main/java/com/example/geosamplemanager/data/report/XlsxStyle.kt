package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow

/**
 * FIX 5.9-report-xlsx / подзаход 3 (xlsx-styles):
 * Модель стилей .xlsx и палитра.
 *
 * Индексы стилей (styleId) фиксированы и соответствуют
 * порядку записей в xl/styles.xml.
 *
 * Приоритет окраски строки — как в HTML-отчёте:
 *   found → hasImportError → postponed → isBlank → weightControl → default.
 */

/** Определение стиля ячейки. */
data class StyleDef(
    val bold: Boolean = false,
    val fillColor: String? = null,
    val border: Boolean = false
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

    /** Все определения по порядку индексов. */
    val all: List<StyleDef> = listOf(
        StyleDef(),                                    // 0 DEFAULT
        StyleDef(bold = true),                         // 1 BOLD
        StyleDef(bold = true, fillColor = "F0F0F0", border = true), // 2 HEADER
        StyleDef(fillColor = "A5D6A7", border = true), // 3 FOUND
        StyleDef(fillColor = "EF9A9A", border = true), // 4 ERROR
        StyleDef(fillColor = "90CAF9", border = true), // 5 POSTPONED
        StyleDef(fillColor = "FFF59D", border = true), // 6 BLANK
        StyleDef(fillColor = "CE93D8", border = true)  // 7 CONTROL
    )

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
}