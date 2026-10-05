package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow

/**
 * FIX 5.9-xlsx-clean (30.09.2026, четырнадцатый заход):
 *  - убран themeIndexMap и themeFor. Причина: Excel Online, увидев
 *    theme="N", брал цвет из своей встроенной темы, а не из нашей
 *    theme1.xml — цвета «перепутывались».
 *  - Оставляем rgb (Excel, LibreOffice, Bree, мобильные) + indexed
 *    (примитивные вьюеры). Оба работают корректно.
 */

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
    const val TITLE = 9
    const val META = 10

    val all: List<StyleDef> = listOf(
        StyleDef(),                                        // 0 DEFAULT
        StyleDef(bold = true),                             // 1 BOLD
        StyleDef(bold = true, fillColor = "F0F0F0", border = true), // 2 HEADER
        StyleDef(fillColor = "A5D6A7", border = true),     // 3 FOUND
        StyleDef(fillColor = "EF9A9A", border = true),     // 4 ERROR
        StyleDef(fillColor = "90CAF9", border = true),     // 5 POSTPONED
        StyleDef(fillColor = "FFF59D", border = true),     // 6 BLANK
        StyleDef(fillColor = "CE93D8", border = true),     // 7 CONTROL
        StyleDef(fontColor = "1976D2", underline = true, border = true), // 8 LINK
        StyleDef(bold = true, fillColor = "BBDEFB"),       // 9 TITLE
        StyleDef(fillColor = "E3F2FD")                     // 10 META
    )

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

    private val indexedColorMap: Map<String, Int> = mapOf(
        "F0F0F0" to 22,
        "A5D6A7" to 42,
        "EF9A9A" to 29,
        "90CAF9" to 44,
        "FFF59D" to 43,
        "CE93D8" to 46,
        "BBDEFB" to 44,
        "E3F2FD" to 41
    )

    fun styleForRow(row: SampleRow): Int = when {
        row.found -> FOUND
        row.hasImportError -> ERROR
        row.postponed -> POSTPONED
        row.isBlank -> BLANK
        row.weightControl -> CONTROL
        else -> DEFAULT
    }

    internal fun fontIdFor(def: StyleDef): Int = when {
        def.fontColor != null || def.underline -> 2
        def.bold -> 1
        else -> 0
    }

    internal fun fillIdFor(def: StyleDef): Int =
        def.fillColor?.let { fillMap[it] } ?: 0

    internal fun indexedFor(hex: String?): Int? =
        hex?.let { indexedColorMap[it] }
}
