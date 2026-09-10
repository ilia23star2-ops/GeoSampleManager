package com.example.geosamplemanager.data.excel

data class ParsedSample(
    val serialNumber: Int,
    val sampleNumber: String,
    val wellNumber: String,
    val intervalFrom: Double?,
    val intervalTo: Double?,
    val weight: Double?,
    val sampleType: String,      // auger / channel / cobra
    val status: String,          // normal / blank / control
    val workings: String?
)

data class ParsedOrder(
    val areaName: String?,
    val orderNumber: String,
    val samples: List<ParsedSample>,
    val wellsCount: Int
)

data class SheetAnalysis(
    val sheetName: String,
    val headerRowIndex: Int,
    val mapping: Map<String, Int?>,
    val score: Int,
    val rows: List<List<String>>
)