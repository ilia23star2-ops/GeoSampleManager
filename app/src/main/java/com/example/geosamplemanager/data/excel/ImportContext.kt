package com.example.geosamplemanager.data.excel

/**
 * Результат разбора одного листа Excel.
 * Включает сам наряд, а также мета-информацию об автодетекте.
 */
data class ImportContext(
    val order: ParsedOrder,
    val areaAutoDetected: Boolean,    // участок определён автоматически
    val orderAutoDetected: Boolean,   // наряд определён автоматически
    val skippedBlanks: Int,           // сколько бланков пропущено
    val skippedEmpty: Int             // сколько пустых строк пропущено
)