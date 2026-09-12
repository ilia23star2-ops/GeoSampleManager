package com.example.geosamplemanager.data.excel

import com.example.geosamplemanager.data.settings.ImportSettings
import java.util.Locale

/**
 * Анализ листов Excel: находит шапку, определяет роли колонок
 * на основе текста шапки И данных, с учётом пользовательских словарей из настроек.
 */
object ExcelAnalyzer {

    object Roles {
        const val SERIAL = "serial"
        const val WELL = "well"
        const val SAMPLE = "sample"
        const val INT_FROM = "int_from"
        const val INT_TO = "int_to"
        const val WEIGHT = "weight"
        const val TYPE = "type"
        const val MATERIAL = "material"
    }

    /** Все роли в порядке использования при поиске колонок. */
    private val ALL_ROLES = listOf(
        Roles.SERIAL, Roles.WELL, Roles.SAMPLE,
        Roles.INT_FROM, Roles.INT_TO, Roles.WEIGHT,
        Roles.TYPE, Roles.MATERIAL
    )

    private val EXCLUDED_SHEET_KEYWORDS = listOf(
        "титул", "оглавление", "содержание", "cover", "toc"
    )

    /** Доменные слова для характеристики — пользователь их не редактирует. */
    private val GEO_WORDS = listOf(
        "глина", "глины", "суглинок", "суглинки", "супесь", "песок", "песк",
        "кора выветривания", "делювий", "аллювий", "щебн", "дресв",
        "сер", "коричнев", "жёлт", "желт", "бур", "фиолетов", "тёмн", "темн",
        "светло", "сине", "зелен", "кварц", "сланец", "алеврит"
    )

    // ============ Публичное API ============

    fun analyze(sheets: List<SheetData>, settings: ImportSettings): SheetAnalysis? {
        val candidates = sheets.filter { !isExcludedSheet(it.name) }
        var best: SheetAnalysis? = null
        for (sheet in candidates) {
            val r = analyzeSheet(sheet, settings) ?: continue
            if (best == null || r.score > best.score) best = r
        }
        return best
    }

    fun analyzeSheet(sheet: SheetData, settings: ImportSettings): SheetAnalysis? {
        var best: SheetAnalysis? = null
        val maxStart = minOf(20, sheet.rows.size)
        for (start in 0 until maxStart) {
            for (count in 1..3) {
                if (start + count > sheet.rows.size) continue
                val r = tryHeader(sheet, start, count, settings) ?: continue
                if (best == null || r.score > best.score) best = r
            }
        }
        return best
    }

    // ============ Ядро ============

    private fun tryHeader(
        sheet: SheetData,
        start: Int,
        count: Int,
        settings: ImportSettings
    ): SheetAnalysis? {
        val header = mergedHeader(sheet.rows, start, count)
        if (header.isEmpty()) return null
        if (header.count { it.isNotBlank() } < 3) return null

        val dataStart = start + count
        if (dataStart >= sheet.rows.size) return null
        val sample = sheet.rows.subList(dataStart, minOf(dataStart + 40, sheet.rows.size))
        if (sample.isEmpty()) return null

        // Все слова-маркеры типа пробы и бланка — из настроек
        val allTypeWords = buildTypeWords(settings)

        val profiles = profileColumns(sample, header.size, allTypeWords)
        val textMapping = matchHeaderText(header, settings)
        val textScore = textMapping.values.count { it != null }

        val mapping = combineMapping(textMapping, profiles, sample)
        if (mapping[Roles.WELL] == null || mapping[Roles.SAMPLE] == null) return null

        val dataScore = scoreMapping(mapping, profiles)
        val total = textScore * 2 + dataScore

        return SheetAnalysis(
            sheetName = sheet.name,
            headerRowIndex = start,
            headerRowCount = count,
            mapping = mapping,
            textualScore = textScore,
            dataScore = dataScore,
            score = total,
            rows = sheet.rows
        )
    }

    /** Собирает все слова-маркеры типов пробы и бланков из настроек. */
    private fun buildTypeWords(settings: ImportSettings): List<String> {
        val types = listOf("hollow", "auger", "channel", "cobra", "duplicate")
        val typeWords = types.flatMap { settings.effectiveTypeKeywords(it) }
        val blankWords = settings.blankKeywords
        return (typeWords + blankWords).distinct()
    }

