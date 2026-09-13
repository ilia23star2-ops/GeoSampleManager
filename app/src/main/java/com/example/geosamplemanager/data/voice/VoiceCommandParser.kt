package com.example.geosamplemanager.data.voice

/**
 * Разбор голосовой фразы в VoiceCommand.
 *
 * Порядок проверок важен: «снять первую» должно сработать как ClearOrdinal,
 * а не как MarkOrdinal(1). «Вес два пять» — как SetWeight, не как Search.
 */
class VoiceCommandParser(
    private val numberParser: VoiceNumberParser = VoiceNumberParser()
) {

    fun parse(input: String): VoiceCommand {
        val raw = input.trim()
        if (raw.isEmpty()) return VoiceCommand.Unknown

        val norm = numberParser.normalize(raw)

        // ---- Управляющие (одиночные слова) ----
        when (norm) {
            "стоп", "хватит" -> return VoiceCommand.Stop
            "пауза", "паузу" -> return VoiceCommand.Pause
            "продолжить", "продолжай" -> return VoiceCommand.Resume
            "отмена", "отменить", "верни", "назад" -> return VoiceCommand.Undo
            "повтори", "вперёд", "вперед" -> return VoiceCommand.Redo
            "следующая", "далее", "следующую", "следующий" -> return VoiceCommand.Next
            "помощь", "команды", "команда" -> return VoiceCommand.Help
            "сколько осталось", "сколько осталось?" -> return VoiceCommand.HowManyLeft
            "показать отложенные", "отложенные" -> return VoiceCommand.ShowPostponed
            "показать найденные", "найденные" -> return VoiceCommand.ShowFound
            "снять все", "сбросить все", "очистить все" -> return VoiceCommand.ClearAll
            "снять последнюю", "последнюю снять" -> return VoiceCommand.ClearLast
            "снять отложенную", "снять отложенную пробу" -> return VoiceCommand.Unpostpone
        }

        // ---- Вес ----
        if (norm == "вес" || norm.startsWith("вес ")) {
            val tail = norm.removePrefix("вес").trim()
            val value = parseWeight(tail)
            if (value != null) return VoiceCommand.SetWeight(value)
        }

        // ---- Снять <ordinal> ----
        val words = norm.split(Regex("\\s+"))
        if (words.size >= 2 &&
            words[0] in setOf("снять", "убрать", "удали", "удалить")
        ) {
            val tail = words.drop(1).joinToString(" ")
            val ord = VoiceOrdinals.match(tail)
            if (ord != null) return VoiceCommand.ClearOrdinal(ord)
        }

        // ---- Отметить <ordinal> ----
        val ord = VoiceOrdinals.match(norm)
        if (ord != null) return VoiceCommand.MarkOrdinal(ord)

        // ---- Сортировка: "<X> и <Y>" ----
        val sortParts = norm.split(Regex("\\s+и\\s+"))
        if (sortParts.size in 2..5 && sortParts.all { it.any { c -> c.isDigit() } }) {
            return VoiceCommand.Sort(sortParts.map { it.trim() })
        }

        // ---- Всё остальное — поиск ----
        return VoiceCommand.Search(raw)
    }

    // ================================================================
    // Разбор веса
    // ================================================================

    /**
     * «вес 2.5», «вес 2,5», «вес два пять» → 2.5
     * «вес два ноль пять» → 2.05
     * «вес пять» → 5.0
     */
    private fun parseWeight(text: String): Double? {
        if (text.isEmpty()) return null

        // Прямое число с точкой или запятой
        text.replace(',', '.').toDoubleOrNull()?.let { return it }

        // Через парсер
        val r = numberParser.parse(text)
        val c = r.primary ?: return null

        // Один блок
        if (!c.contains('|')) {
            c.toDoubleOrNull()?.let {
                // Две цифры подряд без точки: «25» → 2.5
                if (c.length == 2 && c.all { ch -> ch.isDigit() }) {
                    val a = c[0].digitToInt()
                    val b = c[1].digitToInt()
                    if (b != 0) return "$a.$b".toDoubleOrNull()
                }
                return it
            }
            return null
        }

        // Два блока: «2|5» → 2.5
        val parts = c.split('|')
        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            return "${parts[0]}.${parts[1]}".toDoubleOrNull()
        }
        return null
    }
}