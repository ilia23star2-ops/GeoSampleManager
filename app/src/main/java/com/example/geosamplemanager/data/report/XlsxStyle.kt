package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow

/**
 * FIX 5.9-xlsx-legend (30.09.2026, десятый заход):
 *  - indexedColorMap: hex-цвет → ближайший индекс стандартной
 *    палитры Excel 97 (0–63). Нужно для вьюеров, которые не
 *    понимают rgb, но понимают indexed.
 *  - fillIdFor и indexedFor используются одновременно в
 *    XlsxWriter.buildStyles: <fgColor rgb="..." indexed="N"/>.
 *    Excel возьмёт rgb, примитивный вьюер — indexed.
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

    /**
     * Соответствие наших hex-цветов ближайшим индексам стандартной
     * палитры Excel 97 (0–63). Используется как fallback: пишем
     * вместе с rgb, чтобы старый вьюер взял indexed.
     */
    private val indexedColorMap: Map<String, Int> = mapOf(
        "F0F0F0" to 22,   // C0C0C0 светло-серый
        "A5D6A7" to 42,   // CCFFCC светло-зелёный
        "EF9A9A" to 29,   // FF8080 светло-красный
        "90CAF9" to 44,   // 99CCFF светло-синий
        "FFF59D" to 43,   // FFFF99 светло-жёлтый
        "CE93D8" to 46,   // CC99FF светло-фиолетовый
        "BBDEFB" to 44,   // 99CCFF светло-синий
        "E3F2FD" to 41    // CCFFFF бледно-голубой
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

    /** Ближайший indexed-цвет для hex. null — если маппинга нет. */
    internal fun indexedFor(hex: String?): Int? =
        hex?.let { indexedColorMap[it] }
}
