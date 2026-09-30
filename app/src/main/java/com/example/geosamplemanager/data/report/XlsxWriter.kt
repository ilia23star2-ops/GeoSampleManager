package com.example.geosamplemanager.data.report

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FIX 5.9-report-xlsx / подзаход 4 (xlsx-links):
 * Добавлена поддержка внутренних гиперссылок.
 *
 * FIX 5.9-xlsx-ui (30.09.2026):
 * Запись через промежуточный ByteArrayOutputStream.
 * Раньше zip.finish() не флашил underlying stream — файл мог
 * получиться обрезанным (не открывался в Excel / таблицах).
 * Теперь весь zip собирается в памяти, потом пишется в out одним
 * куском + out.flush().
 */

// ====================================================================
// МОДЕЛИ
// ====================================================================

sealed class XlsxCell {
    /** Переопределение стиля ячейки. null — использовать стиль строки. */
    open val styleId: Int? get() = null

    /** Текст. Пишется как inlineStr (см. sheetN.xml). */
    data class Text(
        val value: String,
        override val styleId: Int? = null
    ) : XlsxCell()

    /**
     * Число. Целые пишутся без `.0` (2, а не 2.0).
     * Дробные — с точкой (2.5).
     */
    data class Number(
        val value: Double,
        override val styleId: Int? = null
    ) : XlsxCell()

    /** Пустая ячейка. В XML не пишется вообще. */
    data object Empty : XlsxCell()
}

/**
 * Строка листа. styleId — индекс стиля из xl/styles.xml.
 * Если styleId == XlsxStyles.DEFAULT, атрибут s не пишется.
 */
data class XlsxRow(
    val cells: List<XlsxCell>,
    val styleId: Int = XlsxStyles.DEFAULT
)

/**
 * Внутренняя гиперссылка на ячейку другого листа книги.
 *
 * @param ref  — ячейка-источник, например "I7".
 * @param location — цель в нотации Excel, например "'Приложения'!A3".
 */
data class XlsxHyperlink(
    val ref: String,
    val location: String
)

/**
 * Лист. Порядок строк и ячеек сохраняется как есть.
 */
data class XlsxSheet(
    val name: String,
    val rows: List<XlsxRow>,
    val hyperlinks: List<XlsxHyperlink> = emptyList()
)

// ====================================================================
// ГЕНЕРАТОР
// ====================================================================

object XlsxWriter {

    /**
     * Записать .xlsx в поток.
     *
     * FIX 5.9-xlsx-ui: собираем zip в памяти, потом пишем одним куском.
     * Это гарантирует, что все байты (включая центральный каталог zip)
     * попадут в out до его закрытия — раньше zip.finish() мог оставить
     * часть данных в буфере Deflater, и файл не открывался.
     *
     * @param sheets список листов, порядок сохраняется.
     * @param out куда писать (не закрывается).
     */
    fun write(sheets: List<XlsxSheet>, out: OutputStream) {
        val bytes = toBytes(sheets)
        out.write(bytes)
        out.flush()
    }

    /**
     * Собрать .xlsx в массив байт.
     * Полезно, когда нужен размер файла или запись через SAF.
     */
    fun toBytes(sheets: List<XlsxSheet>): ByteArray {
        val buffer = ByteArrayOutputStream(64 * 1024)
        val zip = ZipOutputStream(buffer)

        val sanitized = sheets.mapIndexed { idx, s ->
            XlsxSheet(
                name = sanitizeSheetName(s.name, idx + 1),
                rows = s.rows,
                hyperlinks = s.hyperlinks
            )
        }

        writeEntry(zip, "[Content_Types].xml", buildContentTypes(sanitized))
        writeEntry(zip, "_rels/.rels", buildRootRels())
        writeEntry(zip, "xl/workbook.xml", buildWorkbook(sanitized))
        writeEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRels(sanitized))
        writeEntry(zip, "xl/styles.xml", buildStyles())

        sanitized.forEachIndexed { idx, sheet ->
            val path = "xl/worksheets/sheet${idx + 1}.xml"
            writeEntry(zip, path, buildSheet(sheet))
        }

        // close() завершает zip и освобождает Deflater.
        // Внутренний buffer — in-memory, его закрытие безопасно.
        zip.close()

