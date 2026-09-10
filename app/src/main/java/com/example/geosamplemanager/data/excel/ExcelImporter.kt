package com.example.geosamplemanager.data.excel

/**
 * Из проанализированного листа делает модель наряда с пробами.
 * Использует SheetAnalysis из ExcelAnalyzer с готовым mapping-ом.
 */
object ExcelImporter {

    fun buildOrder(
        analysis: SheetAnalysis,
        areaName: String?,
        orderNumber: String,
        defaultType: String = "auger"
    ): ParsedOrder {
        val rows = analysis.dataRows()
        val mapping = analysis.mapping
        val samples = mutableListOf<ParsedSample>()
        val wellsSet = mutableSetOf<String>()
        var serial = 0

        for (row in rows) {
            val wellNum = getCell(row, mapping[ExcelAnalyzer.Roles.WELL]).trim()
            val sampleNum = getCell(row, mapping[ExcelAnalyzer.Roles.SAMPLE]).trim()

            // Пропускаем пустые/мусорные строки
            if (wellNum.isEmpty() && sampleNum.isEmpty()) continue

            serial++

            val intFrom = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.INT_FROM]))
            val intTo = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.INT_TO]))
            val weight = ExcelAnalyzer.parseNumber(getCell(row, mapping[ExcelAnalyzer.Roles.WEIGHT]))
            val typeText = getCell(row, mapping[ExcelAnalyzer.Roles.TYPE]).trim()
            val materialDesc = getCell(row, mapping[ExcelAnalyzer.Roles.MATERIAL]).trim().ifEmpty { null }

            val type = determineType(typeText, wellNum, defaultType)
            val status = determineStatus(typeText, intFrom, intTo, weight)

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

        return ParsedOrder(
            areaName = areaName,
            orderNumber = orderNumber,
            samples = samples,
            wellsCount = wellsSet.size
        )
    }

    private fun getCell(row: List<String>, idx: Int?): String {
        if (idx == null || idx < 0 || idx >= row.size) return ""
        return row[idx]
    }

    private fun determineType(typeText: String, wellNum: String, default: String): String {
        val t = typeText.lowercase()
        if (t.contains("шнек")) return "auger"
        if (t.contains("борозд")) return "channel"
        if (t.contains("кобра")) return "cobra"
        // По префиксу номера — упрощённо
        val well = wellNum.uppercase()
        if (well.startsWith("KPD") || well.startsWith("KOP")) return "auger"
        if (well.startsWith("KBK") || well.startsWith("KMB")) return "channel"
        if (well.startsWith("ACD")) return "cobra"
        return default
    }

    private fun determineStatus(
        typeText: String,
        intFrom: Double?,
        intTo: Double?,
        weight: Double?
    ): String {
        if (typeText.lowercase().contains("холост")) return "blank"
        if (intFrom == null && intTo == null && weight == null) return "blank"
        return "normal"
    }
}