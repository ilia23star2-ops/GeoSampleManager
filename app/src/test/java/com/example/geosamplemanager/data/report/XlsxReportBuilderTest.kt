package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.GroupStats
import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus
import com.example.geosamplemanager.ui.screens.SampleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-report-xlsx / подзаход 4 (xlsx-links):
 * Тесты построителя с гиперссылками.
 */
class XlsxReportBuilderTest {

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
        samples: List<ReportSample>
    ) = ReportData(
        areaName = area,
        orderNumber = order,
        generatedAt = "29.09.2026 21:00",
        stats = GroupStats(),
        samples = samples
    )

    private fun cellText(cell: XlsxCell): String? =
        (cell as? XlsxCell.Text)?.value

    private fun cellNumber(cell: XlsxCell): Double? =
        (cell as? XlsxCell.Number)?.value

    // ================================================================
    // Лист 1 — наряд
    // ================================================================

    @Test
    fun onlyOrderSheetWhenNoAppendix() {
        val data = makeReport(
            samples = listOf(
                ReportSample(row = makeRow(), note = null, photos = emptyList())
            )
        )
        val sheets = XlsxReportBuilder.build(data)
        assertEquals(1, sheets.size)
        assertEquals("Наряд 27", sheets[0].name)
    }

    @Test
    fun headerContainsAreaAndOrderAndDate() {
        val data = makeReport(
            area = "Актайский",
            order = "13",
            samples = listOf(
                ReportSample(row = makeRow(), note = null, photos = emptyList())
            )
        )
        val rows = XlsxReportBuilder.build(data)[0].rows

        assertEquals("Отчёт по наряду", cellText(rows[0].cells[0]))
        assertEquals("Участок:", cellText(rows[1].cells[0]))
        assertEquals("Актайский", cellText(rows[1].cells[1]))
        assertEquals("Наряд:", cellText(rows[2].cells[0]))
        assertEquals("№13", cellText(rows[2].cells[1]))
        assertEquals("Дата:", cellText(rows[3].cells[0]))
        assertEquals("29.09.2026 21:00", cellText(rows[3].cells[1]))
    }

    @Test
    fun tableHeaderPresent() {
        val data = makeReport(
            samples = listOf(
                ReportSample(row = makeRow(), note = null, photos = emptyList())
            )
        )
        val rows = XlsxReportBuilder.build(data)[0].rows

        val headerRow = rows[5]
        assertEquals("п/п", cellText(headerRow.cells[0]))
        assertEquals("№ пробы", cellText(headerRow.cells[1]))
        assertEquals("Скважина", cellText(headerRow.cells[2]))
        assertEquals("Интервал", cellText(headerRow.cells[3]))
        assertEquals("Вес", cellText(headerRow.cells[4]))
        assertEquals("Характеристика", cellText(headerRow.cells[5]))
        assertEquals("Тип", cellText(headerRow.cells[6]))
        assertEquals("Найдена", cellText(headerRow.cells[7]))
        assertEquals("Прил.", cellText(headerRow.cells[8]))
    }

    @Test
    fun sampleRowWritten() {
        val row = makeRow(
            serial = 3,
            well = "NV1367",
            sample = "NV136703",
            from = "1.5",
            to = "3.0",
            weight = 2.7,
            characteristic = "Элювий",
            type = SampleType.CHANNEL,
            status = SampleStatus.NORMAL,
            found = true
        )
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows

        val dataRow = rows[6]
        assertEquals(3.0, cellNumber(dataRow.cells[0])!!, 0.0001)
        assertEquals("NV136703", cellText(dataRow.cells[1]))
        assertEquals("NV1367", cellText(dataRow.cells[2]))
        assertEquals("1.5–3.0", cellText(dataRow.cells[3]))
        assertEquals("2.7", cellText(dataRow.cells[4]))
        assertEquals("Элювий", cellText(dataRow.cells[5]))
        assertEquals("Бороздовая", cellText(dataRow.cells[6]))
        assertEquals("✓", cellText(dataRow.cells[7]))
    }

    @Test
    fun blankStatusOverridesType() {
        val row = makeRow(
            status = SampleStatus.BLANK,
            type = SampleType.AUGER,
            found = false
        )
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        val dataRow = rows[6]
        assertEquals("Холостая", cellText(dataRow.cells[6]))
        assertEquals("", cellText(dataRow.cells[7]))
    }

    @Test
    fun controlStatusOverridesType() {
        val row = makeRow(
            status = SampleStatus.CONTROL,
            type = SampleType.COBRA,
            found = true
        )
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals("Весовой контроль", cellText(rows[6].cells[6]))
    }

    @Test
    fun weightWithControlInBrackets() {
        val row = makeRow(weight = 2.5, controlWeight = 2.3)
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals("2.5 (2.3)", cellText(rows[6].cells[4]))
    }

    @Test
    fun weightEmptyIfNull() {
        val row = makeRow(weight = null, controlWeight = null)
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals("—", cellText(rows[6].cells[4]))
    }

    @Test
    fun intervalEmptyShowsDash() {
        val row = makeRow(from = "—", to = "—")
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals("—", cellText(rows[6].cells[3]))
    }

    // ================================================================
    // Стили
    // ================================================================

    @Test
    fun titleRowStyleIsBold() {
        val data = makeReport(
            samples = listOf(ReportSample(makeRow(), note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals(XlsxStyles.BOLD, rows[0].styleId)
    }

    @Test
    fun tableHeaderStyleIsHeader() {
        val data = makeReport(
            samples = listOf(ReportSample(makeRow(), note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals(XlsxStyles.HEADER, rows[5].styleId)
    }

    @Test
    fun foundRowStyle() {
        val row = makeRow(found = true)
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals(XlsxStyles.FOUND, rows[6].styleId)
    }

    @Test
    fun blankRowStyle() {
        val row = makeRow(status = SampleStatus.BLANK, found = false)
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals(XlsxStyles.BLANK, rows[6].styleId)
    }

    @Test
    fun postponedRowStyle() {
        val row = makeRow(found = false, postponed = true)
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals(XlsxStyles.POSTPONED, rows[6].styleId)
    }

    // ================================================================
    // Гиперссылки
    // ================================================================

    @Test
    fun orderSheetHasAppendixColumnHeader() {
        val data = makeReport(
            samples = listOf(ReportSample(makeRow(), note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals("Прил.", cellText(rows[5].cells[8]))
    }

    @Test
    fun noAppendixCellDashWhenNoAppendix() {
        val data = makeReport(
            samples = listOf(ReportSample(makeRow(), note = null, photos = emptyList()))
        )
        val sheet = XlsxReportBuilder.build(data)[0]
        assertEquals("—", cellText(sheet.rows[6].cells[8]))
        assertTrue(sheet.hyperlinks.isEmpty())
    }

    @Test
    fun orderHyperlinkForSampleWithNote() {
        val row = makeRow(sample = "NV136601", hasNote = true)
        val data = makeReport(
            samples = listOf(
                ReportSample(row, note = ReportNote("x"), photos = emptyList())
            )
        )
        val sheet = XlsxReportBuilder.build(data)[0]
        assertEquals("Прил. 1", cellText(sheet.rows[6].cells[8]))
        assertEquals(1, sheet.hyperlinks.size)
        val h = sheet.hyperlinks[0]
        assertEquals("I7", h.ref)
        assertTrue(h.location.contains("Приложения"))
        assertTrue(h.location.contains("A3"))
    }

    @Test
    fun orderHyperlinkCellHasLinkStyle() {
        val row = makeRow(sample = "NV136601", hasNote = true)
        val data = makeReport(
            samples = listOf(
                ReportSample(row, note = ReportNote("x"), photos = emptyList())
            )
        )
        val cells = XlsxReportBuilder.build(data)[0].rows[6].cells
        val appCell = cells[8] as XlsxCell.Text
        assertEquals(XlsxStyles.LINK, appCell.styleId)
    }

    @Test
    fun appendixSheetHasBackLink() {
        val row = makeRow(sample = "NV136601", hasNote = true)
        val data = makeReport(
            samples = listOf(
                ReportSample(row, note = ReportNote("x"), photos = emptyList())
            )
        )
        val sheets = XlsxReportBuilder.build(data)
        assertEquals(2, sheets.size)
        val appSheet = sheets[1]
        assertEquals(1, appSheet.hyperlinks.size)
        val h = appSheet.hyperlinks[0]
        assertTrue(h.ref.startsWith("A"))
        assertTrue(h.location.contains("Наряд"))
        assertTrue(h.location.contains("B7"))
    }

    @Test
    fun orderRowHasNineCells() {
        val row = makeRow()
        val data = makeReport(
            samples = listOf(ReportSample(row, note = null, photos = emptyList()))
        )
        val rows = XlsxReportBuilder.build(data)[0].rows
        assertEquals(9, rows[6].cells.size)
        assertEquals(9, rows[5].cells.size)
    }

    // ================================================================
    // Лист 2 — приложения
    // ================================================================

    @Test
    fun appendixSheetCreatedWhenNoteExists() {
        val row = makeRow(sample = "NV136601", well = "NV1366", hasNote = true)
        val note = ReportNote("Проба отобрана в 10:30")
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = note, photos = emptyList()))
        )
        val sheets = XlsxReportBuilder.build(data)
        assertEquals(2, sheets.size)
        assertEquals("Приложения", sheets[1].name)
    }

    @Test
    fun appendixSheetCreatedWhenPhotosExist() {
        val row = makeRow(hasPhoto = true)
        val photos = listOf(
            ReportPhoto("data:image/jpeg;base64,xxx"),
            ReportPhoto("data:image/jpeg;base64,yyy")
        )
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = photos))
        )
        val sheets = XlsxReportBuilder.build(data)
        assertEquals(2, sheets.size)
        assertEquals("Приложения", sheets[1].name)
    }

    @Test
    fun appendixContentHasSampleWellNotePhotos() {
        val row = makeRow(
            sample = "NV136601",
            well = "NV1366",
            hasNote = true,
            hasPhoto = true
        )
        val note = ReportNote("Дубль")
        val photos = listOf(ReportPhoto("data:image/jpeg;base64,xxx"))
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = note, photos = photos))
        )
        val appRows = XlsxReportBuilder.build(data)[1].rows

        assertEquals("Приложения", cellText(appRows[0].cells[0]))
        assertEquals("Приложение 1", cellText(appRows[2].cells[0]))
        assertEquals("№ пробы:", cellText(appRows[3].cells[0]))
        assertEquals("NV136601", cellText(appRows[3].cells[1]))
        assertEquals("Скважина:", cellText(appRows[4].cells[0]))
        assertEquals("NV1366", cellText(appRows[4].cells[1]))
        assertEquals("Заметка:", cellText(appRows[5].cells[0]))
        assertEquals("Дубль", cellText(appRows[5].cells[1]))
        assertEquals("Фото:", cellText(appRows[6].cells[0]))
        assertEquals("1 шт.", cellText(appRows[6].cells[1]))
        assertEquals("↩ К пробе NV136601", cellText(appRows[7].cells[0]))
    }

    @Test
    fun noAppendixSheetWhenNoNoteNoPhotos() {
        val row = makeRow(hasNote = false, hasPhoto = false)
        val data = makeReport(
            samples = listOf(ReportSample(row = row, note = null, photos = emptyList()))
        )
        val sheets = XlsxReportBuilder.build(data)
        assertEquals(1, sheets.size)
        assertNull(sheets.getOrNull(1))
    }

    @Test
    fun appendixMultipleSamples() {
        val row1 = makeRow(id = "1", sample = "NV136601", hasNote = true)
        val row2 = makeRow(id = "2", sample = "NV136602", hasPhoto = true)
        val data = makeReport(
            samples = listOf(
                ReportSample(row1, note = ReportNote("Первая"), photos = emptyList()),
                ReportSample(row2, note = null, photos = listOf(ReportPhoto("data:x")))
            )
        )
        val appRows = XlsxReportBuilder.build(data)[1].rows
        val text = appRows.flatMap { it.cells }.mapNotNull { cellText(it) }.joinToString("|")
        assertTrue(text.contains("Приложение 1"))
        assertTrue(text.contains("Приложение 2"))
        assertTrue(text.contains("NV136601"))
        assertTrue(text.contains("NV136602"))
    }

    // ================================================================
    // Общее
    // ================================================================

    @Test
    fun sheetsCanBeWrittenToZip() {
        val data = makeReport(
            samples = listOf(
                ReportSample(
                    makeRow(sample = "NV136601", hasNote = true),
                    note = ReportNote("Заметка"),
                    photos = emptyList()
                )
            )
        )
        val sheets = XlsxReportBuilder.build(data)

        val out = java.io.ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)
        val bytes = out.toByteArray()

        assertTrue(bytes.size > 0)
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])

        val names = mutableListOf<String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                names.add(e.name)
                e = zip.nextEntry
            }
        }
        assertTrue(names.contains("xl/worksheets/sheet1.xml"))
        assertTrue(names.contains("xl/worksheets/sheet2.xml"))
        assertNotNull(names.find { it == "xl/workbook.xml" })
        assertNotNull(names.find { it == "xl/styles.xml" })
    }
}