package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.GroupStats
import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus
import com.example.geosamplemanager.ui.screens.SampleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-report-html-tests:
 * Тесты одиночного HTML-генератора.
 *
 * Покрываем: структуру документа, шапку, сводку, таблицу проб,
 * цвета строк, гиперссылки «Прил. N» ↔ «↩ К пробе», экранирование,
 * фото base64, пустой случай.
 *
 * Не покрываем: CSS, шрифты, вёрстку — это визуал, только device.
 */
class ReportHtmlGeneratorTest {

    private fun makeRow(
        id: String = "1",
        serial: Int = 1,
        well: String = "NV1366",
        sample: String = "NV136601",
        numberInWell: Int = 1,
        from: String = "0.2",
        to: String = "2.0",
        weight: Double? = 2.5,
        controlWeight: Double? = null,
        type: SampleType = SampleType.AUGER,
        status: SampleStatus = SampleStatus.NORMAL,
        characteristic: String = "Делювий",
        found: Boolean = true,
        postponed: Boolean = false,
        weightControl: Boolean = false,
        hasNote: Boolean = false,
        hasPhoto: Boolean = false,
        hasImportError: Boolean = false
    ) = SampleRow(
        id = id,
        groupId = "g1",
        serialNumber = serial,
        wellNumber = well,
        sampleNumber = sample,
        numberInWell = numberInWell,
        intervalFrom = from,
        intervalTo = to,
        weight = weight,
        controlWeight = controlWeight,
        type = type,
        status = status,
        characteristic = characteristic,
        found = found,
        postponed = postponed,
        weightControl = weightControl,
        hasNote = hasNote,
        hasPhoto = hasPhoto,
        hasImportError = hasImportError
    )

    private fun makeReport(
        area: String = "Коптеловский",
        order: String = "27",
        samples: List<ReportSample>,
        stats: GroupStats = GroupStats()
    ) = ReportData(
        areaName = area,
        orderNumber = order,
        generatedAt = "29.09.2026 21:00",
        stats = stats,
        samples = samples
    )

    private fun sampleNoAppendix(id: String = "1", sample: String = "NV136601") =
        ReportSample(
            row = makeRow(id = id, sample = sample),
            note = null,
            photos = emptyList()
        )

    private fun sampleWithNote(
        id: String = "1",
        sample: String = "NV136601",
        note: String = "Заметка"
    ) = ReportSample(
        row = makeRow(id = id, sample = sample, hasNote = true),
        note = ReportNote(note),
        photos = emptyList()
    )

    private fun sampleWithPhoto(
        id: String = "1",
        sample: String = "NV136601",
        photos: Int = 1
    ) = ReportSample(
        row = makeRow(id = id, sample = sample, hasPhoto = true),
        note = null,
        photos = List(photos) { ReportPhoto("data:image/jpeg;base64,AAA$it") }
    )

    // ================================================================
    // Структура документа
    // ================================================================

