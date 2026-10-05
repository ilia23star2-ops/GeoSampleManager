package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus

/**
 * FIX 5.9-xlsx-clean (четырнадцатый заход):
 *  - убраны эмодзи. Возврат к галочке ✓.
 *  - шапка таблицы: "Найдена" вместо "Статус".
 *  - легенда без эмодзи.
 */

data class DecodedImage(
    val bytes: ByteArray,
    val extension: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedImage) return false
        return extension == other.extension && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + extension.hashCode()
        return result
    }
}

object XlsxReportBuilder {

    private const val ORDER_FIRST_DATA_ROW = 7
    private const val ORDER_COL_APPENDIX = 8
    private const val ORDER_COL_SAMPLE = 2
    private const val APP_FIRST_BLOCK_ROW = 3
    private const val ORDER_SKIP_WIDTH_ROWS = 6
    private const val COLUMNS = 9

    private const val IMAGE_WIDTH_PX = 240
    private const val IMAGE_HEIGHT_PX = 180
    private const val APP_IMAGE_COL = 1

    private val HEADER_MERGES = listOf("A1:I1", "A2:I2", "A3:I3", "A4:I4")

    private val LEGEND = listOf(
        XlsxLegendItem("Легенда", XlsxStyles.BOLD),
        XlsxLegendItem("Найдена", XlsxStyles.FOUND),
        XlsxLegendItem("Ошибка импорта", XlsxStyles.ERROR),
        XlsxLegendItem("Отложена", XlsxStyles.POSTPONED),
        XlsxLegendItem("Холостая", XlsxStyles.BLANK),
        XlsxLegendItem("Весовой контроль", XlsxStyles.CONTROL)
    )

    private fun headerRow(text: String, style: Int): XlsxRow {
        val cells = ArrayList<XlsxCell>(COLUMNS)
        cells.add(XlsxCell.Text(text, style))
        repeat(COLUMNS - 1) {
            cells.add(XlsxCell.Blank(style))
        }
        return XlsxRow(cells, styleId = style)
    }

    private fun displayType(row: SampleRow): String = when (row.status) {
        SampleStatus.BLANK -> "Холостая"
        SampleStatus.CONTROL -> "Весовой контроль"
        SampleStatus.NORMAL -> row.type.title
    }

    private fun intervalText(row: SampleRow): String {
        val from = row.intervalFrom
        val to = row.intervalTo
        return if (from == "—" && to == "—") "—" else "$from–$to"
    }

    private fun weightText(row: SampleRow): String {
        val w = row.weight
        val cw = row.controlWeight
        return when {
            w != null && cw != null -> "$w ($cw)"
            w != null -> w.toString()
            cw != null -> "($cw)"
            else -> "—"
        }
    }

    private data class AppendixPlacement(
        val number: Int,
        val titleRow: Int
    )

    private fun appendixBlockSize(sample: ReportSample): Int {
        var size = 0
        size += 1
        size += 1
        size += 1
        if (sample.note != null) size += 1
        if (sample.photos.isNotEmpty()) {
            size += 1
            size += sample.photos.size
        }
        size += 1
        size += 1
        return size
    }

