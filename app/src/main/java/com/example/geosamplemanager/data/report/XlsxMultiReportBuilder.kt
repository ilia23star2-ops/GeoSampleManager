package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus

/**
 * FIX 5.9-report-xlsx / подзаход 5 (xlsx-multi):
 * Мультинарядный отчёт. N нарядов → N листов + один общий лист
 * «Приложения» в конце.
 *
 * Отличия от XlsxReportBuilder:
 *  - один файл — много листов;
 *  - лист «Приложения» один общий, блоки идут в порядке нарядов,
 *    нумерация сквозная (Приложение 1, 2, 3, ...);
 *  - имена листов — «<Участок> — Наряд <номер>» с защитой от дублей
 *    (суффикс « (2)», « (3)», ...);
 *  - обратные ссылки из блока приложений ведут в лист своего наряда.
 *
 * Порядок нарядов и их состав определяет вызывающая сторона (UI).
 * Билдер — чистая функция: List<ReportData> → List<XlsxSheet>.
 */

object XlsxMultiReportBuilder {

    /** Имя общего листа приложений. */
    private const val APPENDIX_SHEET_NAME = "Приложения"

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

    /** Максимальная длина имени листа в Excel. */
    private const val SHEET_NAME_MAX = 31

    /** Размещение блока приложения на общем листе. */
    private data class BlockPlacement(
        val orderIdx: Int,
        val sampleId: String,
        val number: Int,
        val titleRow: Int
    )

    // ================================================================
    // Публичный API
    // ================================================================

    /**
     * Собрать листы мультинарядного отчёта.
     *
     * @param orders список нарядов в нужном порядке.
     * @return листы: сначала все наряды, затем один общий «Приложения»
     *   (если есть хоть одно приложение). Пустой вход → пустой список.
     */
    fun build(orders: List<ReportData>): List<XlsxSheet> {
        if (orders.isEmpty()) return emptyList()

        val orderSheetNames = uniqueSheetNames(orders)

        // ============================================================
        // Шаг 1. Пре-сканирование блоков приложений.
        // ============================================================
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

        // Карта строк листа наряда: orderIdx → (sampleId → rowNum).
        val orderRowById: List<Map<String, Int>> = orders.map { data ->
            data.samples.mapIndexed { i, s ->
                s.row.id to (ORDER_FIRST_DATA_ROW + i)
            }.toMap()
        }

        // ============================================================
        // Шаг 2. Листы нарядов.
        // ============================================================
        val orderSheets = orders.mapIndexed { orderIdx, data ->
            buildOrderSheet(
                sheetName = orderSheetNames[orderIdx],
                data = data,
                orderIdx = orderIdx,
                placementByKey = placementByKey
            )
        }

        // ============================================================
        // Шаг 3. Общий лист приложений (если есть).
        // ============================================================
        val appendixSheet = if (placements.isEmpty()) {
            null
        } else {
            buildAppendixSheet(
                orders = orders,
                orderSheetNames = orderSheetNames,
                orderRowById = orderRowById,
                placementByKey = placementByKey
            )
        }

        return if (appendixSheet == null) {
            orderSheets
        } else {
            orderSheets + appendixSheet
        }
    }

    // ================================================================
    // Лист наряда
    // ================================================================

    private fun buildOrderSheet(
        sheetName: String,
        data: ReportData,
        orderIdx: Int,
        placementByKey: Map<Pair<Int, String>, BlockPlacement>
    ): XlsxSheet {
        val rows = mutableListOf<XlsxRow>()
        val links = mutableListOf<XlsxHyperlink>()

        rows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Отчёт по наряду")),
                styleId = XlsxStyles.BOLD
            )
        )
        rows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Участок:"), XlsxCell.Text(data.areaName))
            )
        )
        rows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Наряд:"), XlsxCell.Text("№${data.orderNumber}"))
            )
        )
        rows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Дата:"), XlsxCell.Text(data.generatedAt))
            )
        )
        rows.add(XlsxRow(listOf(XlsxCell.Empty)))

        rows.add(
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
                links.add(
                    XlsxHyperlink(
                        ref = XlsxWriter.cellRef(ORDER_COL_APPENDIX, rowNum),
                        location = "'$APPENDIX_SHEET_NAME'!A${placement.titleRow}"
                    )
                )
            }
        }

        return XlsxSheet(name = sheetName, rows = rows, hyperlinks = links)
    }

    // ================================================================
    // Общий лист приложений
    // ================================================================

    private fun buildAppendixSheet(
        orders: List<ReportData>,
        orderSheetNames: List<String>,
        orderRowById: List<Map<String, Int>>,
        placementByKey: Map<Pair<Int, String>, BlockPlacement>
    ): XlsxSheet {
        val rows = mutableListOf<XlsxRow>()
        val links = mutableListOf<XlsxHyperlink>()

        rows.add(
            XlsxRow(
                listOf(XlsxCell.Text("Приложения")),
                styleId = XlsxStyles.BOLD
            )
        )
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

        return XlsxSheet(name = APPENDIX_SHEET_NAME, rows = rows, hyperlinks = links)
    }

    // ================================================================
    // Имена листов
    // ================================================================

    /**
     * Имена листов нарядов: «<Участок> — Наряд <номер>».
     * Дубли (совпали участок+номер) — суффикс « (2)», « (3)», ...
     * с сохранением лимита в 31 символ.
     */
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

    // ================================================================
    // Вспомогательные
    // ================================================================

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