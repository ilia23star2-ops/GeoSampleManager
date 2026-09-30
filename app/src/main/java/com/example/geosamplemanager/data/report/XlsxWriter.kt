package com.example.geosamplemanager.data.report

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FIX 5.9-xlsx-valid (30.09.2026, седьмой заход):
 *  - styles.xml: добавлены обязательные секции numFmts, dxfs, tableStyles;
 *  - fill: добавлен <bgColor indexed="64"/> (без него Excel не рисует solid);
 *  - пустой XlsxCell.Text не пишется как inlineStr с пустым <t/>;
 *  - пустая XlsxRow пишется как <row r="N"/>.
 */

sealed class XlsxCell {
    open val styleId: Int? get() = null

    data class Text(
        val value: String,
        override val styleId: Int? = null
    ) : XlsxCell()

    data class Number(
        val value: Double,
        override val styleId: Int? = null
    ) : XlsxCell()

    data object Empty : XlsxCell()

    data class Blank(
        override val styleId: Int? = null
    ) : XlsxCell()
}

data class XlsxRow(
    val cells: List<XlsxCell>,
    val styleId: Int = XlsxStyles.DEFAULT
)

data class XlsxHyperlink(
    val ref: String,
    val location: String
)

data class XlsxImage(
    val bytes: ByteArray,
    val extension: String,
    val colIdx: Int = 0,
    val rowIdx: Int = 0,
    val widthPx: Int = 240,
    val heightPx: Int = 180
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is XlsxImage) return false
        return extension == other.extension &&
                colIdx == other.colIdx &&
                rowIdx == other.rowIdx &&
                widthPx == other.widthPx &&
                heightPx == other.heightPx &&
                bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + extension.hashCode()
        result = 31 * result + colIdx
        result = 31 * result + rowIdx
        result = 31 * result + widthPx
        result = 31 * result + heightPx
        return result
    }
}

data class XlsxSheet(
    val name: String,
    val rows: List<XlsxRow>,
    val hyperlinks: List<XlsxHyperlink> = emptyList(),
    val images: List<XlsxImage> = emptyList(),
    val skipWidthRows: Int = 0,
    val mergeCells: List<String> = emptyList()
)

object XlsxWriter {

    private const val MIN_COL_WIDTH = 4.0
    private const val MAX_COL_WIDTH = 100.0
    private const val COL_WIDTH_PADDING = 2.0
    private const val EMU_PER_PX = 9525L
    private const val ROW_HEIGHT_PX_TO_PT = 0.75

    fun write(sheets: List<XlsxSheet>, out: OutputStream) {
        val bytes = toBytes(sheets)
        out.write(bytes)
        out.flush()
    }

    fun toBytes(sheets: List<XlsxSheet>): ByteArray {
        val buffer = ByteArrayOutputStream(64 * 1024)
        val zip = ZipOutputStream(buffer)

        val sanitized = sheets.mapIndexed { idx, s ->
            XlsxSheet(
                name = sanitizeSheetName(s.name, idx + 1),
                rows = s.rows,
                hyperlinks = s.hyperlinks,
                images = s.images,
                skipWidthRows = s.skipWidthRows,
                mergeCells = s.mergeCells
            )
        }

        val allImages = mutableListOf<XlsxImage>()
        sanitized.forEach { sheet -> allImages.addAll(sheet.images) }

        val drawingIndexOfSheet = IntArray(sanitized.size) { -1 }
        val imageIndexOfImage = HashMap<XlsxImage, Int>()
        var drawingCounter = 0
        var imageCounter = 0
        sanitized.forEachIndexed { sheetIdx, sheet ->
            if (sheet.images.isNotEmpty()) {
                drawingCounter++
                drawingIndexOfSheet[sheetIdx] = drawingCounter
                sheet.images.forEach { img ->
                    imageCounter++
                    imageIndexOfImage[img] = imageCounter
                }
            }
        }

        writeEntry(zip, "[Content_Types].xml",
            buildContentTypes(sanitized, drawingIndexOfSheet, allImages))
        writeEntry(zip, "_rels/.rels", buildRootRels())
        writeEntry(zip, "xl/workbook.xml", buildWorkbook(sanitized))
        writeEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRels(sanitized))
        writeEntry(zip, "xl/styles.xml", buildStyles())