        return buffer.toByteArray()
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
        sb.append("<Override PartName=\"/xl/styles.xml\" ")
        sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        sheets.forEachIndexed { idx, _ ->
            sb.append("<Override PartName=\"/xl/worksheets/sheet${idx + 1}.xml\" ")
            sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun buildRootRels(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" """ +
                """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" """ +
                """Target="xl/workbook.xml"/>""" +
                """</Relationships>"""
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

    /**
     * xl/styles.xml с фиксированным набором стилей из XlsxStyles.all.
     */
    private fun buildStyles(): String {
        val styles = XlsxStyles.all
        val sb = StringBuilder(2048)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")

        // fonts: 0 — обычный, 1 — жирный, 2 — ссылка (синий + подчёркивание)
        sb.append("<fonts count=\"3\">")
        sb.append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        sb.append("<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        sb.append("<font><u/><sz val=\"11\"/><color rgb=\"FF1976D2\"/><name val=\"Calibri\"/></font>")
        sb.append("</fonts>")

        // fills: 0 — none, 1 — gray125 (обязательные), далее — наши цвета
        sb.append("<fills count=\"8\">")
        sb.append("<fill><patternFill patternType=\"none\"/></fill>")
        sb.append("<fill><patternFill patternType=\"gray125\"/></fill>")
        styles.forEachIndexed { idx, def ->
            if (idx == 0) return@forEachIndexed
            val color = def.fillColor ?: "FFFFFF"
            sb.append("<fill><patternFill patternType=\"solid\">")
            sb.append("<fgColor rgb=\"FF").append(color).append("\"/>")
            sb.append("<bgColor indexed=\"64\"/>")
            sb.append("</patternFill></fill>")
        }
        sb.append("</fills>")

        // borders: 0 — none, 1 — thin
        sb.append("<borders count=\"2\">")
        sb.append("<border><left/><right/><top/><bottom/><diagonal/></border>")
        sb.append("<border>")
        sb.append("<left style=\"thin\"/><right style=\"thin\"/>")
        sb.append("<top style=\"thin\"/><bottom style=\"thin\"/>")
        sb.append("<diagonal/></border>")
        sb.append("</borders>")

        // cellStyleXfs (обязательный, один)
        sb.append("<cellStyleXfs count=\"1\">")
        sb.append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>")
        sb.append("</cellStyleXfs>")

        // cellXfs — по одному на каждый StyleDef
        sb.append("<cellXfs count=\"").append(styles.size).append("\">")
        styles.forEach { def ->
            sb.append("<xf numFmtId=\"0\" ")
            sb.append("fontId=\"").append(XlsxStyles.fontIdFor(def)).append("\" ")
            sb.append("fillId=\"").append(XlsxStyles.fillIdFor(def)).append("\" ")
            sb.append("borderId=\"").append(if (def.border) 1 else 0).append("\" ")
            sb.append("xfId=\"0\"/>")
        }
        sb.append("</cellXfs>")

        sb.append("</styleSheet>")
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
            row.cells.forEachIndexed { colIdx, cell ->
                if (cell is XlsxCell.Empty) return@forEachIndexed
                val ref = cellRef(colIdx, rowNum)
                val effectiveStyle = cell.styleId ?: row.styleId
                val styleAttr = if (effectiveStyle != XlsxStyles.DEFAULT)
                    " s=\"$effectiveStyle\"" else ""
                when (cell) {
                    is XlsxCell.Text -> {
                        sb.append("<c r=\"").append(ref).append("\"").append(styleAttr)
                            .append(" t=\"inlineStr\">")
                        sb.append("<is><t>").append(escapeXml(cell.value)).append("</t></is>")
                        sb.append("</c>")
                    }
                    is XlsxCell.Number -> {
                        sb.append("<c r=\"").append(ref).append("\"").append(styleAttr).append(">")
                        sb.append("<v>").append(formatNumber(cell.value)).append("</v>")
                        sb.append("</c>")
                    }
                    is XlsxCell.Empty -> Unit
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")

        // Гиперссылки — после sheetData, до закрытия worksheet.
        if (sheet.hyperlinks.isNotEmpty()) {
            sb.append("<hyperlinks>")
            sheet.hyperlinks.forEach { h ->
                sb.append("<hyperlink ref=\"").append(h.ref).append("\" ")
                sb.append("location=\"").append(escapeXml(h.location)).append("\"/>")
            }
            sb.append("</hyperlinks>")
        }

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

    internal fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

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
