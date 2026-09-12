package com.example.geosamplemanager.data.settings

/**
 * Настройки импорта Excel.
 * Хранятся в filesDir/import_settings.json.
 *
 * Разделяем дефолтные и пользовательские словари — это позволяет:
 * 1) показывать пользователю «стандартные» и «ваши» слова отдельно;
 * 2) давать пользовательским словам приоритет при матчинге;
 * 3) сбрасывать только пользовательские, не трогая стандартные.
 */
data class ImportSettings(

    /** Участок → список префиксов в номерах скважин/проб. */
    val areaPrefixes: Map<String, List<String>> = DEFAULT_AREA_PREFIXES,

    /** Откуда брать номер наряда. */
    val orderSource: OrderSource = OrderSource.AUTO,

    /** Как обрабатывать имя источника, чтобы получить номер наряда. */
    val orderNumberRule: OrderNumberRule = OrderNumberRule.LAST_INTEGER,

    /** Стандартные словари заголовков. Только для чтения. */
    val defaultHeaderKeywords: Map<String, List<String>> = DEFAULT_HEADER_KEYWORDS,

    /** Пользовательские словари заголовков. Приоритет выше стандартных. */
    val userHeaderKeywords: Map<String, List<String>> = emptyMap(),

    /** Стандартные словари значений типов пробы. */
    val defaultTypeValueKeywords: Map<String, List<String>> = DEFAULT_TYPE_VALUE_KEYWORDS,

    /** Пользовательские значения типов пробы. */
    val userTypeValueKeywords: Map<String, List<String>> = emptyMap(),

    /** Слова-маркеры бланков и стандартных образцов. */
    val blankKeywords: List<String> = DEFAULT_BLANK_KEYWORDS,

    /** Пропускать бланки, у которых нет интервала и веса. */
    val skipBlanksWithoutData: Boolean = true
) {

    // ============ Эффективные словари (user + default) ============

    /** Список слов для роли заголовка: сначала пользовательские, потом стандартные. */
    fun effectiveHeaderKeywords(role: String): List<String> {
        val user = userHeaderKeywords[role].orEmpty()
        val defaults = defaultHeaderKeywords[role].orEmpty()
        return (user + defaults).distinct()
    }

    /** Список слов для типа пробы: сначала пользовательские, потом стандартные. */
    fun effectiveTypeKeywords(typeCode: String): List<String> {
        val user = userTypeValueKeywords[typeCode].orEmpty()
        val defaults = defaultTypeValueKeywords[typeCode].orEmpty()
        return (user + defaults).distinct()
    }

    /** Только пользовательские слова для роли — для UI. */
    fun userKeywordsForHeader(role: String): List<String> =
        userHeaderKeywords[role].orEmpty()

    /** Только пользовательские слова для типа — для UI. */
    fun userKeywordsForType(typeCode: String): List<String> =
        userTypeValueKeywords[typeCode].orEmpty()

    // ============ Мутаторы ============

    fun addUserHeaderKeyword(role: String, word: String): ImportSettings {
        val w = normalizeHeaderWord(word)
        if (w.isEmpty()) return this
        val existing = userHeaderKeywords[role].orEmpty()
        if (existing.contains(w)) return this
        val updated = userHeaderKeywords.toMutableMap()
        updated[role] = (existing + w).takeLast(20)
        return copy(userHeaderKeywords = updated)
    }

    fun removeUserHeaderKeyword(role: String, word: String): ImportSettings {
        val existing = userHeaderKeywords[role].orEmpty()
        if (!existing.contains(word)) return this
        val updated = userHeaderKeywords.toMutableMap()
        val newList = existing - word
        if (newList.isEmpty()) updated.remove(role) else updated[role] = newList
        return copy(userHeaderKeywords = updated)
    }

    fun addUserTypeKeyword(typeCode: String, word: String): ImportSettings {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return this
        val existing = userTypeValueKeywords[typeCode].orEmpty()
        if (existing.contains(w)) return this
        val updated = userTypeValueKeywords.toMutableMap()
        updated[typeCode] = (existing + w).takeLast(20)
        return copy(userTypeValueKeywords = updated)
    }

    fun removeUserTypeKeyword(typeCode: String, word: String): ImportSettings {
        val existing = userTypeValueKeywords[typeCode].orEmpty()
        if (!existing.contains(word)) return this
        val updated = userTypeValueKeywords.toMutableMap()
        val newList = existing - word
        if (newList.isEmpty()) updated.remove(typeCode) else updated[typeCode] = newList
        return copy(userTypeValueKeywords = updated)
    }

    fun resetUserKeywords(): ImportSettings = copy(
        userHeaderKeywords = emptyMap(),
        userTypeValueKeywords = emptyMap()
    )
}

enum class OrderSource { AUTO, FILENAME, SHEET_NAME }

enum class OrderNumberRule { LAST_INTEGER, FULL_NAME }

// ============ Дефолты ============

val DEFAULT_AREA_PREFIXES: Map<String, List<String>> = mapOf(
    "Коптеловский" to listOf("KPD", "KOP"),
    "Кедровый" to listOf("KBK", "KMB"),
    "Актайский" to listOf("ACD"),
    "Нейвинский" to listOf("NV")
)

val DEFAULT_HEADER_KEYWORDS: Map<String, List<String>> = mapOf(
    "serial"    to listOf("п/п", "№ п/п", "no", "n", "индекс", "серийный"),
    "well"      to listOf("скважина", "скв", "well", "выработка", "канава", "шурф"),
    "sample"    to listOf("№ пробы", "номер пробы", "шифр", "проба №"),
    "int_from"  to listOf("от", "с", "интервал от", "глубина от"),
    "int_to"    to listOf("до", "по", "интервал до", "глубина до"),
    "weight"    to listOf("вес", "масса", "кг", "weight"),
    "material"  to listOf("характеристика", "материал", "литология", "описание"),
    "type"      to listOf("тип пробы", "тип", "вид пробы")
)

val DEFAULT_TYPE_VALUE_KEYWORDS: Map<String, List<String>> = mapOf(
    "hollow"     to listOf("холост", "blank", "пуст"),
    "auger"      to listOf("шнек", "auger"),
    "channel"    to listOf("борозд", "канав", "channel"),
    "cobra"      to listOf("кобра", "cobra"),
    "duplicate"  to listOf("дубликат", "duplicate")
)

val DEFAULT_BLANK_KEYWORDS: List<String> = listOf(
    "бланк", "blank", "стандартный образец", "стандарт", "ст. образец",
    "дубликат хвостов", "контрольный образец", "к.о."
)

/** Нормализация заголовка перед сохранением в словарь. */
fun normalizeHeaderWord(word: String): String {
    var w = word.trim().lowercase()
    // Убираем единицы измерения в конце
    w = w.replace(Regex("[,;.]?\\s*(кг|г|м|см|мм|шт|%)\\s*$"), "")
    // Убираем лишние пробелы
    w = w.replace(Regex("\\s+"), " ")
    // Убираем знаки препинания в начале и конце
    w = w.trim(',', '.', ';', ':', '-')
    return w
}