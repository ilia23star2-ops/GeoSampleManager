package com.example.geosamplemanager.data.settings

/**
 * Настройки импорта Excel.
 * Хранятся в файле filesDir/import_settings.json.
 */
data class ImportSettings(
    /** Участок → список префиксов в именах скважин/проб. */
    val areaPrefixes: Map<String, List<String>> = DEFAULT_AREA_PREFIXES,

    /** Откуда брать номер наряда. */
    val orderSource: OrderSource = OrderSource.AUTO,

    /** Как обрабатывать имя источника, чтобы получить номер наряда. */
    val orderNumberRule: OrderNumberRule = OrderNumberRule.LAST_INTEGER,

    /** Слова, означающие холостую пробу (в колонке «Тип пробы»). */
    val hollowKeywords: List<String> = listOf("холост", "blank"),

    /** Слова, означающие шнековую пробу. */
    val augerKeywords: List<String> = listOf("шнек", "auger"),

    /** Слова, означающие бороздовую пробу. */
    val channelKeywords: List<String> = listOf("борозд", "канав", "channel"),

    /** Слова, означающие бланк или стандартный образец. */
    val blankKeywords: List<String> = listOf(
        "бланк", "blank", "стандартный образец", "стандарт", "со ", "ст. образец"
    ),

    /** Пропускать бланки и стандартные образцы, у которых нет интервала и веса. */
    val skipBlanksWithoutData: Boolean = true
)

enum class OrderSource {
    /** Авто: один лист — имя файла, много листов — имя листа. */
    AUTO,
    /** Всегда имя файла. */
    FILENAME,
    /** Всегда имя листа. */
    SHEET_NAME
}

enum class OrderNumberRule {
    /** Взять последнее целое число из строки: «02-КОПТ00027» → «27». */
    LAST_INTEGER,
    /** Оставить всё имя как есть. */
    FULL_NAME
}

val DEFAULT_AREA_PREFIXES: Map<String, List<String>> = mapOf(
    "Коптеловский" to listOf("KPD", "KOP"),
    "Кедровый" to listOf("KBK", "KMB"),
    "Актайский" to listOf("ACD"),
    "Нейвинский" to listOf("NV")
)