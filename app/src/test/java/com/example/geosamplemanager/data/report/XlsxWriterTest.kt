package com.example.geosamplemanager.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * FIX 5.9-xlsx-theme (одиннадцатый заход):
 *  - xl/theme/theme1.xml;
 *  - связь на theme в workbook.xml.rels;
 *  - Override в [Content_Types].xml;
 *  - theme=N в fills.
 */
class XlsxWriterTest {

    private fun write(sheets: List<XlsxSheet>): ByteArray {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)
        return out.toByteArray()
    }

    private fun readEntries(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val text = zip.readBytes().toString(Charsets.UTF_8)
                result[entry.name] = text
                entry = zip.nextEntry
            }
        }
        return result
    }

    @Test
    fun outputLooksLikeZip() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("Hello"))))
                )
            )
        )
        assertTrue(bytes.size > 0)
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
    }

    @Test
    fun themeEntryPresent() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val entries = readEntries(bytes)
        assertTrue(entries.containsKey("xl/theme/theme1.xml"))
    }

    @Test
    fun themeContainsOurColors() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val theme = readEntries(bytes)["xl/theme/theme1.xml"] ?: ""
        assertTrue(theme.contains("<a:accent1><a:srgbClr val=\"A5D6A7\"/>"))
        assertTrue(theme.contains("<a:accent2><a:srgbClr val=\"EF9A9A\"/>"))
        assertTrue(theme.contains("<a:accent3><a:srgbClr val=\"90CAF9\"/>"))
        assertTrue(theme.contains("<a:accent4><a:srgbClr val=\"FFF59D\"/>"))
        assertTrue(theme.contains("<a:accent5><a:srgbClr val=\"CE93D8\"/>"))
        assertTrue(theme.contains("<a:accent6><a:srgbClr val=\"BBDEFB\"/>"))
    }

    @Test
    fun workbookRelsHaveThemeLink() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val rels = readEntries(bytes)["xl/_rels/workbook.xml.rels"] ?: ""
        assertTrue(rels.contains("relationships/theme"))
        assertTrue(rels.contains("theme/theme1.xml"))
    }

    @Test
    fun contentTypesHaveThemeOverride() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val ct = readEntries(bytes)["[Content_Types].xml"] ?: ""
        assertTrue(ct.contains("/xl/theme/theme1.xml"))
        assertTrue(ct.contains("officedocument.theme+xml"))
    }

    @Test
    fun fillsHaveThemeAttribute() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val styles = readEntries(bytes)["xl/styles.xml"] ?: ""
        // FOUND: theme=4.
        assertTrue(styles.contains("theme=\"4\" rgb=\"FFA5D6A7\""))
        // ERROR: theme=5.
        assertTrue(styles.contains("theme=\"5\" rgb=\"FFEF9A9A\""))
        // BLANK: theme=7.
        assertTrue(styles.contains("theme=\"7\" rgb=\"FFFFF59D\""))
        // CONTROL: theme=8.
        assertTrue(styles.contains("theme=\"8\" rgb=\"FFCE93D8\""))
    }

    @Test
    fun requiredEntriesPresent() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val entries = readEntries(bytes)
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/styles.xml"))
    }

    @Test
    fun sheetHasSheetViewsAndFormatPr() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("<sheetViews>"))
        assertTrue(sheet.contains("<sheetView workbookViewId=\"0\"/>"))
        assertTrue(sheet.contains("<sheetFormatPr defaultRowHeight=\"15\"/>"))
    }

    @Test
    fun textCellIncluded() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("Hello"))))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("Hello"))
        assertTrue(sheet.contains("t=\"inlineStr\""))
    }

    @Test
    fun emptyTextWrittenAsBlank() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("", XlsxStyles.FOUND))))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("r=\"A1\" s=\"3\"/>"))
        assertFalse(sheet.contains("r=\"A1\" s=\"3\" t=\"inlineStr\""))
    }

    @Test
    fun emptyRowSelfClosing() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(
                        XlsxRow(listOf(XlsxCell.Text("A"))),
                        XlsxRow(emptyList()),
                        XlsxRow(listOf(XlsxCell.Text("B")))
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("<row r=\"2\"/>"))
    }

    @Test
    fun intNumberWrittenWithoutDecimal() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Number(2.0))))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("<v>2</v>"))
        assertFalse(sheet.contains("<v>2.0</v>"))
    }

    @Test
    fun decimalNumberWrittenWithDot() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Number(2.5))))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("<v>2.5</v>"))
    }

    @Test
    fun emptyCellNotWritten() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(
                        XlsxRow(listOf(XlsxCell.Empty, XlsxCell.Text("Only")))
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertFalse(sheet.contains("r=\"A1\""))
        assertTrue(sheet.contains("r=\"B1\""))
    }

    @Test
    fun blankCellWrittenWithStyle() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(
                        XlsxRow(listOf(
                            XlsxCell.Text("First"),
                            XlsxCell.Blank(styleId = XlsxStyles.TITLE)
                        ))
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("r=\"B1\" s=\"9\""))
        assertFalse(sheet.contains("r=\"B1\" s=\"9\" t=\"inlineStr\""))
    }

    @Test
    fun multipleSheets() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "First", rows = listOf(XlsxRow(listOf(XlsxCell.Text("A"))))),
                XlsxSheet(name = "Second", rows = listOf(XlsxRow(listOf(XlsxCell.Text("B")))))
            )
        )
        val entries = readEntries(bytes)
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries["xl/worksheets/sheet1.xml"]!!.contains("A"))
        assertTrue(entries["xl/worksheets/sheet2.xml"]!!.contains("B"))
    }

    @Test
    fun stylesXmlHasAllRequiredSections() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "Test", rows = listOf(XlsxRow(listOf(XlsxCell.Text("x")))))
            )
        )
        val styles = readEntries(bytes)["xl/styles.xml"] ?: ""
        assertTrue(styles.contains("<numFmts count=\"0\"/>"))
        assertTrue(styles.contains("<cellStyleXfs count=\"11\">"))
        assertTrue(styles.contains("<cellXfs count=\"11\">"))
        assertTrue(styles.contains("<cellStyles count=\"1\">"))
        assertTrue(styles.contains("name=\"Normal\""))
        assertTrue(styles.contains("<dxfs count=\"0\"/>"))
        assertTrue(styles.contains("<tableStyles count=\"0\""))
    }

    @Test
    fun fillHasRgbAndIndexedAndTheme() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "Test", rows = listOf(XlsxRow(listOf(XlsxCell.Text("x")))))
            )
        )
        val styles = readEntries(bytes)["xl/styles.xml"] ?: ""
        // FOUND: rgb + indexed + theme.
        assertTrue(styles.contains(
            "theme=\"4\" rgb=\"FFA5D6A7\" indexed=\"42\""
        ))
        assertTrue(styles.contains(
            "theme=\"5\" rgb=\"FFEF9A9A\" indexed=\"29\""
        ))
    }

    @Test
    fun stylesXmlContainsLinkFont() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "Test", rows = listOf(XlsxRow(listOf(XlsxCell.Text("x")))))
            )
        )
        val styles = readEntries(bytes)["xl/styles.xml"] ?: ""
        assertTrue(styles.contains("<fonts count=\"3\">"))
        assertTrue(styles.contains("FF1976D2"))
        assertTrue(styles.contains("<u/>"))
    }

    @Test
    fun styleIdWrittenToCell() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(
                        XlsxRow(
                            listOf(XlsxCell.Text("Styled")),
                            styleId = XlsxStyles.FOUND
                        )
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("s=\"3\""))
    }

    @Test
    fun defaultStyleNotWritten() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("Plain"))))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertFalse(sheet.contains("s=\"0\""))
    }

    @Test
    fun cellStyleOverridesRowStyle() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(
                        XlsxRow(
                            listOf(
                                XlsxCell.Text("plain"),
                                XlsxCell.Text("link", styleId = XlsxStyles.LINK)
                            ),
                            styleId = XlsxStyles.FOUND
                        )
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("r=\"A1\" s=\"3\""))
        assertTrue(sheet.contains("r=\"B1\" s=\"8\""))
    }

    @Test
    fun hyperlinkWrittenToSheet() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("Go")))),
                    hyperlinks = listOf(
                        XlsxHyperlink(ref = "A1", location = "'Other'!B2")
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("<hyperlinks>"))
        assertTrue(sheet.contains("ref=\"A1\""))
        assertTrue(sheet.contains("location="))
        assertTrue(sheet.contains("!B2"))
    }

    @Test
    fun noHyperlinksSectionWhenNone() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x"))))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertFalse(sheet.contains("<hyperlinks>"))
    }

    @Test
    fun mergeCellsWrittenToSheet() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(
                        XlsxRow(
                            listOf(
                                XlsxCell.Text("Title"),
                                XlsxCell.Blank(XlsxStyles.TITLE),
                                XlsxCell.Blank(XlsxStyles.TITLE)
                            ),
                            styleId = XlsxStyles.TITLE
                        )
                    ),
                    mergeCells = listOf("A1:C1")
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("<mergeCells count=\"1\">"))
        assertTrue(sheet.contains("<mergeCell ref=\"A1:C1\"/>"))
    }

    @Test
    fun legendRenderedInColumnJ() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(
                        XlsxRow(listOf(XlsxCell.Text("Title"))),
                        XlsxRow(listOf(XlsxCell.Text("Row2")))
                    ),
                    legend = listOf(
                        XlsxLegendItem("Легенда", XlsxStyles.BOLD),
                        XlsxLegendItem("Найдена", XlsxStyles.FOUND)
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("r=\"J1\""))
        assertTrue(sheet.contains("r=\"J2\""))
        assertTrue(sheet.contains("Легенда"))
        assertTrue(sheet.contains("Найдена"))
    }

    @Test
    fun legendColumnHasFixedWidth() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(XlsxRow(listOf(XlsxCell.Text("x")))),
                    legend = listOf(XlsxLegendItem("Легенда", XlsxStyles.BOLD))
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("<col min=\"10\" max=\"10\""))
    }

    @Test
    fun themeForReturnsExpectedValues() {
        assertEquals(4, XlsxStyles.themeFor("A5D6A7"))
        assertEquals(5, XlsxStyles.themeFor("EF9A9A"))
        assertEquals(6, XlsxStyles.themeFor("90CAF9"))
        assertEquals(7, XlsxStyles.themeFor("FFF59D"))
        assertEquals(8, XlsxStyles.themeFor("CE93D8"))
        assertEquals(9, XlsxStyles.themeFor("BBDEFB"))
        assertEquals(2, XlsxStyles.themeFor("F0F0F0"))
        assertEquals(3, XlsxStyles.themeFor("E3F2FD"))
    }

    @Test
    fun indexedForReturnsExpectedValues() {
        assertEquals(22, XlsxStyles.indexedFor("F0F0F0"))
        assertEquals(42, XlsxStyles.indexedFor("A5D6A7"))
        assertEquals(29, XlsxStyles.indexedFor("EF9A9A"))
        assertEquals(44, XlsxStyles.indexedFor("90CAF9"))
        assertEquals(43, XlsxStyles.indexedFor("FFF59D"))
        assertEquals(46, XlsxStyles.indexedFor("CE93D8"))
        assertEquals(44, XlsxStyles.indexedFor("BBDEFB"))
        assertEquals(41, XlsxStyles.indexedFor("E3F2FD"))
    }

    @Test
    fun escapeXmlSpecials() {
        assertEquals("a&lt;b&gt;c", XlsxWriter.escapeXml("a<b>c"))
        assertEquals("x &amp; y", XlsxWriter.escapeXml("x & y"))
        assertEquals("&quot;q&quot;", XlsxWriter.escapeXml("\"q\""))
        assertEquals("&apos;a&apos;", XlsxWriter.escapeXml("'a'"))
    }

    @Test
    fun escapeXmlDropsControlChars() {
        assertEquals("abc", XlsxWriter.escapeXml("a\u0001b\u0002c"))
    }

    @Test
    fun sanitizeSheetNameForbiddenChars() {
        assertEquals("a_b_c", XlsxWriter.sanitizeSheetName("a/b:c", 1))
        assertEquals("___", XlsxWriter.sanitizeSheetName("?*[", 1))
    }

    @Test
    fun sanitizeSheetNameTooLong() {
        val long = "x".repeat(50)
        val result = XlsxWriter.sanitizeSheetName(long, 1)
        assertEquals(31, result.length)
    }

    @Test
    fun sanitizeSheetNameEmptyFallback() {
        assertEquals("Sheet1", XlsxWriter.sanitizeSheetName("", 1))
        assertEquals("Sheet2", XlsxWriter.sanitizeSheetName("   ", 2))
    }

    @Test
    fun cellRefBasic() {
        assertEquals("A1", XlsxWriter.cellRef(0, 1))
        assertEquals("B1", XlsxWriter.cellRef(1, 1))
        assertEquals("Z1", XlsxWriter.cellRef(25, 1))
        assertEquals("AA1", XlsxWriter.cellRef(26, 1))
        assertEquals("AB1", XlsxWriter.cellRef(27, 1))
        assertEquals("A10", XlsxWriter.cellRef(0, 10))
    }

    @Test
    fun formatNumberIntAndDecimal() {
        assertEquals("2", XlsxWriter.formatNumber(2.0))
        assertEquals("0", XlsxWriter.formatNumber(0.0))
        assertEquals("-3", XlsxWriter.formatNumber(-3.0))
        assertEquals("2.5", XlsxWriter.formatNumber(2.5))
        assertEquals("0", XlsxWriter.formatNumber(Double.NaN))
        assertEquals("0", XlsxWriter.formatNumber(Double.POSITIVE_INFINITY))
    }
}