    fun build(
        data: ReportData,
        imageDecoder: ((String) -> DecodedImage?)? = null
    ): List<XlsxSheet> {
        val orderSheetName = "Наряд ${data.orderNumber}"
        val appendixSheetName = "Приложения"

        val withAppendix = data.samples.filter { it.hasAppendix }

        val placementById = mutableMapOf<String, AppendixPlacement>()
        var cursor = APP_FIRST_BLOCK_ROW
        withAppendix.forEachIndexed { idx, sample ->
            placementById[sample.row.id] = AppendixPlacement(
                number = idx + 1,
                titleRow = cursor
            )
            cursor += appendixBlockSize(sample)
        }

        val orderRows = mutableListOf<XlsxRow>()
        val orderHyperlinks = mutableListOf<XlsxHyperlink>()

        orderRows.add(headerRow("Отчёт по наряду", XlsxStyles.TITLE))
        orderRows.add(headerRow("Участок: ${data.areaName}", XlsxStyles.META))
        orderRows.add(headerRow("Наряд: №${data.orderNumber}", XlsxStyles.META))
        orderRows.add(headerRow("Дата: ${data.generatedAt}", XlsxStyles.META))
        orderRows.add(XlsxRow(listOf(XlsxCell.Empty)))

        orderRows.add(
            XlsxRow(
                listOf(
                    XlsxCell.Text("Найдена"),
                    XlsxCell.Text("п/п"),
                    XlsxCell.Text("№ пробы"),
                    XlsxCell.Text("Скважина"),
                    XlsxCell.Text("Интервал"),
                    XlsxCell.Text("Вес"),
                    XlsxCell.Text("Характеристика"),
                    XlsxCell.Text("Тип"),
                    XlsxCell.Text("Прил.")
                ),
                styleId = XlsxStyles.HEADER
            )
        )

        val orderRowById = data.samples.mapIndexed { idx, s ->
            s.row.id to (ORDER_FIRST_DATA_ROW + idx)
        }.toMap()

        data.samples.forEach { sample ->
            val row = sample.row
            val rowNum = orderRowById.getValue(row.id)
            val placement = placementById[row.id]

            val appendixCell: XlsxCell = if (placement != null) {
                XlsxCell.Text("Прил. ${placement.number}", XlsxStyles.LINK)
            } else {
                XlsxCell.Text("—")
            }

            orderRows.add(
                XlsxRow(
                    listOf(
                        XlsxCell.Text(if (row.found) "✓" else ""),
                        XlsxCell.Number(row.serialNumber.toDouble()),
                        XlsxCell.Text(row.sampleNumber),
                        XlsxCell.Text(row.wellNumber),
                        XlsxCell.Text(intervalText(row)),
                        XlsxCell.Text(weightText(row)),
                        XlsxCell.Text(row.characteristic),
                        XlsxCell.Text(displayType(row)),
                        appendixCell
                    ),
                    styleId = XlsxStyles.styleForRow(row)
                )
            )

            if (placement != null) {
                orderHyperlinks.add(
                    XlsxHyperlink(
                        ref = XlsxWriter.cellRef(ORDER_COL_APPENDIX, rowNum),
                        location = "'$appendixSheetName'!A${placement.titleRow}"
                    )
                )
            }
        }

        val orderSheet = XlsxSheet(
            name = orderSheetName,
            rows = orderRows,
            hyperlinks = orderHyperlinks,
            images = emptyList(),
            skipWidthRows = ORDER_SKIP_WIDTH_ROWS,
            mergeCells = HEADER_MERGES,
            legend = LEGEND
        )

        if (withAppendix.isEmpty()) {
            return listOf(orderSheet)
        }

        val appRows = mutableListOf<XlsxRow>()
        val appHyperlinks = mutableListOf<XlsxHyperlink>()
        val appImages = mutableListOf<XlsxImage>()

        appRows.add(headerRow("Приложения", XlsxStyles.TITLE))
        appRows.add(XlsxRow(listOf(XlsxCell.Empty)))

        withAppendix.forEach { sample ->
            val placement = placementById.getValue(sample.row.id)
            val row = sample.row

            appRows.add(
                XlsxRow(
                    listOf(XlsxCell.Text("Приложение ${placement.number}")),
                    styleId = XlsxStyles.BOLD
                )
            )
            appRows.add(
                XlsxRow(
                    listOf(
                        XlsxCell.Text("№ пробы:"),
                        XlsxCell.Text(row.sampleNumber)
                    )
                )
            )
            appRows.add(
                XlsxRow(
                    listOf(
                        XlsxCell.Text("Скважина:"),
                        XlsxCell.Text(row.wellNumber)
                    )
                )
            )

            sample.note?.let { note ->
                appRows.add(
                    XlsxRow(
                        listOf(
                            XlsxCell.Text("Заметка:"),
                            XlsxCell.Text(note.text)
                        )
                    )
                )
            }

            if (sample.photos.isNotEmpty()) {
                appRows.add(
                    XlsxRow(
                        listOf(
                            XlsxCell.Text("Фото:"),
                            XlsxCell.Text("${sample.photos.size} шт.")
                        )
                    )
                )
                sample.photos.forEach { photo ->
                    val imageRowIdx = appRows.size
                    val decoded = imageDecoder?.invoke(photo.dataUri)
                    if (decoded != null) {
                        appImages.add(
                            XlsxImage(
                                bytes = decoded.bytes,
                                extension = decoded.extension,
                                colIdx = APP_IMAGE_COL,
                                rowIdx = imageRowIdx,
                                widthPx = IMAGE_WIDTH_PX,
                                heightPx = IMAGE_HEIGHT_PX
                            )
                        )
                    }
                    appRows.add(XlsxRow(listOf(XlsxCell.Empty)))
                }
            }

            val backLinkRowNum = appRows.size + 1
            appRows.add(
                XlsxRow(
                    listOf(
                        XlsxCell.Text("↩ К пробе ${row.sampleNumber}", XlsxStyles.LINK)
                    )
                )
            )

            val orderRowNum = orderRowById.getValue(row.id)
            appHyperlinks.add(
                XlsxHyperlink(
                    ref = XlsxWriter.cellRef(0, backLinkRowNum),
                    location = "'$orderSheetName'!" +
                            XlsxWriter.cellRef(ORDER_COL_SAMPLE, orderRowNum)
                )
            )

            appRows.add(XlsxRow(listOf(XlsxCell.Empty)))
        }

        val appendixSheet = XlsxSheet(
            name = appendixSheetName,
            rows = appRows,
            hyperlinks = appHyperlinks,
            images = appImages,
            skipWidthRows = 0
        )

        return listOf(orderSheet, appendixSheet)
    }
}
