package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.GroupStats
import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus
import com.example.geosamplemanager.ui.screens.SampleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-report-html-multi / подзаход 5 (html-multi):
 * Тесты мультинарядного HTML-генератора.
 */
class MultiHtmlReportGeneratorTest {

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

    private val generatedAt = "29.09.2026 21:00"

    // ================================================================
    // Пустой вход / один наряд / несколько нарядов
    // ================================================================

    @Test
    fun emptyInputProducesValidHtmlWithMessage() {
        val html = MultiHtmlReportGenerator.generate(emptyList(), generatedAt)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("Нет нарядов для отчёта"))
        assertTrue(html.contains("Нарядов:</b> 0"))
    }

    @Test
    fun singleOrderProducesTocAndOneOrderSection() {
        val orders = listOf(
            makeReport(area = "Актайский", order = "13", samples = listOf(sampleNoAppendix()))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("id=\"toc\""))
        assertTrue(html.contains("id=\"order-0\""))
        assertFalse(html.contains("id=\"order-1\""))
        assertTrue(html.contains("Актайский — Наряд №13"))
    }

    @Test
    fun threeOrdersProduceThreeOrderSections() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2"))),
            makeReport(area = "В", order = "3", samples = listOf(sampleNoAppendix("3")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("id=\"order-0\""))
        assertTrue(html.contains("id=\"order-1\""))
        assertTrue(html.contains("id=\"order-2\""))
        assertTrue(html.contains("А — Наряд №1"))
        assertTrue(html.contains("Б — Наряд №2"))
        assertTrue(html.contains("В — Наряд №3"))
    }

    @Test
    fun tocHasLinksToAllOrders() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("href=\"#order-0\""))
        assertTrue(html.contains("href=\"#order-1\""))
    }

    // ================================================================
    // Общая сводка на титуле
    // ================================================================

    @Test
    fun tocHasTotalsSummingAcrossOrders() {
        val s1 = GroupStats(total = 10, found = 6, notFound = 2, blanks = 1,
            weightControls = 1, postponed = 0, errors = 0)
        val s2 = GroupStats(total = 5, found = 3, notFound = 1, blanks = 0,
            weightControls = 1, postponed = 0, errors = 0)
        val orders = listOf(
            makeReport(area = "А", order = "1",
                samples = listOf(sampleNoAppendix("1")), stats = s1),
            makeReport(area = "Б", order = "2",
                samples = listOf(sampleNoAppendix("2")), stats = s2)
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        // Всего = 15, Найдено = 9.
        // «Всего» без цвета → <b>15</b> Всего.
        // «Найдено» с цветом → <b style="...">9</b> Найдено.
        // Проверяем по хвосту — он одинаков в обоих случаях.
        assertTrue(html.contains("15</b> Всего"))
        assertTrue(html.contains("9</b> Найдено"))
    }

    // ================================================================
    // Общий блок приложений
    // ================================================================

    @Test
    fun noAppendixBlockWhenNothingToShow() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertFalse(html.contains("id=\"appendices\""))
    }

    @Test
    fun appendixBlockPresentWhenSomeOrderHasAppendix() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleWithNote("1", "A1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("id=\"appendices\""))
        assertTrue(html.contains("id=\"appendix-0-1\""))
    }

    @Test
    fun appendixNumberingIsSequentialAcrossOrders() {
        val orders = listOf(
            makeReport(area = "А", order = "1",
                samples = listOf(
                    sampleWithNote("1", "A1"),
                    sampleWithNote("2", "A2")
                )),
            makeReport(area = "Б", order = "2",
                samples = listOf(sampleWithNote("3", "B1")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("Приложение 1 —"))
        assertTrue(html.contains("Приложение 2 —"))
        assertTrue(html.contains("Приложение 3 —"))
    }

    @Test
    fun appendixOrderTitleAppearsInAppendixBlock() {
        val orders = listOf(
            makeReport(area = "Актайский", order = "5",
                samples = listOf(sampleWithNote("1", "A1")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        // Должно быть два упоминания: в TOC и в заглавии группы приложений.
        val count = Regex("Актайский — Наряд №5").findAll(html).count()
        assertTrue("Ожидали >= 2 упоминаний, получили $count", count >= 2)
    }

    // ================================================================
    // Гиперссылки внутри HTML
    // ================================================================

    @Test
    fun tableLinksToAppendixWithOrderPrefix() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleWithNote("1", "A1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleWithNote("2", "B1")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("href=\"#appendix-0-1\""))
        assertTrue(html.contains("href=\"#appendix-1-2\""))
    }

    @Test
    fun appendixBackLinkPointsToOwnOrderSection() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleWithNote("2", "B1")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        // Обратная ссылка из второго наряда — на sample-1-2, не на sample-0-...
        assertTrue(html.contains("href=\"#sample-1-2\""))
        assertFalse(html.contains("href=\"#sample-1-1\""))
    }

    @Test
    fun sampleAnchorsHaveOrderPrefix() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("id=\"sample-0-1\""))
        assertTrue(html.contains("id=\"sample-1-2\""))
    }

    @Test
    fun backToTocLinkExistsInEachOrderSection() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2")))
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        val count = Regex("href=\"#toc\"").findAll(html).count()
        assertTrue("Ожидали минимум 2 ссылки на #toc, получили $count", count >= 2)
    }

    // ================================================================
    // Экранирование
    // ================================================================

    @Test
    fun specialCharactersAreEscaped() {
        val orders = listOf(
            makeReport(
                area = "А & Б",
                order = "1",
                samples = listOf(sampleNoAppendix("1", "S<1>"))
            )
        )
        val html = MultiHtmlReportGenerator.generate(orders, generatedAt)
        assertTrue(html.contains("А &amp; Б"))
        assertTrue(html.contains("S&lt;1&gt;"))
        assertFalse(html.contains("S<1>"))
    }
}