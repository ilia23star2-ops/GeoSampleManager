package com.example.geosamplemanager.data.excel

import com.example.geosamplemanager.data.settings.ImportSettings
import com.example.geosamplemanager.data.settings.OrderNumberRule
import com.example.geosamplemanager.data.settings.OrderSource

/**
 * Извлечение номера наряда из имени файла или имени листа.
 *
 * Логика AUTO:
 *   1) пробуем имя файла — если удалось извлечь номер, используем;
 *   2) иначе имя листа — если удалось, используем;
 *   3) иначе — fallback (пользователь введёт вручную).
 */
object OrderNumberExtractor {

    /**
     * @param fileName имя файла без расширения
     * @param sheetName имя листа Excel
     * @param totalSheets всего листов (для справки, в новой логике не участвует)
     * @param settings настройки импорта
     * @param fallback что вернуть, если совсем не получилось извлечь
     */
    fun extract(
        fileName: String,
        sheetName: String,
        totalSheets: Int,
        settings: ImportSettings,
        fallback: String
    ): String {
        return when (settings.orderSource) {
            OrderSource.FILENAME -> extractFrom(fileName, settings, fallback)
            OrderSource.SHEET_NAME -> extractFrom(sheetName, settings, fallback)
            OrderSource.AUTO -> {
                // 1) пробуем имя файла
                val fromFile = extractFrom(fileName, settings, fallback = "")
                if (fromFile.isNotEmpty()) return fromFile
                // 2) пробуем имя листа
                val fromSheet = extractFrom(sheetName, settings, fallback = "")
                if (fromSheet.isNotEmpty()) return fromSheet
                // 3) ничего не вышло
                fallback
            }
        }
    }

    /**
     * Извлекает номер из указанного источника.
     * Возвращает fallback, если номер не удалось получить.
     */
    private fun extractFrom(source: String, settings: ImportSettings, fallback: String): String {
        return when (settings.orderNumberRule) {
            OrderNumberRule.LAST_INTEGER ->
                extractLastInteger(source) ?: fallback
            OrderNumberRule.FULL_NAME -> {
                val t = source.trim()
                if (t.length >= 2) t else fallback
            }
        }
    }

    /**
     * Ищет последнее целое число в строке.
     * «02-КОПТ00027» → «27»  (без ведущих нулей)
     * «Опись 1» → «1»
     * «ASSAY (2)» → «2»
     * «нет чисел» → null
     */
    fun extractLastInteger(text: String): String? {
        val matches = Regex("\\d+").findAll(text).map { it.value }.toList()
        if (matches.isEmpty()) return null
        val last = matches.last()
        val trimmed = last.trimStart('0')
        return if (trimmed.isEmpty()) "0" else trimmed
    }
}