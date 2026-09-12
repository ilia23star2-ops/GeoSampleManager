package com.example.geosamplemanager.data.excel

import com.example.geosamplemanager.data.settings.ImportSettings

/**
 * Собирает модель наряда из проанализированного листа.
 * Использует настройки для определения участка, наряда и фильтрации.
 */
object ExcelImporter {

    fun buildOrder(
        analysis: SheetAnalysis,
        fileName: String,
        totalSheets: Int,
        settings: ImportSettings,
        fallbackOrderNumber: String
    ): ImportContext {
        val rows = analysis.dataRows()
        val mapping = analysis.mapping

        // 1) Фильтруем строки
        val keptRows = mutableListOf<List<String>>()
        var skippedBlanks = 0
        var skippedEmpty = 0
        for (row in rows) {
            when (SampleFilter.classify(row, mapping, settings)) {
                SampleFilter.Result.KEEP -> keptRows.add(row)
                SampleFilter.Result.SKIP_BLANK -> skippedBlanks++
                SampleFilter.Result.SKIP_EMPTY -> skippedEmpty++
            }
        }

        // 2) Собираем пробы
        val samples = mutableListOf<ParsedSample>()
        val wellsSet = mutableSetOf<String>()
        var serial = 0

        for (row in keptRows) {
            val wellNum = getCell(row, mapping[ExcelAnalyzer.Roles.WELL]).trim()
            val sampleNum = getCell(row, mapping[ExcelAnalyzer.Roles.SAMPLE]).trim()
            if (wellNum.isEmpty() && sampleNum.isEmpty()) continue

            serial++

            val intFrom = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.INT_FROM]))
            val intTo = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.INT_TO]))
            val weight = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.WEIGHT]))
            val materialDesc = getCell(row, mapping[ExcelAnalyzer.Roles.MATERIAL])
                .trim().ifEmpty { null }

            val (type, status) = SampleFilter.classifyTypeAndStatus(row, mapping, wellNum, settings)

            samples.add(
                ParsedSample(
                    serialNumber = serial,
                    sampleNumber = sampleNum.ifEmpty { "S-$serial" },
                    wellNumber = wellNum,
                    intervalFrom = intFrom,
                    intervalTo = intTo,
                    weight = weight,
                    sampleType = type,
                    status = status,
                    workings = null,
                    materialDesc = materialDesc
                )
            )

            if (wellNum.isNotEmpty()) wellsSet.add(wellNum)
        }

        // 3) Автоопределение участка
        val areaName = AreaResolver.resolve(wellsSet, settings)
        val areaAutoDetected = areaName != null

        // 4) Автоопределение наряда
        val orderNumber = OrderNumberExtractor.extract(
            fileName = fileName,
            sheetName = analysis.sheetName,
            totalSheets = totalSheets,
            settings = settings,
            fallback = fallbackOrderNumber
        )
        val orderAutoDetected = orderNumber != fallbackOrderNumber

        return ImportContext(
            order = ParsedOrder(
                areaName = areaName,
                orderNumber = orderNumber,
                samples = samples,
                wellsCount = wellsSet.size
            ),
            areaAutoDetected = areaAutoDetected,
            orderAutoDetected = orderAutoDetected,
            skippedBlanks = skippedBlanks,
            skippedEmpty = skippedEmpty
        )
    }

    private fun getCell(row: List<String>, idx: Int?): String {
        if (idx == null || idx < 0 || idx >= row.size) return ""
        return row[idx]
    }
}