package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus

/**
 * FIX 5.9-report-xlsx / подзаход 4 (xlsx-links):
 * Гиперссылки внутри файла.
 *
 * Изменения:
 *  - в листе «Наряд» — новая колонка «Прил.» с ссылкой
 *    на соответствующий блок в листе «Приложения»;
 *  - в листе «Приложения» — в конце каждого блока строка
 *    «↩ К пробе <номер>» с обратной ссылкой на строку пробы.
 *
 * Формат ссылок (location): "'<ИмяЛиста>'!<Ячейка>".
 */

object XlsxReportBuilder {

    /** Номер строки с заголовками колонок в листе «Наряд». */
    private const val ORDER_HEADER_ROW = 6

    /** Первая строка данных в листе «Наряд». */
    private const val ORDER_FIRST_DATA_ROW = 7

    /** Колонка «Прил.» — 9-я, индекс 8 (0-based). */
    private const val ORDER_COL_APPENDIX = 8

    /** Колонка «№ пробы» — 2-я, индекс 1 (0-based). */
    private const val ORDER_COL_SAMPLE = 1

    /** Первая строка блока приложений (после заголовка и пустой строки). */
    private const val APP_FIRST_BLOCK_ROW = 3

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

    /** Размещение блока приложения на листе «Приложения». */
    private data class AppendixPlacement(
        val number: Int,
        val titleRow: Int
    )

    /** Размер блока приложения (сколько строк он занимает). */
    private fun appendixBlockSize(sample: ReportSample): Int {
        var size = 0
        size += 1                                  // «Приложение N»
        size += 1                                  // «№ пробы:»
        size += 1                                  // «Скважина:»
        if (sample.note != null) size += 1         // «Заметка:»
        if (sample.photos.isNotEmpty()) size += 1  // «Фото:»
        size += 1                                  // «↩ К пробе»
        size += 1                                  // пустая строка
        return size
    }

    /**
     * Собрать листы отчёта по одному наряду.
     * Порядок: [Наряд, Приложения (если есть)].
     */
    fun build(data: ReportData): List<XlsxSheet> {
        val orderSheetName = "Наряд ${data.orderNumber}"
        val appendixSheetName = "Приложения"

        val withAppendix = data.samples.filter { it.hasAppendix }

        // ============================================================
        // Шаг 1. Пре-сканирование: где будет каждый блок приложений.
        // ============================================================
        val placementById = mutableMapOf<String, AppendixPlacement>()
        var cursor = APP_FIRST_BLOCK_ROW
        withAppendix.forEachIndexed { idx, sample ->
            placementById[sample.row.id] = AppendixPlacement(
                number = idx + 1,
                titleRow = cursor
            )
            cursor += appendixBlockSize(sample)
        }

        // ============================================================
        // Шаг 2. Лист «Наряд» — с колонкой «Прил.» и ссылками.
        // ============================================================
        val orderRows = mutableListOf<XlsxRow>()
        val orderHyperlinks = mutableListOf<XlsxHyperlink>()

        orderRows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Отчёт по наряду")),
                styleId = XlsxStyles.BOLD
            )
        )
        orderRows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Участок:"), XlsxCell.Text(data.areaName))
            )
        )
        orderRows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Наряд:"), XlsxCell.Text("№${data.orderNumber}"))
            )
        )
        orderRows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Дата:"), XlsxCell.Text(data.generatedAt))
            )
        )
        orderRows.add(XlsxRow(listOf(XlsxCell.Empty)))

        orderRows.add(
            XlsxRow(
                listOf(
                    XlsxCell.Text("п/п"),
                    XlsxCell.Text("№ пробы"),
                    XlsxCell.Text("Скважина"),
                    XlsxCell.Text("Интервал"),
                    XlsxCell.Text("Вес"),
                    XlsxCell.Text("Характеристика"),
                    XlsxCell.Text("Тип"),
                    XlsxCell.Text("Найдена"),
                    XlsxCell.Text("Прил.")
                ),
                styleId = XlsxStyles.HEADER
            )
        )

        // Карта: id пробы → номер строки в листе «Наряд».
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
                        XlsxCell.Number(row.serialNumber.toDouble()),
                        XlsxCell.Text(row.sampleNumber),
                        XlsxCell.Text(row.wellNumber),
                        XlsxCell.Text(intervalText(row)),
                        XlsxCell.Text(weightText(row)),
                        XlsxCell.Text(row.characteristic),
                        XlsxCell.Text(displayType(row)),
                        XlsxCell.Text(if (row.found) "✓" else ""),
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
            hyperlinks = orderHyperlinks
        )

        // Если приложений нет — только лист наряда.
        if (withAppendix.isEmpty()) {
            return listOf(orderSheet)
        }

        // ============================================================
        // Шаг 3. Лист «Приложения» — с обратными ссылками.
        // ============================================================
        val appRows = mutableListOf<XlsxRow>()
        val appHyperlinks = mutableListOf<XlsxHyperlink>()

        appRows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Приложения")),
                styleId = XlsxStyles.BOLD
            )
        )
        appRows.add(XlsxRow(listOf(XlsxCell.Empty)))

        withAppendix.forEach { sample ->
            val placement = placementById.getValue(sample.row.id)
            val row = sample.row

            // «Приложение N» — сюда ведёт ссылка из листа наряда.
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
            }

            // «↩ К пробе» — обратная ссылка на строку пробы в листе наряда.
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
            hyperlinks = appHyperlinks
        )

        return listOf(orderSheet, appendixSheet)
    }
}