    fun mergedHeader(rows: List<List<String>>, start: Int, count: Int): List<String> {
        if (start < 0 || start >= rows.size) return emptyList()
        val size = rows.subList(start, minOf(start + count, rows.size))
            .maxOfOrNull { it.size } ?: 0
        val result = MutableList(size) { "" }
        for (i in 0 until count) {
            val row = rows.getOrNull(start + i) ?: continue
            for (j in row.indices) {
                val v = row[j].trim()
                if (v.isEmpty()) continue
                result[j] = if (result[j].isEmpty()) v else "${result[j]} $v"
            }
        }
        return result
    }

    // ============ Текстовый матчинг ============

    private fun matchHeaderText(
        header: List<String>,
        settings: ImportSettings
    ): Map<String, Int?> {
        val norm = header.map { it.trim().lowercase(Locale.ROOT) }
        val result = mutableMapOf<String, Int?>()
        val used = mutableSetOf<Int>()

        // Сначала идём по ролям с более специфичными словами,
        // чтобы "№ пробы" не занял "well", если параллельно есть "скважина".
        val order = listOf(
            Roles.INT_FROM, Roles.INT_TO,
            Roles.WEIGHT, Roles.MATERIAL,
            Roles.TYPE,
            Roles.SAMPLE, Roles.WELL,
            Roles.SERIAL
        )
        for (role in order) {
            val words = settings.effectiveHeaderKeywords(role)
            val idx = findColumnByKeywords(norm, words, used)
            if (idx != null) {
                result[role] = idx
                used.add(idx)
            } else {
                result[role] = null
            }
        }
        return result
    }

    private fun findColumnByKeywords(
        normalized: List<String>,
        words: List<String>,
        exclude: Set<Int>
    ): Int? {
        if (words.isEmpty()) return null

        // 1) Точное совпадение всей ячейки
        for ((i, cell) in normalized.withIndex()) {
            if (i in exclude) continue
            if (cell.isEmpty()) continue
            if (words.any { it == cell }) return i
        }
        // 2) Слово как отдельный токен
        for ((i, cell) in normalized.withIndex()) {
            if (i in exclude) continue
            if (cell.isEmpty()) continue
            val tokens = cell.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            if (words.any { w -> tokens.contains(w) }) return i
        }
        // 3) Подстрока — только для длинных слов (>4 символов), чтобы избежать ложных
        for ((i, cell) in normalized.withIndex()) {
            if (i in exclude) continue
            if (cell.isEmpty()) continue
            if (words.any { w -> w.length >= 5 && cell.contains(w) }) return i
        }
        return null
    }

    // ============ Профили колонок ============

    private fun profileColumns(
        rows: List<List<String>>,
        cols: Int,
        typeWords: List<String>
    ): List<ColumnProfile> =
        (0 until cols).map { profileColumn(it, rows, typeWords) }

    private fun profileColumn(
        c: Int,
        rows: List<List<String>>,
        typeWords: List<String>
    ): ColumnProfile {
        val values = rows.mapNotNull { row ->
            row.getOrNull(c)?.trim()?.takeIf { it.isNotEmpty() }
        }
        val nonEmpty = values.size
        if (nonEmpty == 0) {
            return ColumnProfile(c, 0, false, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, "")
        }
        val intSeq = isIntSequence(values)
        val wellLike = values.count {
            it.matches(Regex("^[A-Za-zА-Яа-я]{2,6}\\d{3,15}$"))
        }.toDouble() / nonEmpty
        val floatRatio = values.count { parseNumber(it) != null }.toDouble() / nonEmpty
        val shortText = values.count { it.length <= 25 }.toDouble() / nonEmpty
        val lower = values.map { it.lowercase(Locale.ROOT) }
        val typeRatio = if (typeWords.isEmpty()) 0.0 else
            lower.count { v -> typeWords.any { v.contains(it) } }.toDouble() / nonEmpty
        val geoRatio = lower.count { v -> GEO_WORDS.any { v.contains(it) } }.toDouble() / nonEmpty
        val unique = values.toSet().size
        val avgLen = values.map { it.length }.average()
        return ColumnProfile(
            index = c,
            nonEmpty = nonEmpty,
            intSeqMatch = intSeq,
            wellLikeRatio = wellLike,
            floatRatio = floatRatio,
            shortTextRatio = shortText,
            typeWordRatio = typeRatio,
            geoWordRatio = geoRatio,
            uniqueCount = unique,
            avgLength = avgLen,
            firstValue = values.first()
        )
    }

    private fun isIntSequence(values: List<String>): Boolean {
        if (values.size < 3) return false
        var prev = Int.MIN_VALUE
        var ok = 0
        for (v in values.take(15)) {
            val d = parseNumber(v) ?: return false
            if (d != d.toInt().toDouble()) return false
            val iv = d.toInt()
            if (prev != Int.MIN_VALUE && iv != prev + 1) return false
            prev = iv
            ok++
        }
        return ok >= 3
    }

