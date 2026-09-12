package com.example.geosamplemanager.data.history

/**
 * Одна запись в истории импорта — соответствует одному завершённому импорту файла.
 */
data class ImportHistoryEntry(
    val timestamp: Long,
    val fileName: String,
    val totalSheets: Int,
    val importedCount: Int,
    val replacedCount: Int = 0,
    val skippedCount: Int = 0,
    val generalListCount: Int = 0,
    val errorCount: Int = 0,
    val items: List<ImportHistoryItem>
) {
    val totalSamples: Int
        get() = items.filter {
            it.status == "imported" || it.status == "replaced"
        }.sumOf { it.sampleCount }
}

/**
 * Один обработанный лист внутри записи истории.
 * reason — краткое объяснение статуса (почему пропущен, что заменено и т.п.).
 */
data class ImportHistoryItem(
    val sheetName: String,
    val areaName: String,
    val orderNumber: String,
    val sampleCount: Int,
    val status: String,       // "imported" | "replaced" | "skipped" | "existing_skipped" | "general_list" | "error"
    val reason: String? = null
)