    @Test
    fun documentStartsWithDoctypeAndHasHtmlLangRu() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()))
        )
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<html lang=\"ru\">"))
        assertTrue(html.contains("<meta charset=\"UTF-8\">"))
        assertTrue(html.contains("</html>"))
    }

    @Test
    fun titleContainsOrderNumber() {
        val html = ReportHtmlGenerator.generate(
            makeReport(order = "27", samples = listOf(sampleNoAppendix()))
        )
        assertTrue(html.contains("<title>Отчёт · Наряд №27</title>"))
    }

    @Test
    fun toolbarHasPrintButton() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()))
        )
        assertTrue(html.contains("window.print()"))
        assertTrue(html.contains("no-print"))
    }

    // ================================================================
    // Шапка
    // ================================================================

    @Test
    fun headerContainsAreaOrderDate() {
        val html = ReportHtmlGenerator.generate(
            makeReport(
                area = "Актайский",
                order = "13",
                samples = listOf(sampleNoAppendix())
            )
        )
        assertTrue(html.contains("<b>Участок:</b> Актайский"))
        assertTrue(html.contains("<b>Наряд:</b> №13"))
        assertTrue(html.contains("<b>Дата:</b> 29.09.2026 21:00"))
    }

    @Test
    fun summaryShowsTotalFoundNotFound() {
        val stats = GroupStats(
            total = 10, found = 6, notFound = 2,
            blanks = 1, weightControls = 1,
            postponed = 0, errors = 0
        )
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()), stats = stats)
        )
        assertTrue(html.contains(">10</b> Всего"))
        assertTrue(html.contains(">6</b> Найдено"))
        assertTrue(html.contains(">2</b> Не найдено"))
    }

    @Test
    fun summaryShowsErrorsOnlyWhenPresent() {
        val noErrors = GroupStats(total = 5, found = 5, errors = 0)
        val html1 = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()), stats = noErrors)
        )
        assertFalse(html1.contains("Ошибки"))

        val withErrors = GroupStats(total = 5, found = 3, errors = 2)
        val html2 = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()), stats = withErrors)
        )
        assertTrue(html2.contains(">2</b> Ошибки"))
    }

    @Test
    fun progressPercentPresentInHeader() {
        // total=10, errors=0, found=6 → 60.0%
        val stats = GroupStats(total = 10, found = 6, errors = 0)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()), stats = stats)
        )
        assertTrue(html.contains("class=\"progress-pct\">60.0%"))
    }

    // ================================================================
    // Таблица проб
    // ================================================================

    @Test
    fun tableHasNineHeaders() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()))
        )
        assertTrue(html.contains("<th>✓</th>"))
        assertTrue(html.contains("<th>п/п</th>"))
        assertTrue(html.contains("<th>№ пробы</th>"))
        assertTrue(html.contains("<th>Скважина</th>"))
        assertTrue(html.contains("<th>Интервал</th>"))
        assertTrue(html.contains("<th>Вес</th>"))
        assertTrue(html.contains("<th>Характеристика</th>"))
        assertTrue(html.contains("<th>Тип</th>"))
        assertTrue(html.contains("<th>Прил.</th>"))
    }

    @Test
    fun sampleRowContainsValues() {
        val row = makeRow(
            serial = 3,
            well = "NV1367",
            sample = "NV136703",
            from = "1.5",
            to = "3.0",
            weight = 2.7,
            characteristic = "Элювий",
            type = SampleType.CHANNEL,
            found = true
        )
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains(">NV136703<"))
        assertTrue(html.contains(">NV1367<"))
        assertTrue(html.contains(">1.5–3.0<"))
        assertTrue(html.contains(">2.7<"))
        assertTrue(html.contains(">Элювий<"))
        assertTrue(html.contains(">Бороздовая<"))
    }

    @Test
    fun checkmarkShownOnlyWhenFound() {
        val found = makeRow(found = true)
        val html1 = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(found, null, emptyList())))
        )
        assertTrue(html1.contains("class=\"c-check\">✓"))

        val notFound = makeRow(found = false)
        val html2 = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(notFound, null, emptyList())))
        )
        assertFalse(html2.contains("class=\"c-check\">✓"))
    }

    @Test
    fun blankStatusOverridesType() {
        val row = makeRow(status = SampleStatus.BLANK, type = SampleType.AUGER)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains(">Холостая<"))
    }

    @Test
    fun controlStatusOverridesType() {
        val row = makeRow(status = SampleStatus.CONTROL, type = SampleType.COBRA)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains(">Весовой контроль<"))
    }

    @Test
    fun weightWithControlInBrackets() {
        val row = makeRow(weight = 2.5, controlWeight = 2.3)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains(">2.5 (2.3)<"))
    }

    @Test
    fun intervalDashWhenEmpty() {
        val row = makeRow(from = "—", to = "—")
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains(">—<"))
    }

    // ================================================================
    // Цвета строк (CSS-классы)
    // ================================================================

    @Test
    fun foundRowHasFoundClass() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()))
        )
        assertTrue(html.contains("class=\"row-found\""))
    }

    @Test
    fun postponedRowHasPostponedClass() {
        val row = makeRow(found = false, postponed = true)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains("class=\"row-postponed\""))
    }

    @Test
    fun blankRowHasBlankClass() {
        val row = makeRow(status = SampleStatus.BLANK, found = false)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains("class=\"row-blank\""))
    }

    @Test
    fun errorRowHasErrorClass() {
        val row = makeRow(found = false, hasImportError = true)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains("class=\"row-error\""))
    }

    @Test
    fun controlRowHasControlClass() {
        val row = makeRow(found = false, weightControl = true)
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(ReportSample(row, null, emptyList())))
        )
        assertTrue(html.contains("class=\"row-control\""))
    }

    // ================================================================
    // Гиперссылки внутри HTML
    // ================================================================

    @Test
    fun noAppendixLinkWhenNoNoteNoPhoto() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()))
        )
        assertFalse(html.contains("href=\"#appendix-"))
        assertTrue(html.contains("class=\"c-link muted\">—"))
    }

    @Test
    fun tableLinksToAppendixForSampleWithNote() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleWithNote("1", "NV136601")))
        )
        assertTrue(html.contains("href=\"#appendix-1\""))
        assertTrue(html.contains("Прил. 1"))
    }

    @Test
    fun appendixHasBackLinkToSample() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleWithNote("1", "NV136601")))
        )
        assertTrue(html.contains("href=\"#sample-1\""))
        assertTrue(html.contains("↩ К пробе NV136601"))
    }

    @Test
    fun sampleCellHasAnchorId() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleWithNote("abc-123", "NV136601")))
        )
        assertTrue(html.contains("id=\"sample-abc-123\""))
        assertTrue(html.contains("id=\"appendix-abc-123\""))
    }

    // ================================================================
    // Приложения
    // ================================================================

    @Test
    fun noAppendixSectionWhenNothingToShow() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleNoAppendix()))
        )
        assertFalse(html.contains("id=\"appendices\""))
    }

    @Test
    fun appendixSectionPresentWithNote() {
        val html = ReportHtmlGenerator.generate(
            makeReport(
                samples = listOf(sampleWithNote("1", "NV136601", "Дубль"))
            )
        )
        assertTrue(html.contains("id=\"appendices\""))
        assertTrue(html.contains("Приложение 1 —"))
        assertTrue(html.contains(">Дубль<"))
    }

    @Test
    fun appendixSectionPresentWithPhoto() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = listOf(sampleWithPhoto("1", "NV136601", 2)))
        )
        assertTrue(html.contains("id=\"appendices\""))
        assertTrue(html.contains("data:image/jpeg;base64,AAA0"))
        assertTrue(html.contains("data:image/jpeg;base64,AAA1"))
        assertTrue(html.contains("Фото 1 из 2"))
        assertTrue(html.contains("Фото 2 из 2"))
    }

    @Test
    fun appendixNumbersSequentialForMultipleSamples() {
        val html = ReportHtmlGenerator.generate(
            makeReport(
                samples = listOf(
                    sampleWithNote("1", "S1", "N1"),
                    sampleWithNote("2", "S2", "N2"),
                    sampleWithNote("3", "S3", "N3")
                )
            )
        )
        assertTrue(html.contains("Приложение 1 —"))
        assertTrue(html.contains("Приложение 2 —"))
        assertTrue(html.contains("Приложение 3 —"))
    }

    // ================================================================
    // Экранирование
    // ================================================================

    @Test
    fun specialCharactersAreEscaped() {
        val html = ReportHtmlGenerator.generate(
            makeReport(
                area = "А & Б",
                samples = listOf(
                    sampleWithNote("1", "S<1>", "Заметка <b>тест</b>")
                )
            )
        )
        assertTrue(html.contains("А &amp; Б"))
        assertTrue(html.contains("S&lt;1&gt;"))
        assertTrue(html.contains("&lt;b&gt;тест&lt;/b&gt;"))
        assertFalse(html.contains("S<1>"))
    }

    @Test
    fun multilineNoteHasBrTag() {
        val html = ReportHtmlGenerator.generate(
            makeReport(
                samples = listOf(sampleWithNote("1", "S1", "Строка 1\nСтрока 2"))
            )
        )
        assertTrue(html.contains("Строка 1<br>Строка 2"))
    }

    // ================================================================
    // Пустой случай
    // ================================================================

    @Test
    fun emptySamplesProduceValidHtmlWithoutTable() {
        val html = ReportHtmlGenerator.generate(
            makeReport(samples = emptyList())
        )
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("</html>"))
        // Шапка есть, таблица есть — но без строк tbody.
        assertTrue(html.contains("<thead>"))
    }
}