    // ============ Комбинированный маппинг ============

    private fun combineMapping(
        textMapping: Map<String, Int?>,
        profiles: List<ColumnProfile>,
        sample: List<List<String>>
    ): Map<String, Int?> {
        val result = mutableMapOf<String, Int?>()
        val used = mutableSetOf<Int>()

        // 1) Скважина / Проба
        val wellLikeCols = profiles.filter { it.wellLikeRatio >= 0.7 }.map { it.index }
        val pair = pickWellSample(wellLikeCols, sample)
        val dataWell = pair.first
        val dataSample = pair.second

        val finalWell = dataWell ?: textMapping[Roles.WELL]
        val finalSample = dataSample ?: textMapping[Roles.SAMPLE]
        result[Roles.WELL] = finalWell
        result[Roles.SAMPLE] = finalSample
        finalWell?.let { used.add(it) }
        finalSample?.let { used.add(it) }

        // 2) Интервал
        val (dataFrom, dataTo) = pickInterval(profiles, sample, used)
        val finalFrom = dataFrom ?: textMapping[Roles.INT_FROM]
        val finalTo = dataTo ?: textMapping[Roles.INT_TO]
        result[Roles.INT_FROM] = finalFrom
        result[Roles.INT_TO] = finalTo
        finalFrom?.let { used.add(it) }
        finalTo?.let { used.add(it) }

        // 3) Серийный
        val serialCol = profiles.firstOrNull { it.intSeqMatch && it.index !in used }?.index
            ?: textMapping[Roles.SERIAL]
        result[Roles.SERIAL] = serialCol
        serialCol?.let { used.add(it) }

        // 4) Вес
        val weightCol = pickWeight(profiles, used) ?: textMapping[Roles.WEIGHT]
        result[Roles.WEIGHT] = weightCol
        weightCol?.let { used.add(it) }

        // 5) Тип
        val typeCol = pickType(profiles, used) ?: textMapping[Roles.TYPE]
        result[Roles.TYPE] = typeCol
        typeCol?.let { used.add(it) }

        // 6) Характеристика
        val matCol = pickMaterial(profiles, used) ?: textMapping[Roles.MATERIAL]
        result[Roles.MATERIAL] = matCol

        return result
    }

    private fun pickWellSample(
        cols: List<Int>,
        sample: List<List<String>>
    ): Pair<Int?, Int?> {
        if (cols.isEmpty()) return null to null
        if (cols.size == 1) return null to cols[0]

        var bestPair: Pair<Int, Int>? = null
        var bestScore = -1.0
        for (a in cols) {
            for (b in cols) {
                if (a == b) continue
                var hits = 0
                var total = 0
                for (row in sample) {
                    val va = row.getOrNull(a)?.trim().orEmpty()
                    val vb = row.getOrNull(b)?.trim().orEmpty()
                    if (va.isEmpty() || vb.isEmpty()) continue
                    total++
                    if (va.startsWith(vb) || vb.startsWith(va)) hits++
                }
                if (total == 0) continue
                val score = hits.toDouble() / total
                if (score > bestScore) {
                    bestScore = score
                    val aLen = avgLength(sample, a)
                    val bLen = avgLength(sample, b)
                    bestPair = if (aLen >= bLen) b to a else a to b
                }
            }
        }
        return bestPair ?: run {
            val sampleCol = cols.maxByOrNull { avgLength(sample, it) } ?: cols[0]
            val wellCol = cols.firstOrNull { it != sampleCol }
            wellCol to sampleCol
        }
    }

    private fun avgLength(rows: List<List<String>>, c: Int): Double {
        val vals = rows.mapNotNull { it.getOrNull(c)?.length?.takeIf { it > 0 } }
        return if (vals.isEmpty()) 0.0 else vals.average()
    }

    private fun pickInterval(
        profiles: List<ColumnProfile>,
        rows: List<List<String>>,
        used: Set<Int>
    ): Pair<Int?, Int?> {
        val numeric = profiles.filter { it.floatRatio >= 0.6 && it.index !in used }
        if (numeric.size < 2) return null to null

        for (p1 in numeric) {
            for (p2 in numeric) {
                if (p2.index != p1.index + 1) continue
                val (ok, total) = countPairs(rows, p1.index, p2.index)
                if (total >= 3 && ok.toDouble() / total >= 0.85) {
                    return p1.index to p2.index
                }
            }
        }
        for (p1 in numeric) {
            for (p2 in numeric) {
                if (p2.index <= p1.index) continue
                val (ok, total) = countPairs(rows, p1.index, p2.index)
                if (total >= 3 && ok.toDouble() / total >= 0.95) {
                    return p1.index to p2.index
                }
            }
        }
        return null to null
    }

