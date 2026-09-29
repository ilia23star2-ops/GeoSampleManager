package com.example.geosamplemanager.data.report

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FIX 5.9-report-xlsx / подзаход 1 (xlsx-core):
 * Низкоуровневый генератор .xlsx без внешних библиотек.
 *
 * .xlsx — это ZIP-архив с XML-файлами. Минимальный набор:
 *   [Content_Types].xml
 *   _rels/.rels
 *   xl/workbook.xml
 *   xl/_rels/workbook.xml.rels
 *   xl/worksheets/sheetN.xml
 *
 * Возможности этого подзахода:
 *   - текст (через inlineStr — без sharedStrings);
 *   - числа (целые и дробные);
 *   - пустые ячейки (не пишутся);
 *   - несколько листов.
 *
 * Чего НЕТ (следующие подзаходы):
 *   - стилей (цвет строк, жирный шрифт);
 *   - гиперссылок;
 *   - формул;
 *   - объединённых ячеек;
 *   - ширины колонок.
 */

// ====================================================================
// МОДЕЛИ
// ====================================================================

sealed class XlsxCell {
    /** Текст. Пишется как inlineStr (см. sheetN.xml). */
    data class Text(val value: String) : XlsxCell()

    /**
     * Число. Целые пишутся без `.0` (2, а не 2.0).
     * Дробные — с точкой (2.5).
     */
    data class Number(val value: Double) : XlsxCell()

    /** Пустая ячейка. В XML не пишется вообще. */
    data object Empty : XlsxCell()
}

/**
 * Лист. Порядок строк и ячеек сохраняется как есть.
 */
data class XlsxSheet(
    val name: String,
    val rows: List<List<XlsxCell>>
)

// ====================================================================
// ГЕНЕРАТОР
// ====================================================================

object XlsxWriter {

    /**
     * Записать .xlsx в поток.
     * @param sheets список листов, порядок сохраняется.
     * @param out куда писать (не закрывается).
     */
    fun write(sheets: List<XlsxSheet>, out: OutputStream) {
        val zip = ZipOutputStream(out)

        val sanitized = sheets.mapIndexed { idx, s ->
            XlsxSheet(
                name = sanitizeSheetName(s.name, idx + 1),
                rows = s.rows
            )
        }

        writeEntry(zip, "[Content_Types].xml", buildContentTypes(sanitized))
        writeEntry(zip, "_rels/.rels", buildRootRels())
        writeEntry(zip, "xl/workbook.xml", buildWorkbook(sanitized))
        writeEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRels(sanitized))

        sanitized.forEachIndexed { idx, sheet ->
            val path = "xl/worksheets/sheet${idx + 1}.xml"
            writeEntry(zip, path, buildSheet(sheet))
        }

        zip.finish()
    }

    // ================================================================
    // XML-строители
    // ================================================================

    private fun buildContentTypes(sheets: List<XlsxSheet>): String {
        val sb = StringBuilder(512)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ")
        sb.append("ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ")
        sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        sheets.forEachIndexed { idx, _ ->
            sb.append("<Override PartName=\"/xl/worksheets/sheet${idx + 1}.xml\" ")
            sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun buildRootRels(): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent().replace("\n", "").replace(Regex(" {2,}"), "")
    }

    private fun buildWorkbook(sheets: List<XlsxSheet>): String {
        val sb = StringBuilder(512)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        sb.append("<sheets>")
        sheets.forEachIndexed { idx, sheet ->
            sb.append("<sheet name=\"").append(escapeXml(sheet.name)).append("\" ")
            sb.append("sheetId=\"").append(idx + 1).append("\" ")
            sb.append("r:id=\"rId").append(idx + 1).append("\"/>")
        }
        sb.append("</sheets>")
        sb.append("</workbook>")
        return sb.toString()
    }

    private fun buildWorkbookRels(sheets: List<XlsxSheet>): String {
        val sb = StringBuilder(512)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        sheets.forEachIndexed { idx, _ ->
            sb.append("<Relationship Id=\"rId").append(idx + 1).append("\" ")
            sb.append("Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
            sb.append("Target=\"worksheets/sheet").append(idx + 1).append(".xml\"/>")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun buildSheet(sheet: XlsxSheet): String {
        val sb = StringBuilder(1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { rowIdx, row ->
            val rowNum = rowIdx + 1
            sb.append("<row r=\"").append(rowNum).append("\">")
            row.forEachIndexed { colIdx, cell ->
                if (cell is XlsxCell.Empty) return@forEachIndexed
                val ref = cellRef(colIdx, rowNum)
                when (cell) {
                    is XlsxCell.Text -> {
                        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\">")
                        sb.append("<is><t>").append(escapeXml(cell.value)).append("</t></is>")
                        sb.append("</c>")
                    }
                    is XlsxCell.Number -> {
                        sb.append("<c r=\"").append(ref).append("\">")
                        sb.append("<v>").append(formatNumber(cell.value)).append("</v>")
                        sb.append("</c>")
                    }
                    is XlsxCell.Empty -> Unit
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    // ================================================================
    // Утилиты
    // ================================================================

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        val entry = ZipEntry(path)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /**
     * A1, B1, ..., Z1, AA1, AB1, ... — 0-based колонка + 1-based строка.
     */
    internal fun cellRef(colIdx: Int, rowNum: Int): String {
        var col = colIdx
        val letters = StringBuilder()
        while (true) {
            letters.insert(0, ('A' + (col % 26)))
            col = col / 26 - 1
            if (col < 0) break
        }
        return "$letters$rowNum"
    }

    /**
     * Целые без `.0`, дробные с точкой. `2.0` → `2`, `2.5` → `2.5`.
     */
    internal fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    /**
     * XML-escape для `<`, `>`, `&`, `"`, `'`. Также убираем управляющие
     * символы (Excel их не принимает).
     */
    internal fun escapeXml(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length + 8)
        s.forEach { c ->
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\'' -> sb.append("&apos;")
                c.code < 0x20 && c != '\t' && c != '\n' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Excel: имя листа ≤31 символа, без `\ / ? * [ ] :`.
     * Если после очистки пусто — "SheetN".
     */
    internal fun sanitizeSheetName(name: String, index: Int): String {
        val cleaned = name.map { c ->
            when (c) {
                '\\', '/', '?', '*', '[', ']', ':' -> '_'
                else -> c
            }
        }.joinToString("")

        val trimmed = cleaned.trim()
        val base = if (trimmed.isEmpty()) "Sheet$index" else trimmed
        return if (base.length > 31) base.substring(0, 31) else base
    }
}