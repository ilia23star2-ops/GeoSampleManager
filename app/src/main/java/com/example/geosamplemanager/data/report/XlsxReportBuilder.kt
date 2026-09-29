package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus

/**
 * FIX 5.9-report-xlsx / подзаход 2 (xlsx-cells):
 * Сборка листов .xlsx из ReportData.
 *
 * Что делает:
 *  - лист с нарядом: шапка + таблица проб (п/п, № пробы, скважина,
 *    интервал, вес, характеристика, тип, найдена);
 *  - отдельный лист «Приложения» — если есть пробы с заметками/фото.
 *
 * Что НЕ делает (следующие подзаходы):
 *  - стили (цвет строк, жирный шрифт) — подзаход 3;
 *  - гиперссылки из строки на лист приложений — подзаход 4;
 *  - мультинарядный (N листов) — подзаход 5;
 *  - UI-кнопка — подзаход 6.
 *
 * Ссылки на фото пока не делаем: у ReportPhoto нет имени файла,
 * только dataUri. Пишем количество: «3 шт.».
 */

object XlsxReportBuilder {

    /** Статус → строка для колонки «Тип». */
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

    /**
     * Собрать все листы для отчёта по одному наряду.
     * Первый лист — наряд. Второй — приложения (если есть).
     */
    fun build(data: ReportData): List<XlsxSheet> {
        val result = mutableListOf<XlsxSheet>()

        // ============================================================
        // ЛИСТ 1 — НАРЯД
        // ============================================================
        val orderRows = mutableListOf<List<XlsxCell>>()

        orderRows.add(listOf(XlsxCell.Text("Отчёт по наряду")))
        orderRows.add(
            listOf(
                XlsxCell.Text("Участок:"),
                XlsxCell.Text(data.areaName)
            )
        )
        orderRows.add(
            listOf(
                XlsxCell.Text("Наряд:"),
                XlsxCell.Text("№${data.orderNumber}")
            )
        )
        orderRows.add(
            listOf(
                XlsxCell.Text("Дата:"),
                XlsxCell.Text(data.generatedAt)
            )
        )
        orderRows.add(listOf(XlsxCell.Empty))

        orderRows.add(
            listOf(
                XlsxCell.Text("п/п"),
                XlsxCell.Text("№ пробы"),
                XlsxCell.Text("Скважина"),
                XlsxCell.Text("Интервал"),
                XlsxCell.Text("Вес"),
                XlsxCell.Text("Характеристика"),
                XlsxCell.Text("Тип"),
                XlsxCell.Text("Найдена")
            )
        )

        data.samples.forEach { sample ->
            val row = sample.row
            orderRows.add(
                listOf(
                    XlsxCell.Number(row.serialNumber.toDouble()),
                    XlsxCell.Text(row.sampleNumber),
                    XlsxCell.Text(row.wellNumber),
                    XlsxCell.Text(intervalText(row)),
                    XlsxCell.Text(weightText(row)),
                    XlsxCell.Text(row.characteristic),
                    XlsxCell.Text(displayType(row)),
                    XlsxCell.Text(if (row.found) "✓" else "")
                )
            )
        }

        val orderSheetName = "Наряд ${data.orderNumber}"
        result.add(XlsxSheet(name = orderSheetName, rows = orderRows))

        // ============================================================
        // ЛИСТ 2 — ПРИЛОЖЕНИЯ (если есть)
        // ============================================================
        val withAppendix = data.samples.filter { it.hasAppendix }

        if (withAppendix.isNotEmpty()) {
            val appRows = mutableListOf<List<XlsxCell>>()
            appRows.add(listOf(XlsxCell.Text("Приложения")))
            appRows.add(listOf(XlsxCell.Empty))

            withAppendix.forEachIndexed { idx, sample ->
                appRows.add(listOf(XlsxCell.Text("Приложение ${idx + 1}")))
                appRows.add(
                    listOf(
                        XlsxCell.Text("№ пробы:"),
                        XlsxCell.Text(sample.row.sampleNumber)
                    )
                )
                appRows.add(
                    listOf(
                        XlsxCell.Text("Скважина:"),
                        XlsxCell.Text(sample.row.wellNumber)
                    )
                )

                sample.note?.let { note ->
                    appRows.add(
                        listOf(
                            XlsxCell.Text("Заметка:"),
                            XlsxCell.Text(note.text)
                        )
                    )
                }

                if (sample.photos.isNotEmpty()) {
                    appRows.add(
                        listOf(
                            XlsxCell.Text("Фото:"),
                            XlsxCell.Text("${sample.photos.size} шт.")
                        )
                    )
                }

                appRows.add(listOf(XlsxCell.Empty))
            }

            result.add(XlsxSheet(name = "Приложения", rows = appRows))
        }

        return result
    }
}