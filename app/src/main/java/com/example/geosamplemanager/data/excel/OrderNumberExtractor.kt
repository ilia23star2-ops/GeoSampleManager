package com.example.geosamplemanager.data.excel

import com.example.geosamplemanager.data.settings.ImportSettings
import com.example.geosamplemanager.data.settings.OrderNumberRule
import com.example.geosamplemanager.data.settings.OrderSource

/**
 * Извлечение номера наряда из имени файла или имени листа.
 *
 * FIX 5.8.10-f:
 * Раньше AUTO **всегда** сначала пробовал имя файла. Если файл
 * назывался «опись проб 13.xlsx», а листы — «НЗ №97», «НЗ №98»…,
 * для всех листов возвращался наряд «13». Итог: импортировался только
 * первый лист, остальные падали в «наряд уже есть в базе».
 *
 * Теперь приоритет — **осмысленное имя листа** (если оно не «Лист1»,
 * «Sheet1», «TDSheet» и т.п.). Иначе — имя файла. Это позволяет
 * одному Excel-файлу содержать несколько нарядов на разных листах.
 *
 * Логика AUTO (новая):
 *   1) если имя листа не стандартное («НЗ №97», «Наряд 13-2») —
 *      пробуем извлечь номер оттуда;
 *   2) иначе имя файла;
 *   3) иначе имя листа, даже если оно выглядит стандартным
 *      (вдруг там есть цифры, которые нужны);
 *   4) иначе fallback.
 */
object OrderNumberExtractor {

    /**
     * Стандартные имена листов, которые Excel создаёт автоматически.
     * Такие имена не несут смысла и не должны давать номер наряда.
     * Регистр не важен, пробелы вокруг и после слова допускаются.
     */
    private val STANDARD_SHEET_NAME =
        Regex("^(лист|sheet|tdsheet|page|страница)\\s*\\d*$", RegexOption.IGNORE_CASE)

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
            OrderSource.AUTO -> extractAuto(fileName, sheetName, settings, fallback)
        }
    }

    /**
     * FIX 5.8.10-f: приоритет осмысленного имени листа над именем файла.
     *
     * Почему так:
     *  - Один файл — один наряд: обычно имя файла осмысленное, имя листа
     *    стандартное («Лист1»). Сработает ветка «имя файла».
     *  - Один файл — много нарядов: имя файла общее («опись проб 13»),
     *    имя листа — конкретное («НЗ №97»). Сработает ветка «имя листа».
     */
    private fun extractAuto(
        fileName: String,
        sheetName: String,
        settings: ImportSettings,
        fallback: String
    ): String {
        val sheetIsMeaningful = !isStandardSheetName(sheetName)

        // 1) Осмысленное имя листа — приоритет.
        if (sheetIsMeaningful) {
            val fromSheet = extractFrom(sheetName, settings, fallback = "")
            if (fromSheet.isNotEmpty()) return fromSheet
        }

        // 2) Имя файла.
        val fromFile = extractFrom(fileName, settings, fallback = "")
        if (fromFile.isNotEmpty()) return fromFile

        // 3) Имя листа, даже если оно выглядит стандартным — вдруг там
        //    всё-таки есть цифры, которые нужны пользователю.
        val fromSheet = extractFrom(sheetName, settings, fallback = "")
        if (fromSheet.isNotEmpty()) return fromSheet

        // 4) Ничего не вышло — отдаём fallback.
        return fallback
    }

    /** FIX 5.8.10-f: «Лист1», «Sheet 2», «TDSheet» — не осмысленные имена. */
    private fun isStandardSheetName(name: String): Boolean =
        STANDARD_SHEET_NAME.matches(name.trim())

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
