package com.example.geosamplemanager.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * FIX 5.9-report-xlsx / подзаход 1 (xlsx-core):
 * Тесты низкоуровневого генератора .xlsx.
 */
class XlsxWriterTest {

    // ================================================================
    // Хелперы
    // ================================================================

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

    // ================================================================
    // Базовое
    // ================================================================

    @Test
    fun outputLooksLikeZip() {
        val bytes = write(
            listOf(
                XlsxSheet(
                    name = "Test",
                    rows = listOf(listOf(XlsxCell.Text("Hello")))
                )
            )
        )
        assertTrue(bytes.size > 0)
        // Zip magic number "PK"
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
    }

    @Test
    fun requiredEntriesPresent() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "Test", rows = listOf(listOf(XlsxCell.Text("x"))))
            )
        )
        val entries = readEntries(bytes)
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
    }

    // ================================================================
    // Ячейки
    // ================================================================

    @Test
    fun textCellIncluded() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "Test", rows = listOf(listOf(XlsxCell.Text("Hello"))))
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet.contains("Hello"))
        assertTrue(sheet.contains("t=\"inlineStr\""))
    }

    @Test
    fun intNumberWrittenWithoutDecimal() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "Test", rows = listOf(listOf(XlsxCell.Number(2.0))))
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
                XlsxSheet(name = "Test", rows = listOf(listOf(XlsxCell.Number(2.5))))
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
                        listOf(XlsxCell.Empty, XlsxCell.Text("Only"))
                    )
                )
            )
        )
        val sheet = readEntries(bytes)["xl/worksheets/sheet1.xml"] ?: ""
        assertFalse(sheet.contains("r=\"A1\""))
        assertTrue(sheet.contains("r=\"B1\""))
    }

    // ================================================================
    // Несколько листов
    // ================================================================

    @Test
    fun multipleSheets() {
        val bytes = write(
            listOf(
                XlsxSheet(name = "First", rows = listOf(listOf(XlsxCell.Text("A")))),
                XlsxSheet(name = "Second", rows = listOf(listOf(XlsxCell.Text("B"))))
            )
        )
        val entries = readEntries(bytes)
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries["xl/worksheets/sheet1.xml"]!!.contains("A"))
        assertTrue(entries["xl/worksheets/sheet2.xml"]!!.contains("B"))
        assertTrue(entries["xl/workbook.xml"]!!.contains("First"))
        assertTrue(entries["xl/workbook.xml"]!!.contains("Second"))
    }

    // ================================================================
    // Утилиты
    // ================================================================

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