    private fun countPairs(rows: List<List<String>>, cFrom: Int, cTo: Int): Pair<Int, Int> {
        var ok = 0
        var total = 0
        for (row in rows) {
            val a = parseNumber(row.getOrNull(cFrom) ?: "")
            val b = parseNumber(row.getOrNull(cTo) ?: "")
            if (a == null || b == null) continue
            total++
            if (b >= a) ok++
        }
        return ok to total
    }

    private fun pickWeight(profiles: List<ColumnProfile>, used: Set<Int>): Int? {
        for (p in profiles) {
            if (p.index in used) continue
            if (p.floatRatio < 0.6) continue
            if (p.intSeqMatch) continue
            val v = parseNumber(p.firstValue) ?: continue
            if (v in 0.2..80.0) return p.index
        }
        return null
    }

    private fun pickType(profiles: List<ColumnProfile>, used: Set<Int>): Int? {
        for (p in profiles) {
            if (p.index in used) continue
            if (p.typeWordRatio >= 0.5 && p.uniqueCount <= 20) return p.index
        }
        for (p in profiles) {
            if (p.index in used) continue
            if (p.typeWordRatio >= 0.25 && p.avgLength <= 30) return p.index
        }
        return null
    }

    private fun pickMaterial(profiles: List<ColumnProfile>, used: Set<Int>): Int? {
        for (p in profiles) {
            if (p.index in used) continue
            if (p.geoWordRatio >= 0.3 && p.avgLength >= 10) return p.index
        }
        return profiles
            .filter { it.index !in used && it.avgLength >= 15 }
            .maxByOrNull { it.avgLength }?.index
    }

    private fun scoreMapping(mapping: Map<String, Int?>, profiles: List<ColumnProfile>): Int {
        var s = 0
        mapping[Roles.WELL]?.let { if ((profiles.getOrNull(it)?.wellLikeRatio ?: 0.0) >= 0.6) s += 3 }
        mapping[Roles.SAMPLE]?.let { if ((profiles.getOrNull(it)?.wellLikeRatio ?: 0.0) >= 0.6) s += 3 }
        mapping[Roles.SERIAL]?.let { if (profiles.getOrNull(it)?.intSeqMatch == true) s += 2 }
        mapping[Roles.INT_FROM]?.let { if ((profiles.getOrNull(it)?.floatRatio ?: 0.0) >= 0.5) s += 1 }
        mapping[Roles.INT_TO]?.let { if ((profiles.getOrNull(it)?.floatRatio ?: 0.0) >= 0.5) s += 1 }
        mapping[Roles.WEIGHT]?.let { if ((profiles.getOrNull(it)?.floatRatio ?: 0.0) >= 0.5) s += 2 }
        mapping[Roles.TYPE]?.let { if ((profiles.getOrNull(it)?.typeWordRatio ?: 0.0) >= 0.4) s += 2 }
        mapping[Roles.MATERIAL]?.let { if ((profiles.getOrNull(it)?.geoWordRatio ?: 0.0) >= 0.3) s += 2 }
        return s
    }

    // ============ Утилиты ============

    fun parseNumber(s: String): Double? {
        if (s.isBlank()) return null
        val cleaned = s.replace(',', '.').trim()
        return cleaned.toDoubleOrNull()
    }

    private fun isExcludedSheet(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return EXCLUDED_SHEET_KEYWORDS.any { n.contains(it) }
    }
}

data class ColumnProfile(
    val index: Int,
    val nonEmpty: Int,
    val intSeqMatch: Boolean,
    val wellLikeRatio: Double,
    val floatRatio: Double,
    val shortTextRatio: Double,
    val typeWordRatio: Double,
    val geoWordRatio: Double,
    val uniqueCount: Int,
    val avgLength: Double,
    val firstValue: String
)

data class SheetAnalysis(
    val sheetName: String,
    val headerRowIndex: Int,
    val headerRowCount: Int,
    val mapping: Map<String, Int?>,
    val textualScore: Int,
    val dataScore: Int,
    val score: Int,
    val rows: List<List<String>>
) {
    fun dataStartIndex(): Int = headerRowIndex + headerRowCount
    fun headerText(): List<String> = ExcelAnalyzer.mergedHeader(rows, headerRowIndex, headerRowCount)
    fun previewRow(idx: Int): List<String>? = rows.getOrNull(dataStartIndex() + idx)
    fun dataRows(): List<List<String>> = rows.drop(dataStartIndex())
}