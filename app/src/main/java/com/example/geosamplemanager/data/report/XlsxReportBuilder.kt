package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus

/**
 * FIX 5.9-report-xlsx / подзаход 3 (xlsx-styles):
 * Назначение стилей строкам.
 *
 * Изменения:
 *  - заголовок «Отчёт по наряду» — BOLD;
 *  - шапка таблицы — HEADER (жирный + фон);
 *  - строки данных — по статусу пробы (styleForRow).
 */

object XlsxReportBuilder {

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

    fun build(data: ReportData): List<XlsxSheet> {
        val result = mutableListOf<XlsxSheet>()

        // ============================================================
        // ЛИСТ 1 — НАРЯД
        // ============================================================
        val orderRows = mutableListOf<XlsxRow>()

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
                    XlsxCell.Text("Найдена")
                ),
                styleId = XlsxStyles.HEADER
            )
        )

        data.samples.forEach { sample ->
            val row = sample.row
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
                        XlsxCell.Text(if (row.found) "✓" else "")
                    ),
                    styleId = XlsxStyles.styleForRow(row)
                )
            )
        }

        result.add(XlsxSheet(name = "Наряд ${data.orderNumber}", rows = orderRows))

        // ============================================================
        // ЛИСТ 2 — ПРИЛОЖЕНИЯ (если есть)
        // ============================================================
        val withAppendix = data.samples.filter { it.hasAppendix }

        if (withAppendix.isNotEmpty()) {
            val appRows = mutableListOf<XlsxRow>()
            appRows.add(
                XlsxRow(listOf(XlsxCell.Text("Приложения")), styleId = XlsxStyles.BOLD)
            )
            appRows.add(XlsxRow(listOf(XlsxCell.Empty)))

            withAppendix.forEachIndexed { idx, sample ->
                appRows.add(
                    XlsxRow(listOf(XlsxCell.Text("Приложение ${idx + 1}")), styleId = XlsxStyles.BOLD)
                )
                appRows.add(
                    XlsxRow(
                        listOf(
                            XlsxCell.Text("№ пробы:"),
                            XlsxCell.Text(sample.row.sampleNumber)
                        )
                    )
                )
                appRows.add(
                    XlsxRow(
                        listOf(
                            XlsxCell.Text("Скважина:"),
                            XlsxCell.Text(sample.row.wellNumber)
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

                appRows.add(XlsxRow(listOf(XlsxCell.Empty)))
            }

            result.add(XlsxSheet(name = "Приложения", rows = appRows))
        }

        return result
    }
}