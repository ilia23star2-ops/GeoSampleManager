package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.GroupStats
import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus
import com.example.geosamplemanager.ui.screens.SampleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-xlsx-header:
 *  - шапка объединена A1:I1..A4:I4;
 *  - строки шапки имеют стили TITLE / META.
 */
class XlsxMultiReportBuilderTest {

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
        sample: String = "NV136601"
    ) = ReportSample(
        row = makeRow(id = id, sample = sample, hasPhoto = true),
        note = null,
        photos = listOf(ReportPhoto("data:image/jpeg;base64,AAA"))
    )

    private fun cellText(cell: XlsxCell): String? =
        (cell as? XlsxCell.Text)?.value

    private val fakeDecoder: (String) -> DecodedImage = { _ ->
        DecodedImage(bytes = byteArrayOf(1, 2, 3, 4), extension = "jpg")
    }

    @Test
    fun emptyInputReturnsEmptyList() {
        val sheets = XlsxMultiReportBuilder.build(emptyList())
        assertTrue(sheets.isEmpty())
    }

    @Test
    fun oneOrderNoAppendixSingleSheet() {
        val order = makeReport(
            area = "Актайский",
            order = "13",
            samples = listOf(sampleNoAppendix())
        )
        val sheets = XlsxMultiReportBuilder.build(listOf(order))
        assertEquals(1, sheets.size)
        assertEquals("Актайский — Наряд 13", sheets[0].name)
    }

    @Test
    fun threeOrdersNoAppendixThreeSheets() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2"))),
            makeReport(area = "В", order = "3", samples = listOf(sampleNoAppendix("3")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        assertEquals(3, sheets.size)
        assertEquals("А — Наряд 1", sheets[0].name)
        assertEquals("Б — Наряд 2", sheets[1].name)
        assertEquals("В — Наряд 3", sheets[2].name)
    }

    @Test
    fun orderSheetHasMergedHeader() {
        val order = makeReport(
            area = "Коптеловский",
            order = "27",
            samples = listOf(sampleNoAppendix())
        )
        val sheet = XlsxMultiReportBuilder.build(listOf(order))[0]
        assertEquals(
            listOf("A1:I1", "A2:I2", "A3:I3", "A4:I4"),
            sheet.mergeCells
        )
    }

    @Test
    fun orderSheetHeaderUsesTitleAndMetaStyles() {
        val order = makeReport(
            area = "Коптеловский",
            order = "27",
            samples = listOf(sampleNoAppendix())
        )
        val rows = XlsxMultiReportBuilder.build(listOf(order))[0].rows
        assertEquals(XlsxStyles.TITLE, rows[0].styleId)
        assertEquals(XlsxStyles.META, rows[1].styleId)
        assertEquals(XlsxStyles.META, rows[2].styleId)
        assertEquals(XlsxStyles.META, rows[3].styleId)
    }

    @Test
    fun orderSheetHeaderContainsCombinedMeta() {
        val order = makeReport(
            area = "Коптеловский",
            order = "27",
            samples = listOf(sampleNoAppendix())
        )
        val rows = XlsxMultiReportBuilder.build(listOf(order))[0].rows
        assertEquals("Участок: Коптеловский", cellText(rows[1].cells[0]))
        assertEquals("Наряд: №27", cellText(rows[2].cells[0]))
    }

    @Test
    fun orderSheetContainsHeaderAndSamples() {
        val order = makeReport(
            area = "Коптеловский",
            order = "27",
            samples = listOf(
                sampleNoAppendix(id = "1", sample = "NV136601"),
                sampleNoAppendix(id = "2", sample = "NV136602")
            )
        )
        val sheet = XlsxMultiReportBuilder.build(listOf(order))[0]
        assertEquals("Отчёт по наряду", cellText(sheet.rows[0].cells[0]))
        assertEquals("Найдена", cellText(sheet.rows[5].cells[0]))
        assertEquals("п/п", cellText(sheet.rows[5].cells[1]))
        assertEquals("№ пробы", cellText(sheet.rows[5].cells[2]))
        assertEquals("NV136601", cellText(sheet.rows[6].cells[2]))
        assertEquals("NV136602", cellText(sheet.rows[7].cells[2]))
    }

    @Test
    fun appendixSheetAppendedAfterOrders() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleWithNote("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleWithNote("2")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        assertEquals(3, sheets.size)
        assertEquals("А — Наряд 1", sheets[0].name)
        assertEquals("Б — Наряд 2", sheets[1].name)
        assertEquals("Приложения", sheets[2].name)
    }

    @Test
    fun appendixNumberingIsSequentialAcrossOrders() {
        val orders = listOf(
            makeReport(
                area = "А",
                order = "1",
                samples = listOf(
                    sampleWithNote("1", "A1"),
                    sampleWithNote("2", "A2")
                )
            ),
            makeReport(
                area = "Б",
                order = "2",
                samples = listOf(sampleWithNote("3", "B1"))
            )
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        val appSheet = sheets[2]
        val text = appSheet.rows.flatMap { it.cells }
            .mapNotNull { cellText(it) }
            .joinToString("|")
        assertTrue(text.contains("Приложение 1"))
        assertTrue(text.contains("Приложение 2"))
        assertTrue(text.contains("Приложение 3"))
    }

    @Test
    fun noAppendixSheetWhenNothingToShow() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        assertEquals(2, sheets.size)
        assertTrue(sheets.none { it.name == "Приложения" })
    }

    @Test
    fun orderWithAppendixHasLinkToCommonSheet() {
        val orders = listOf(
            makeReport(
                area = "А",
                order = "1",
                samples = listOf(sampleWithNote("1", "A1"))
            )
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        val orderSheet = sheets[0]
        assertEquals(1, orderSheet.hyperlinks.size)
        val link = orderSheet.hyperlinks[0]
        assertEquals("I7", link.ref)
        assertTrue(link.location.contains("Приложения"))
    }

    @Test
    fun appendixBackLinkPointsToOwnOrderSheet() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleWithNote("2", "B1")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        val appSheet = sheets[2]
        assertEquals(1, appSheet.hyperlinks.size)
        val link = appSheet.hyperlinks[0]
        assertTrue(link.location.contains("Б — Наряд 2"))
        assertTrue(link.location.contains("C7"))
    }

    @Test
    fun duplicateNamesGetSuffix() {
        val orders = listOf(
            makeReport(area = "А", order = "27", samples = listOf(sampleNoAppendix("1"))),
            makeReport(area = "А", order = "27", samples = listOf(sampleNoAppendix("2"))),
            makeReport(area = "А", order = "27", samples = listOf(sampleNoAppendix("3")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        assertEquals("А — Наряд 27", sheets[0].name)
        assertEquals("А — Наряд 27 (2)", sheets[1].name)
        assertEquals("А — Наряд 27 (3)", sheets[2].name)
    }

    @Test
    fun longNameIsTruncatedWithSuffix() {
        val longArea = "ОченьДлинноеНазваниеУчасткаДляПроверкиЛимита"
        val orders = listOf(
            makeReport(
                area = longArea,
                order = "27",
                samples = listOf(sampleNoAppendix("1"))
            ),
            makeReport(
                area = longArea,
                order = "27",
                samples = listOf(sampleNoAppendix("2"))
            )
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        assertTrue(sheets[0].name.length <= 31)
        assertTrue(sheets[1].name.length <= 31)
        assertTrue(sheets[1].name.endsWith(" (2)"))
    }

    @Test
    fun multiSheetsCanBeWrittenToZip() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleWithNote("1", "A1"))),
            makeReport(area = "Б", order = "2", samples = listOf(sampleNoAppendix("2")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders)
        assertEquals(3, sheets.size)

        val bytes = XlsxWriter.toBytes(sheets)
        assertTrue(bytes.size > 0)

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
        assertTrue(names.contains("xl/worksheets/sheet3.xml"))
        assertNotNull(names.find { it == "xl/workbook.xml" })
    }

    @Test
    fun imageGoesToCommonAppendixSheet() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleWithPhoto("1", "A1")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders, fakeDecoder)
        assertEquals(2, sheets.size)
        assertEquals(1, sheets[1].images.size)
        assertEquals("jpg", sheets[1].images[0].extension)
    }

    @Test
    fun multiMediaEntriesAppearInZip() {
        val orders = listOf(
            makeReport(area = "А", order = "1", samples = listOf(sampleWithPhoto("1", "A1")))
        )
        val sheets = XlsxMultiReportBuilder.build(orders, fakeDecoder)
        val bytes = XlsxWriter.toBytes(sheets)

        val names = mutableListOf<String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                names.add(e.name)
                e = zip.nextEntry
            }
        }
        assertTrue(names.contains("xl/media/image1.jpg"))
        assertTrue(names.contains("xl/drawings/drawing1.xml"))
    }
}
