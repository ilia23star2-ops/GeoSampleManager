package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus

/**
 * FIX 5.9-xlsx-emoji (тринадцатый заход):
 *  - эмодзи-статус в колонке A на каждом листе наряда;
 *  - шапка: "Статус" вместо "Найдена".
 */

object XlsxMultiReportBuilder {

    private const val APPENDIX_SHEET_NAME = "Приложения"
    private const val ORDER_FIRST_DATA_ROW = 7
    private const val ORDER_COL_APPENDIX = 8
    private const val ORDER_COL_SAMPLE = 2
    private const val APP_FIRST_BLOCK_ROW = 3
    private const val SHEET_NAME_MAX = 31
    private const val ORDER_SKIP_WIDTH_ROWS = 6
    private const val COLUMNS = 9

    private const val IMAGE_WIDTH_PX = 240
    private const val IMAGE_HEIGHT_PX = 180
    private const val APP_IMAGE_COL = 1

    private val HEADER_MERGES = listOf("A1:I1", "A2:I2", "A3:I3", "A4:I4")

    private val LEGEND = listOf(
        XlsxLegendItem("Легенда", XlsxStyles.BOLD),
        XlsxLegendItem("🟢 Найдена", XlsxStyles.FOUND),
        XlsxLegendItem("🔴 Ошибка импорта", XlsxStyles.ERROR),
        XlsxLegendItem("🔵 Отложена", XlsxStyles.POSTPONED),
        XlsxLegendItem("🟡 Холостая", XlsxStyles.BLANK),
        XlsxLegendItem("🟣 Весовой контроль", XlsxStyles.CONTROL)
    )

    private fun statusEmoji(row: SampleRow): String = when {
        row.found -> "🟢"
        row.hasImportError -> "🔴"
        row.postponed -> "🔵"
        row.isBlank -> "🟡"
        row.weightControl -> "🟣"
        else -> "⚪"
    }

    private fun headerRow(text: String, style: Int): XlsxRow {
        val cells = ArrayList<XlsxCell>(COLUMNS)
        cells.add(XlsxCell.Text(text, style))
        repeat(COLUMNS - 1) {
            cells.add(XlsxCell.Blank(style))
        }
        return XlsxRow(cells, styleId = style)
    }

    private data class BlockPlacement(
        val orderIdx: Int,
        val sampleId: String,
        val number: Int,
        val titleRow: Int
    )

    fun build(
        orders: List<ReportData>,
        imageDecoder: ((String) -> DecodedImage?)? = null
    ): List<XlsxSheet> {
        if (orders.isEmpty()) return emptyList()

        val orderSheetNames = uniqueSheetNames(orders)

        val placements = mutableListOf<BlockPlacement>()
        var cursor = APP_FIRST_BLOCK_ROW
        var number = 1
        orders.forEachIndexed { orderIdx, data ->
            data.samples.forEach { sample ->
                if (sample.hasAppendix) {
                    placements.add(
                        BlockPlacement(
                            orderIdx = orderIdx,
                            sampleId = sample.row.id,
                            number = number,
                            titleRow = cursor
                        )
                    )
                    cursor += appendixBlockSize(sample)
                    number++
                }
            }
        }
        val placementByKey = placements.associateBy { it.orderIdx to it.sampleId }

        val orderRowById: List<Map<String, Int>> = orders.map { data ->
            data.samples.mapIndexed { i, s ->
                s.row.id to (ORDER_FIRST_DATA_ROW + i)
            }.toMap()
        }

        val orderSheets = orders.mapIndexed { orderIdx, data ->
            buildOrderSheet(
                sheetName = orderSheetNames[orderIdx],
                data = data,
                orderIdx = orderIdx,
                placementByKey = placementByKey
            )
        }

        val appendixSheet = if (placements.isEmpty()) {
            null
        } else {
            buildAppendixSheet(
                orders = orders,
                orderSheetNames = orderSheetNames,
                orderRowById = orderRowById,
                placementByKey = placementByKey,
                imageDecoder = imageDecoder
            )
        }

        return if (appendixSheet == null) {
            orderSheets
        } else {
            orderSheets + appendixSheet
        }
    }

