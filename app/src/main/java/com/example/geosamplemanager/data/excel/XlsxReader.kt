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
 * Лёгкий парсер .xlsx без Apache POI.
 * Читает sharedStrings.xml и sheets/sheetN.xml.
 */
object XlsxReader {

    fun read(input: InputStream): List<SheetData> {
        // 1. Копим все файлы из zip в память
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // 2. Shared strings
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) }
            ?: emptyList()

        // 3. Имена листов из workbook.xml
        val sheetNames = entries["xl/workbook.xml"]?.let { parseSheetNames(it) } ?: emptyList()
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()

        // 4. Читаем каждый лист
        val result = mutableListOf<SheetData>()
        sheetNames.forEachIndexed { index, (name, rId) ->
            val target = rels[rId] ?: "worksheets/sheet${index + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val sheetBytes = entries[path] ?: return@forEachIndexed
            val rows = parseSheet(sheetBytes, sharedStrings)
            result.add(SheetData(name, rows))
        }
        return result
    }

    // ==================== Shared strings ====================
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
                    "si" -> {
                        strings.add(current.toString())
                        inSi = false
                    }
                }
            }
        }
        Xml.parse(bytes.inputStream(), android.util.Xml.Encoding.UTF_8, h)
        return h.strings
    }

    // ==================== Sheet names ====================
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

    // ==================== Relationships ====================
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

    // ==================== Sheet ====================
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
                        currentRow?.let { map ->
                            if (map.isNotEmpty()) rows.add(map)
                        }
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