        sanitized.forEachIndexed { sheetIdx, sheet ->
            val drawingIndex = drawingIndexOfSheet[sheetIdx]
            val path = "xl/worksheets/sheet${sheetIdx + 1}.xml"
            writeEntry(zip, path, buildSheet(sheet, drawingIndex))

            if (drawingIndex > 0) {
                writeEntry(
                    zip,
                    "xl/worksheets/_rels/sheet${sheetIdx + 1}.xml.rels",
                    buildSheetRels(drawingIndex)
                )
                writeEntry(
                    zip,
                    "xl/drawings/drawing$drawingIndex.xml",
                    buildDrawing(sheet.images)
                )
                writeEntry(
                    zip,
                    "xl/drawings/_rels/drawing$drawingIndex.xml.rels",
                    buildDrawingRels(sheet.images, imageIndexOfImage)
                )
            }
        }

        allImages.forEach { img ->
            val idx = imageIndexOfImage[img] ?: return@forEach
            val entry = ZipEntry("xl/media/image$idx.${img.extension}")
            zip.putNextEntry(entry)
            zip.write(img.bytes)
            zip.closeEntry()
        }

        zip.close()
        return buffer.toByteArray()
    }

    private fun buildContentTypes(
        sheets: List<XlsxSheet>,
        drawingIndexOfSheet: IntArray,
        allImages: List<XlsxImage>
    ): String {
        val sb = StringBuilder(1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ")
        sb.append("ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")

        val exts = allImages.map { it.extension }.toSet()
        if ("jpg" in exts || "jpeg" in exts) {
            sb.append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>")
        }
        if ("png" in exts) {
            sb.append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        }
        if ("webp" in exts) {
            sb.append("<Default Extension=\"webp\" ContentType=\"image/webp\"/>")
        }

        sb.append("<Override PartName=\"/xl/workbook.xml\" ")
        sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        sb.append("<Override PartName=\"/xl/styles.xml\" ")
        sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        sheets.forEachIndexed { idx, _ ->
            sb.append("<Override PartName=\"/xl/worksheets/sheet${idx + 1}.xml\" ")
            sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        drawingIndexOfSheet.forEach { dIdx ->
            if (dIdx > 0) {
                sb.append("<Override PartName=\"/xl/drawings/drawing$dIdx.xml\" ")
                sb.append("ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
            }
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
     * xl/styles.xml — каноничная структура:
     * numFmts, fonts, fills, borders, cellStyleXfs, cellXfs, cellStyles,
     * dxfs, tableStyles.
     */
    private fun buildStyles(): String {
        val styles = XlsxStyles.all
        val fillMap = XlsxStyles.fillMap

        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")

        sb.append("<numFmts count=\"0\"/>")

        sb.append("<fonts count=\"3\">")
        sb.append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        sb.append("<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        sb.append("<font><color rgb=\"FF1976D2\"/><sz val=\"11\"/><u/>")
        sb.append("<name val=\"Calibri\"/></font>")
        sb.append("</fonts>")

        sb.append("<fills count=\"").append(2 + fillMap.size).append("\">")
        sb.append("<fill><patternFill patternType=\"none\"/></fill>")
        sb.append("<fill><patternFill patternType=\"gray125\"/></fill>")
        fillMap.entries.sortedBy { it.value }.forEach { (color, _) ->
            sb.append("<fill><patternFill patternType=\"solid\">")
            sb.append("<fgColor rgb=\"FF").append(color).append("\"/>")
            sb.append("<bgColor indexed=\"64\"/>")
            sb.append("</patternFill></fill>")
        }
        sb.append("</fills>")

        sb.append("<borders count=\"2\">")
        sb.append("<border><left/><right/><top/><bottom/><diagonal/></border>")
        sb.append("<border>")
        sb.append("<left style=\"thin\"/><right style=\"thin\"/>")
        sb.append("<top style=\"thin\"/><bottom style=\"thin\"/>")
        sb.append("<diagonal/></border>")
        sb.append("</borders>")

        sb.append("<cellStyleXfs count=\"").append(styles.size).append("\">")
        styles.forEach { def ->
            val fontId = XlsxStyles.fontIdFor(def)
            val fillId = XlsxStyles.fillIdFor(def)
            val borderId = if (def.border) 1 else 0
            sb.append("<xf numFmtId=\"0\" ")
            sb.append("fontId=\"").append(fontId).append("\" ")
            sb.append("fillId=\"").append(fillId).append("\" ")
            sb.append("borderId=\"").append(borderId).append("\"/>")
        }
        sb.append("</cellStyleXfs>")

        sb.append("<cellXfs count=\"").append(styles.size).append("\">")
        styles.forEachIndexed { idx, def ->
            val fontId = XlsxStyles.fontIdFor(def)
            val fillId = XlsxStyles.fillIdFor(def)
            val borderId = if (def.border) 1 else 0
            sb.append("<xf numFmtId=\"0\" ")
            sb.append("fontId=\"").append(fontId).append("\" ")
            sb.append("fillId=\"").append(fillId).append("\" ")
            sb.append("borderId=\"").append(borderId).append("\" ")
            sb.append("xfId=\"").append(idx).append("\"")
            if (fontId > 0) sb.append(" applyFont=\"1\"")
            if (fillId > 0) sb.append(" applyFill=\"1\"")
            if (borderId > 0) sb.append(" applyBorder=\"1\"")
            sb.append("/>")
        }
        sb.append("</cellXfs>")

        sb.append("<cellStyles count=\"1\">")
        sb.append("<cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/>")
        sb.append("</cellStyles>")

        sb.append("<dxfs count=\"0\"/>")
        sb.append("<tableStyles count=\"0\" ")
        sb.append("defaultTableStyle=\"TableStyleMedium9\" ")
        sb.append("defaultPivotStyle=\"PivotStyleLight16\"/>")

        sb.append("</styleSheet>")
        return sb.toString()
    }

    private fun buildSheet(sheet: XlsxSheet, drawingIndex: Int): String {
        val sb = StringBuilder(1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")

        sb.append("<dimension ref=\"A1:")
        sb.append(cellRef(maxColsOf(sheet), maxRowsOf(sheet)))
        sb.append("\"/>")

        sb.append(buildCols(sheet.rows, sheet.skipWidthRows))

        val rowHeightByIndex: Map<Int, Double> = sheet.images
            .associate { it.rowIdx to (it.heightPx * ROW_HEIGHT_PX_TO_PT) }

        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { rowIdx, row ->
            val rowNum = rowIdx + 1
            val ht = rowHeightByIndex[rowIdx]

            // Пустая строка → self-closing <row/>.
            if (row.cells.isEmpty()) {
                sb.append("<row r=\"").append(rowNum).append("\"")
                if (ht != null && ht > 0) {
                    sb.append(" ht=\"").append(ht).append("\" customHeight=\"1\"")
                }
                sb.append("/>")
                return@forEachIndexed
            }

            sb.append("<row r=\"").append(rowNum).append("\"")
            if (ht != null && ht > 0) {
                sb.append(" ht=\"").append(ht).append("\" customHeight=\"1\"")
            }
            sb.append(">")
            row.cells.forEachIndexed { colIdx, cell ->
                if (cell is XlsxCell.Empty) return@forEachIndexed
                val ref = cellRef(colIdx, rowNum)
                val effectiveStyle = cell.styleId ?: row.styleId
                val styleAttr = if (effectiveStyle != XlsxStyles.DEFAULT)
                    " s=\"$effectiveStyle\"" else ""
                when (cell) {
                    is XlsxCell.Text -> {
                        // Пустой текст — рендерим как Blank, без <is><t/>.
                        if (cell.value.isEmpty()) {
                            sb.append("<c r=\"").append(ref).append("\"")
                                .append(styleAttr).append("/>")
                        } else {
                            sb.append("<c r=\"").append(ref).append("\"")
                                .append(styleAttr)
                                .append(" t=\"inlineStr\">")
                            sb.append("<is><t xml:space=\"preserve\">")
                                .append(escapeXml(cell.value)).append("</t></is>")
                            sb.append("</c>")
                        }
                    }
                    is XlsxCell.Number -> {
                        sb.append("<c r=\"").append(ref).append("\"")
                            .append(styleAttr).append(">")
                        sb.append("<v>").append(formatNumber(cell.value)).append("</v>")
                        sb.append("</c>")
                    }
                    is XlsxCell.Blank -> {
                        sb.append("<c r=\"").append(ref).append("\"")
                            .append(styleAttr).append("/>")
                    }
                    is XlsxCell.Empty -> Unit
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")

        if (sheet.mergeCells.isNotEmpty()) {
            sb.append("<mergeCells count=\"").append(sheet.mergeCells.size).append("\">")
            sheet.mergeCells.forEach { ref ->
                sb.append("<mergeCell ref=\"").append(ref).append("\"/>")
            }
            sb.append("</mergeCells>")
        }

        if (sheet.hyperlinks.isNotEmpty()) {
            sb.append("<hyperlinks>")
            sheet.hyperlinks.forEach { h ->
                sb.append("<hyperlink ref=\"").append(h.ref).append("\" ")
                sb.append("location=\"").append(escapeXml(h.location)).append("\"/>")
            }
            sb.append("</hyperlinks>")
        }

        if (drawingIndex > 0) {
            sb.append("<drawing r:id=\"rId1\"/>")
        }

        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun maxColsOf(sheet: XlsxSheet): Int {
        val m = sheet.rows.maxOfOrNull { it.cells.size } ?: 0
        return if (m == 0) 0 else m - 1
    }

    private fun maxRowsOf(sheet: XlsxSheet): Int {
        return if (sheet.rows.isEmpty()) 1 else sheet.rows.size
    }

    private fun buildSheetRels(drawingIndex: Int): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" """ +
                """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" """ +
                """Target="../drawings/drawing$drawingIndex.xml"/>""" +
                """</Relationships>"""
    }

    private fun buildDrawing(images: List<XlsxImage>): String {
        val sb = StringBuilder(2048)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<xdr:wsDr ")
        sb.append("xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" ")
        sb.append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">")

        images.forEachIndexed { picIdx, img ->
            val cx = img.widthPx.toLong() * EMU_PER_PX
            val cy = img.heightPx.toLong() * EMU_PER_PX

            sb.append("<xdr:oneCellAnchor>")
            sb.append("<xdr:from>")
            sb.append("<xdr:col>").append(img.colIdx).append("</xdr:col>")
            sb.append("<xdr:colOff>0</xdr:colOff>")
            sb.append("<xdr:row>").append(img.rowIdx).append("</xdr:row>")
            sb.append("<xdr:rowOff>0</xdr:rowOff>")
            sb.append("</xdr:from>")
            sb.append("<xdr:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
            sb.append("<xdr:pic>")
            sb.append("<xdr:nvPicPr>")
            sb.append("<xdr:cNvPr id=\"").append(picIdx + 1)
                .append("\" name=\"Picture ").append(picIdx + 1).append("\"/>")
            sb.append("<xdr:cNvPicPr/>")
            sb.append("</xdr:nvPicPr>")
            sb.append("<xdr:blipFill>")
            sb.append("<a:blip xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" ")
            sb.append("r:embed=\"rId").append(picIdx + 1).append("\"/>")
            sb.append("<a:stretch><a:fillRect/></a:stretch>")
            sb.append("</xdr:blipFill>")
            sb.append("<xdr:spPr>")
            sb.append("<a:xfrm>")
            sb.append("<a:off x=\"0\" y=\"0\"/>")
            sb.append("<a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
            sb.append("</a:xfrm>")
            sb.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>")
            sb.append("</xdr:spPr>")
            sb.append("</xdr:pic>")
            sb.append("<xdr:clientData/>")
            sb.append("</xdr:oneCellAnchor>")
        }

        sb.append("</xdr:wsDr>")
        return sb.toString()
    }

    private fun buildDrawingRels(
        images: List<XlsxImage>,
        imageIndexOfImage: Map<XlsxImage, Int>
    ): String {
        val sb = StringBuilder(512)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        images.forEachIndexed { picIdx, img ->
            val globalIdx = imageIndexOfImage[img] ?: (picIdx + 1)
            sb.append("<Relationship Id=\"rId").append(picIdx + 1).append("\" ")
            sb.append("Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" ")
            sb.append("Target=\"../media/image").append(globalIdx).append(".")
                .append(img.extension).append("\"/>")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun buildCols(rows: List<XlsxRow>, skipRows: Int): String {
        if (rows.isEmpty()) return ""

        val maxCols = rows.maxOfOrNull { it.cells.size } ?: 0
        if (maxCols == 0) return ""

        val widths = DoubleArray(maxCols)
        rows.forEachIndexed { rowIdx, row ->
            if (rowIdx < skipRows) return@forEachIndexed
            row.cells.forEachIndexed { colIdx, cell ->
                val text = when (cell) {
                    is XlsxCell.Text -> cell.value
                    is XlsxCell.Number -> formatNumber(cell.value)
                    is XlsxCell.Blank -> ""
                    is XlsxCell.Empty -> ""
                }
                val longestLine = if (text.isEmpty()) {
                    0
                } else {
                    text.split('\n').maxOfOrNull { it.length } ?: 0
                }
                if (longestLine > widths[colIdx]) {
                    widths[colIdx] = longestLine.toDouble()
                }
            }
        }

        val sb = StringBuilder(128)
        sb.append("<cols>")
        widths.forEachIndexed { idx, w ->
            val width = (w + COL_WIDTH_PADDING)
                .coerceIn(MIN_COL_WIDTH, MAX_COL_WIDTH)
            sb.append("<col min=\"").append(idx + 1).append("\" ")
            sb.append("max=\"").append(idx + 1).append("\" ")
            sb.append("width=\"").append(width).append("\" ")
            sb.append("customWidth=\"1\"/>")
        }
        sb.append("</cols>")
        return sb.toString()
    }

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
