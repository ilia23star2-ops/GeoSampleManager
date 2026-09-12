package com.example.geosamplemanager.data.excel

import android.util.Xml
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.zip.ZipInputStream

data class SheetData(
    val name: String,
    val rows: List<List<String>>
)

/**
 * Метаданные листа Excel без чтения содержимого.
 * Используется для ленивой загрузки: сначала узнаём, какие листы есть и сколько
 * в них строк, а потом читаем только те, что нужны.
 */
data class SheetMeta(
    val name: String,
    val path: String,       // путь внутри zip, например "xl/worksheets/sheet1.xml"
    val rowCount: Int       // приблизительно — количество <row>
)

/**
 * Ленивый парсер .xlsx. Читает листы по требованию, чтобы не держать
 * в памяти весь файл целиком.
 *
 * Все методы принимают функцию `openStream`, которая открывает **новый** поток
 * на файл. Это нужно, потому что ZipInputStream последовательный, и для
 * второго прохода нужно переоткрыть файл.
 */
object XlsxReader {

    // ============================================================
    // МЕТАДАННЫЕ
    // ============================================================

    /**
     * Читает только имена листов и приблизительное количество строк в каждом.
     * Не парсит ячейки. Экономит память на больших файлах.
     */
    fun readMetadata(openStream: () -> InputStream): List<SheetMeta> {
        // 1. Первый проход: только workbook.xml и rels
        var workbook: ByteArray? = null
        var rels: ByteArray? = null
        openStream().use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "xl/workbook.xml" -> workbook = zip.readBytes()
                        "xl/_rels/workbook.xml.rels" -> rels = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        if (workbook == null) return emptyList()

        val sheetNames = parseSheetNames(workbook!!)
        val relMap = rels?.let { parseRels(it) } ?: emptyMap()

        // 2. Второй проход: считаем строки в каждом листе
        return sheetNames.mapIndexed { index, (name, rId) ->
            val target = relMap[rId] ?: "worksheets/sheet${index + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/")
            else "xl/$target"
            val rowCount = countRows(openStream, path)
            SheetMeta(name, path, rowCount)
        }
    }

    // ============================================================
    // ЧТЕНИЕ ОДНОГО ЛИСТА
    // ============================================================

    /**
     * Полное чтение одного листа.
     * @return SheetData или null, если лист не найден в архиве.
     */
    fun readSheet(
        openStream: () -> InputStream,
        sheetPath: String,
        sheetName: String
    ): SheetData? {
        var sheetBytes: ByteArray? = null
        var sharedBytes: ByteArray? = null

        openStream().use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "xl/sharedStrings.xml" -> sharedBytes = zip.readBytes()
                        sheetPath -> sheetBytes = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (sheetBytes == null) return null
        val sharedStrings = sharedBytes?.let { parseSharedStrings(it) } ?: emptyList()
        val rows = parseSheet(sheetBytes!!, sharedStrings)
        return SheetData(sheetName, rows)
    }

    // ============================================================
    // СТАРЫЙ API (для совместимости, помечен deprecated)
    // ============================================================

    @Deprecated("Используйте readMetadata + readSheet")
    fun read(input: InputStream): List<SheetData> {
        val entries = readAllEntries(input)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetNames = entries["xl/workbook.xml"]?.let { parseSheetNames(it) } ?: emptyList()
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()

        val result = mutableListOf<SheetData>()
        sheetNames.forEachIndexed { index, (name, rId) ->
            val target = rels[rId] ?: "worksheets/sheet${index + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val bytes = entries[path] ?: return@forEachIndexed
            val rows = parseSheet(bytes, sharedStrings)
            result.add(SheetData(name, rows))
        }
        return result
    }

    private fun readAllEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    // ============================================================
    // ХЕЛПЕРЫ
    // ============================================================

    /** Считает строки в указанном листе, не разбирая ячейки. */
    private fun countRows(openStream: () -> InputStream, path: String): Int {
        var count = 0
        openStream().use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == path) {
                        val handler = object : DefaultHandler() {
                            override fun startElement(
                                uri: String?, localName: String?,
                                qName: String?, attrs: Attributes?
                            ) {
                                if (qName == "row") count++
                            }
                        }
                        try {
                            Xml.parse(zip, android.util.Xml.Encoding.UTF_8, handler)
                        } catch (_: Exception) {
                            // игнорируем — некорректный xml, но строки посчитали
                        }
                        return count
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return count
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val h = object : DefaultHandler() {
            val strings = mutableListOf<String>()
            val current = StringBuilder()
            var inT = false
            var inSi = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                when (qName) {
                    "si" -> { inSi = true; current.clear() }
                    "t" -> { inT = true }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && inSi) current.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (qName) {
                    "t" -> inT = false
                    "si" -> { strings.add(current.toString()); inSi = false }
                }
            }
        }
        Xml.parse(bytes.inputStream(), android.util.Xml.Encoding.UTF_8, h)
        return h.strings
    }

    private fun parseSheetNames(bytes: ByteArray): List<Pair<String, String>> {
        val h = object : DefaultHandler() {
            val list = mutableListOf<Pair<String, String>>()
            override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
                if (q == "sheet") {
                    val name = a?.getValue("name") ?: return
                    val rId = a.getValue("r:id") ?: a.getValue("id") ?: return
                    list.add(name to rId)
                }
            }
        }
        Xml.parse(bytes.inputStream(), android.util.Xml.Encoding.UTF_8, h)
        return h.list
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val h = object : DefaultHandler() {
            val map = mutableMapOf<String, String>()
            override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
                if (q == "Relationship") {
                    val id = a?.getValue("Id") ?: return
                    val target = a.getValue("Target") ?: return
                    map[id] = target
                }
            }
        }
        Xml.parse(bytes.inputStream(), android.util.Xml.Encoding.UTF_8, h)
        return h.map
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val h = object : DefaultHandler() {
            val rows = mutableListOf<MutableMap<Int, String>>()
            var currentRow: MutableMap<Int, String>? = null
            var currentCol = 0
            var currentType = ""
            val currentValue = StringBuilder()
            val inlineValue = StringBuilder()
            var inValue = false
            var inInlineStr = false

            override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
                when (q) {
                    "row" -> currentRow = mutableMapOf()
                    "c" -> {
                        val ref = a?.getValue("r") ?: ""
                        currentCol = colFromRef(ref)
                        currentType = a?.getValue("t") ?: ""
                        currentValue.clear()
                        inlineValue.clear()
                    }
                    "v" -> inValue = true
                    "is" -> inInlineStr = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) currentValue.append(ch, start, length)
                else if (inInlineStr) inlineValue.append(ch, start, length)
            }

            override fun endElement(u: String?, l: String?, q: String?) {
                when (q) {
                    "v" -> inValue = false
                    "is" -> inInlineStr = false
                    "c" -> {
                        val raw = currentValue.toString()
                        val value = when (currentType) {
                            "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: ""
                            "inlineStr" -> inlineValue.toString()
                            "str" -> raw
                            else -> raw
                        }
                        currentRow?.set(currentCol, value)
                    }
                    "row" -> {
                        currentRow?.let { map -> if (map.isNotEmpty()) rows.add(map) }
                        currentRow = null
                    }
                }
            }
        }
        Xml.parse(bytes.inputStream(), android.util.Xml.Encoding.UTF_8, h)
        return h.rows.map { map ->
            val maxCol = map.keys.maxOrNull() ?: -1
            val list = MutableList(maxCol + 1) { "" }
            map.forEach { (col, v) -> list[col] = v }
            list
        }
    }

    /** "B5" → 1 */
    private fun colFromRef(ref: String): Int {
        var col = 0
        for (c in ref) {
            if (c.isLetter()) col = col * 26 + (c.uppercaseChar() - 'A' + 1)
            else break
        }
        return col - 1
    }
}