    private fun buildOrderSheet(
        sheetName: String,
        data: ReportData,
        orderIdx: Int,
        placementByKey: Map<Pair<Int, String>, BlockPlacement>
    ): XlsxSheet {
        val rows = mutableListOf<XlsxRow>()
        val links = mutableListOf<XlsxHyperlink>()

        rows.add(headerRow("Отчёт по наряду", XlsxStyles.TITLE))
        rows.add(headerRow("Участок: ${data.areaName}", XlsxStyles.META))
        rows.add(headerRow("Наряд: №${data.orderNumber}", XlsxStyles.META))
        rows.add(headerRow("Дата: ${data.generatedAt}", XlsxStyles.META))
        rows.add(XlsxRow(listOf(XlsxCell.Empty)))

        rows.add(
            XlsxRow(
                listOf(
                    XlsxCell.Text("Статус"),
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

        val rowById = data.samples.mapIndexed { i, s ->
            s.row.id to (ORDER_FIRST_DATA_ROW + i)
        }.toMap()

        data.samples.forEach { sample ->
            val row = sample.row
            val rowNum = rowById.getValue(row.id)
            val placement = placementByKey[orderIdx to row.id]

            val appendixCell: XlsxCell = if (placement != null) {
                XlsxCell.Text("Прил. ${placement.number}", XlsxStyles.LINK)
            } else {
                XlsxCell.Text("—")
            }

            rows.add(
                XlsxRow(
                    listOf(
                        XlsxCell.Text(statusEmoji(row)),
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
                links.add(
                    XlsxHyperlink(
                        ref = XlsxWriter.cellRef(ORDER_COL_APPENDIX, rowNum),
                        location = "'$APPENDIX_SHEET_NAME'!A${placement.titleRow}"
                    )
                )
            }
        }

        return XlsxSheet(
            name = sheetName,
            rows = rows,
            hyperlinks = links,
            images = emptyList(),
            skipWidthRows = ORDER_SKIP_WIDTH_ROWS,
            mergeCells = HEADER_MERGES,
            legend = LEGEND
        )
    }

    private fun buildAppendixSheet(
        orders: List<ReportData>,
        orderSheetNames: List<String>,
        orderRowById: List<Map<String, Int>>,
        placementByKey: Map<Pair<Int, String>, BlockPlacement>,
        imageDecoder: ((String) -> DecodedImage?)?
    ): XlsxSheet {
        val rows = mutableListOf<XlsxRow>()
        val links = mutableListOf<XlsxHyperlink>()
        val images = mutableListOf<XlsxImage>()

        rows.add(headerRow("Приложения", XlsxStyles.TITLE))
        rows.add(XlsxRow(listOf(XlsxCell.Empty)))

        orders.forEachIndexed { orderIdx, data ->
            val rowById = orderRowById[orderIdx]
            val targetSheetName = orderSheetNames[orderIdx]

            data.samples.forEach { sample ->
                val placement = placementByKey[orderIdx to sample.row.id]
                    ?: return@forEach
                val row = sample.row
                val orderRowNum = rowById[row.id] ?: return@forEach

                rows.add(
                    XlsxRow(
                        listOf(XlsxCell.Text("Приложение ${placement.number}")),
                        styleId = XlsxStyles.BOLD
                    )
                )
                rows.add(
                    XlsxRow(
                        listOf(
                            XlsxCell.Text("№ пробы:"),
                            XlsxCell.Text(row.sampleNumber)
                        )
                    )
                )
                rows.add(
                    XlsxRow(
                        listOf(
                            XlsxCell.Text("Скважина:"),
                            XlsxCell.Text(row.wellNumber)
                        )
                    )
                )

                sample.note?.let { note ->
                    rows.add(
                        XlsxRow(
                            listOf(
                                XlsxCell.Text("Заметка:"),
                                XlsxCell.Text(note.text)
                            )
                        )
                    )
                }

                if (sample.photos.isNotEmpty()) {
                    rows.add(
                        XlsxRow(
                            listOf(
                                XlsxCell.Text("Фото:"),
                                XlsxCell.Text("${sample.photos.size} шт.")
                            )
                        )
                    )
                    sample.photos.forEach { photo ->
                        val imageRowIdx = rows.size
                        val decoded = imageDecoder?.invoke(photo.dataUri)
                        if (decoded != null) {
                            images.add(
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
                        rows.add(XlsxRow(listOf(XlsxCell.Empty)))
                    }
                }

                val backLinkRowNum = rows.size + 1
                rows.add(
                    XlsxRow(
                        listOf(
                            XlsxCell.Text(
                                "↩ К пробе ${row.sampleNumber}",
                                XlsxStyles.LINK
                            )
                        )
                    )
                )
                links.add(
                    XlsxHyperlink(
                        ref = XlsxWriter.cellRef(0, backLinkRowNum),
                        location = "'$targetSheetName'!" +
                                XlsxWriter.cellRef(ORDER_COL_SAMPLE, orderRowNum)
                    )
                )

                rows.add(XlsxRow(listOf(XlsxCell.Empty)))
            }
        }

        return XlsxSheet(
            name = APPENDIX_SHEET_NAME,
            rows = rows,
            hyperlinks = links,
            images = images,
            skipWidthRows = 0
        )
    }

    private fun uniqueSheetNames(orders: List<ReportData>): List<String> {
        val used = mutableSetOf<String>()
        return orders.mapIndexed { idx, data ->
            val base = if (data.areaName.isBlank()) {
                "Наряд ${data.orderNumber}"
            } else {
                "${data.areaName} — Наряд ${data.orderNumber}"
            }
            val sanitized = XlsxWriter.sanitizeSheetName(base, idx + 1)

            if (used.add(sanitized)) {
                return@mapIndexed sanitized
            }

            var n = 2
            var attempt = sanitized
            while (attempt in used) {
                val suffix = " ($n)"
                val maxBase = SHEET_NAME_MAX - suffix.length
                val truncated = if (sanitized.length > maxBase) {
                    sanitized.substring(0, maxBase)
                } else {
                    sanitized
                }
                attempt = truncated + suffix
                n++
            }
            used.add(attempt)
            attempt
        }
    }

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
}
