package com.example.geosamplemanager.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * FIX 5.9-xlsx-legend (девятый заход):
 *  - sheetViews и sheetFormatPr в worksheet;
 *  - bgColor = fgColor в solid fill;
 *  - легенда: J1..J6, ширину J фиксированную.
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
    fun fillHasBgColorSameAsFgColor() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "Test", rows = listOf(XlsxRow(listOf(XlsxCell.Text("x")))))
            )
        )
        val styles = readEntries(bytes)["xl/styles.xml"] ?: ""
        // Оба цвета одинаковы.
        assertTrue(styles.contains(
            "<fgColor rgb=\"FFA5D6A7\"/><bgColor rgb=\"FFA5D6A7\"/>"
        ))
        assertTrue(styles.contains(
            "<fgColor rgb=\"FFBBDEFB\"/><bgColor rgb=\"FFBBDEFB\"/>"
        ))
        // indexed="64" больше нет.
        assertFalse(styles.contains("indexed=\"64\""))
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
