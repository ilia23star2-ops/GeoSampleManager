package com.example.geosamplemanager.data.excel

/**
 * Из строк Excel делает модель наряда с пробами.
 */
object ExcelImporter {

    fun buildOrder(
        rows: List<List<String>>,
        headerRowIdx: Int,
        mapping: Map<String, Int?>,
        areaName: String?,
        orderNumber: String,
        defaultType: String = "auger"
    ): ParsedOrder {
        val samples = mutableListOf<ParsedSample>()
        val wellsSet = mutableSetOf<String>()
        var serial = 0

        for (rowIdx in (headerRowIdx + 1) until rows.size) {
            val row = rows[rowIdx]

            val wellNum = getCell(row, mapping["well"]).trim()
            val sampleNum = getCell(row, mapping["sample"]).trim()

            if (wellNum.isEmpty() && sampleNum.isEmpty()) continue

            serial++

            val intFrom = ExcelAnalyzer.parseNumber(getCell(row, mapping["int_from"]))
            val intTo = ExcelAnalyzer.parseNumber(getCell(row, mapping["int_to"]))
            val weight = ExcelAnalyzer.parseNumber(getCell(row, mapping["weight"]))
            val typeText = getCell(row, mapping["type"]).trim()
            val workings = getCell(row, mapping["workings"]).trim().ifEmpty { null }

            val type = determineType(typeText, wellNum, defaultType)
            val status = determineStatus(intFrom, intTo, weight)

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
                    workings = workings
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
        val well = wellNum.uppercase()
        if (well.startsWith("KPD") || well.startsWith("KOP")) return "auger"
        if (well.startsWith("KBK") || well.startsWith("KMB")) return "channel"
        if (well.startsWith("ACD")) return "cobra"
        return default
    }

    private fun determineStatus(intFrom: Double?, intTo: Double?, weight: Double?): String {
        if (intFrom == null && intTo == null && weight == null) return "blank"
        return "normal"
    }
}